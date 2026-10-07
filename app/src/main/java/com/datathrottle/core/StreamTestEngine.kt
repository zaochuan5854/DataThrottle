package com.datathrottle.core

import android.app.DownloadManager
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.net.Uri
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okio.buffer
import okio.source
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

enum class TestStatus {
    IDLE,
    RUNNING,
    COMPLETED,
    CANCELLED,
    ERROR
}

data class TestState(
    val status: TestStatus = TestStatus.IDLE,
    val bytesRead: Long = 0L,
    val totalBytes: Long = 0L,
    val progress: Float = 0.0f,
    val elapsedTimeMs: Long = 0L,
    val currentSpeedKbps: Float = 0.0f,
    val averageSpeedKbps: Float = 0.0f,
    val targetKbps: Float = 0.0f,
    val isThrottlingVerified: Boolean? = null,
    val imageBitmap: ImageBitmap? = null,
    val usingLocalAsset: Boolean = false,
    val partialImage: Boolean = false,
    val shapingExemptTransport: Boolean = false,
    val errorMessage: String? = null
)

/**
 * Measures the *actual* throughput of a shaped download (S1-01).
 *
 * Device measurements (docs/VERIFICATION.md) mapped the shaping scope:
 * the app's own uid is exempt (it writes the cap), while the shell uid
 * **and** the system DownloadProvider (uid 10099) are governed by it
 * (E7: 2 MB probe @ cap → 12.1 KB/s, 72 KiB steps). The primary
 * transport therefore downloads via DownloadManager — a true
 * measurement with no external dependency. Progressive visualization
 * region-decodes the top band of the JPEG from the bytes delivered so
 * far (E3b).
 *
 * The 1 MB test object is deliberate: the shaper is a token bucket with
 * a ≈100 KB burst allowance (E7 control: 200 KB @ 27 KB/s, ≥600 KB ≈
 * cap), so anything smaller rides the burst window and proves nothing.
 *
 * Fallbacks: Shizuku (shell-uid curl, also shaped) when the provider
 * path fails, and finally the app's own shaping-exempt network stack,
 * which marks the result honestly as non-measurable (S1-01).
 */
class StreamTestEngine(
    private val context: Context,
    private val shizuku: ShizukuManager
) {

    companion object {
        private const val TAG = "StreamTestEngine"
        const val TARGET_RATE_BYTES_PER_SEC = 12500L // 100 kbps (12.5 KB/s)
        // E7: ≥1 MB object so the transfer exits the shaper's ≈100 KB burst
        // window; 1,075,580 B baseline JPEG ≈ 86 s at the 100 kbps cap.
        const val DEFAULT_TEST_IMAGE_URL = "https://files.catbox.moe/n0ra4w.jpg"
        const val ASSET_FALLBACK_PATH = "diagnostic/peppers_test.jpg"

        private const val READ_BUFFER_SIZE = 4096
        private const val UI_UPDATE_INTERVAL_MS = 100L
        private const val MAX_TEST_DURATION_MS = 150_000L
        private const val MIN_BYTES_FOR_REGION = 2048L
    }

    private val _testState = MutableStateFlow(TestState())
    val testState: StateFlow<TestState> = _testState.asStateFlow()

    private var testJob: Job? = null
    private val engineScope = CoroutineScope(Dispatchers.Default + Job())

    // S1-05: the completion callback must be delivered exactly once. Without
    // this guard a throw after the success-path delivery re-invokes
    // onComplete from the catch block and the UI reports twice.
    private val completionDelivered = AtomicBoolean(true)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        // Shaping governs connections established after the cap is installed,
        // and a pooled keep-alive connection predates the cap. The fallback
        // measurement must run on a freshly established connection, so keep
        // no idle connections and evict any leftovers before each test.
        .connectionPool(okhttp3.ConnectionPool(0, 1, TimeUnit.MINUTES))
        .build()

    private class TestStream(
        val source: okhttp3.ResponseBody,
        val totalBytesHint: Long,
        val usingLocalAsset: Boolean
    )

    fun startTest(
        rateBytesPerSec: Long = TARGET_RATE_BYTES_PER_SEC,
        onComplete: ((TestState) -> Unit)? = null
    ) {
        testJob?.cancel()
        testJob = engineScope.launch {
            try {
                completionDelivered.set(false)
                _testState.value = TestState(status = TestStatus.RUNNING)

                // Primary transport: DownloadManager runs the fetch on the
                // system DownloadProvider uid, which the shaping governs
                // (E7, device-verified: 12.1 KB/s @ cap). No external dependency.
                if (measureViaDownloadManager(rateBytesPerSec, onComplete)) {
                    return@launch
                }

                // Secondary: Shizuku runs curl on the shell uid, also shaped
                // (used when the provider path is unavailable or fails).
                if (measureViaShizuku(rateBytesPerSec, onComplete)) {
                    return@launch
                }

                // Guarantee the fallback stream establishes a brand-new
                // connection (post-cap), never a pre-cap keep-alive one.
                httpClient.connectionPool.evictAll()

                val (stream, openError) = openTestStream()
                if (stream == null) {
                    val errorState = TestState(
                        status = TestStatus.ERROR,
                        errorMessage = openError
                    )
                    _testState.value = errorState
                    finishOnMain(onComplete, errorState)
                    return@launch
                }

                measureStream(stream, rateBytesPerSec, onComplete)
            } catch (e: CancellationException) {
                // Cooperative cancellation must propagate (S2-01). The terminal
                // CANCELLED state is written here — after the loop has fully
                // unwound — because the loop's last in-flight RUNNING frame can
                // otherwise overwrite cancelTest()'s write and leave the UI
                // stuck on a dead running display (S2-14).
                _testState.value = _testState.value.copy(
                    status = TestStatus.CANCELLED,
                    currentSpeedKbps = 0f
                )
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Test error", e)
                val errorState = _testState.value.copy(
                    status = TestStatus.ERROR,
                    currentSpeedKbps = 0f,
                    errorMessage = e.localizedMessage ?: "Unknown error"
                )
                _testState.value = errorState
                finishOnMain(onComplete, errorState)
            }
        }
    }

    // ─── Primary transport: DownloadManager (provider uid = shaped, E7) ─────

    private suspend fun measureViaDownloadManager(
        rateBytesPerSec: Long,
        onComplete: ((TestState) -> Unit)?
    ): Boolean {
        val dm = context.getSystemService(DownloadManager::class.java) ?: run {
            Log.w(TAG, "DownloadManager unavailable")
            return false
        }
        val dest = java.io.File(context.getExternalFilesDir(null), "diagnostic_test.jpg")
        runCatching { dest.delete() }
        val request = DownloadManager.Request(Uri.parse(DEFAULT_TEST_IMAGE_URL)).apply {
            setDestinationUri(Uri.fromFile(dest))
            // HyperOS rejects VISIBILITY_HIDDEN even with POST_NOTIFICATIONS (S2-11).
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }
        val enqueueId = try {
            dm.enqueue(request)
        } catch (e: Exception) {
            Log.w(TAG, "DownloadManager enqueue failed: ${e.message}")
            return false
        }
        return try {
            measureDmTransfer(dm, enqueueId, dest, rateBytesPerSec, onComplete)
        } finally {
            runCatching { dm.remove(enqueueId) }
            runCatching { dest.delete() }
        }
    }

    private suspend fun measureDmTransfer(
        dm: DownloadManager,
        downloadId: Long,
        dest: java.io.File,
        rateBytesPerSec: Long,
        onComplete: ((TestState) -> Unit)?
    ): Boolean {
        val targetKbps = (rateBytesPerSec * 8f) / 1000f
        val startTime = System.nanoTime()
        val deadlineNanos = startTime + MAX_TEST_DURATION_MS * 1_000_000L
        var lastUiNanos = startTime
        var lastRegionAt = 0L
        var bandBitmap: ImageBitmap? = null
        var totalRead = 0L
        var targetTotal = 0L
        var magicChecked = false

        while (true) {
            delay(UI_UPDATE_INTERVAL_MS)
            val now = System.nanoTime()
            val query = dm.query(DownloadManager.Query().setFilterById(downloadId))
            var status = DownloadManager.STATUS_FAILED
            var bytes = 0L
            var reason = 0
            try {
                if (query != null && query.moveToFirst()) {
                    status = query.getInt(query.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    bytes = query.getLong(query.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    targetTotal = maxOf(
                        targetTotal,
                        query.getLong(query.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    )
                    reason = query.getInt(query.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                }
            } finally {
                runCatching { query?.close() }
            }

            if (!magicChecked && bytes >= 2) {
                magicChecked = true
                val magic = ByteArray(2)
                runCatching { dest.inputStream().use { it.read(magic) } }
                if (!(magic[0] == 0xFF.toByte() && magic[1] == 0xD8.toByte())) {
                    Log.w(TAG, "DownloadManager delivered non-JPEG data; transport failed")
                    return false
                }
            }

            totalRead = bytes

            if (status == DownloadManager.STATUS_FAILED) {
                Log.w(TAG, "DownloadManager transfer failed (reason=$reason) at ${bytes}B")
                return false
            }

            if (now - lastUiNanos >= UI_UPDATE_INTERVAL_MS * 1_000_000L) {
                val elapsedMs = (now - startTime) / 1_000_000L
                val avgKbps = if (elapsedMs > 0) (totalRead * 8f) / (elapsedMs / 1000f) / 1000f else 0f
                val progress = if (targetTotal > 0) {
                    (totalRead.toFloat() / targetTotal.toFloat()).coerceIn(0f, 1f)
                } else 0f

                if (totalRead - lastRegionAt >= (targetTotal / 20).coerceAtLeast(MIN_BYTES_FOR_REGION)) {
                    lastRegionAt = totalRead
                    bandBitmap = decodeBandFrom(readDelimitedBytes(dest, totalRead), progress) ?: bandBitmap
                }

                _testState.value = _testState.value.copy(
                    status = TestStatus.RUNNING,
                    bytesRead = totalRead,
                    totalBytes = targetTotal,
                    progress = progress,
                    elapsedTimeMs = elapsedMs,
                    // The provider delivers in ~72 KiB bursts every ~6 s (E7), so a
                    // short window would read 0 between bursts (S3-11). Show the
                    // running average, which matches the verdict the report gives.
                    currentSpeedKbps = avgKbps,
                    averageSpeedKbps = avgKbps,
                    imageBitmap = bandBitmap,
                    partialImage = true,
                    shapingExemptTransport = false
                )
                lastUiNanos = now
            }

            if (status == DownloadManager.STATUS_SUCCESSFUL) break
            if (now >= deadlineNanos) {
                Log.w(TAG, "DownloadManager transport timed out at ${totalRead}B (target=$targetTotal)")
                return false
            }
        }

        val finalElapsedMs = ((System.nanoTime() - startTime) / 1_000_000L).coerceAtLeast(1L)
        val finalAvgKbps = (totalRead * 8f) / (finalElapsedMs / 1000f) / 1000f
        val data = readDelimitedBytes(dest, totalRead)
        val full = BitmapFactory.decodeByteArray(data, 0, data.size)?.asImageBitmap()
        if (full == null) {
            Log.w(TAG, "DownloadManager file is not a decodable image; transport failed")
            return false
        }

        // The provider uid is shaping-governed (E7), so the band check is a real verdict.
        val verified = targetKbps > 0f &&
            finalAvgKbps >= targetKbps * 0.4f && finalAvgKbps <= targetKbps * 1.6f

        val finalState = _testState.value.copy(
            status = TestStatus.COMPLETED,
            bytesRead = totalRead,
            totalBytes = maxOf(targetTotal, totalRead),
            progress = 1.0f,
            elapsedTimeMs = finalElapsedMs,
            currentSpeedKbps = 0f,
            averageSpeedKbps = finalAvgKbps,
            targetKbps = targetKbps,
            isThrottlingVerified = verified,
            imageBitmap = full,
            partialImage = false,
            shapingExemptTransport = false
        )
        _testState.value = finalState
        Log.d(
            TAG,
            "DownloadManager transport completed bytes=$totalRead elapsedMs=$finalElapsedMs " +
                "avgKbps=$finalAvgKbps target=$targetKbps verified=$verified"
        )
        finishOnMain(onComplete, finalState)
        return true
    }

    /** Reads at most [limit] bytes of the in-flight download for band decode. */
    private fun readDelimitedBytes(dest: java.io.File, limit: Long): ByteArray {
        return runCatching {
            dest.inputStream().use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(READ_BUFFER_SIZE)
                var remaining = limit.coerceAtLeast(0L)
                while (remaining > 0) {
                    val n = input.read(buf, 0, minOf(remaining, buf.size.toLong()).toInt())
                    if (n <= 0) break
                    out.write(buf, 0, n)
                    remaining -= n
                }
                out.toByteArray()
            }
        }.getOrDefault(ByteArray(0))
    }

    // ─── Secondary transport: Shizuku curl (shell uid = shaped) ──────────────

    private suspend fun measureViaShizuku(
        rateBytesPerSec: Long,
        onComplete: ((TestState) -> Unit)?
    ): Boolean {
        val process = shizuku.runCommand(
            listOf("curl", "-s", "-S", "-L", "--connect-timeout", "5", DEFAULT_TEST_IMAGE_URL)
        ) ?: return false

        return try {
            val targetKbps = (rateBytesPerSec * 8f) / 1000f
            val targetTotal = queryRemoteSize()

            val input = process.inputStream
            val buffer = ByteArray(READ_BUFFER_SIZE)
            val accumulator = ByteArrayOutputStream()
            var totalRead = 0L
            var magicChecked = false

            val startTime = System.nanoTime()
            val deadlineNanos = startTime + MAX_TEST_DURATION_MS * 1_000_000L
            var lastUiNanos = startTime
            var windowStartNanos = startTime
            var windowBytes = 0L
            var instKbps = 0f
            var lastRegionAt = 0L
            var bandBitmap: ImageBitmap? = null
            var endedByDeadline = false

            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                if (!magicChecked) {
                    // A non-JPEG payload means curl delivered an error page:
                    // treat the transport as failed, not as measurement.
                    if (totalRead == 0L && !(buffer[0] == 0xFF.toByte() && buffer[1] == 0xD8.toByte())) {
                        Log.w(TAG, "Shizuku curl delivered non-JPEG data; transport failed")
                        process.destroy()
                        return false
                    }
                    magicChecked = true
                }
                accumulator.write(buffer, 0, read)
                totalRead += read
                windowBytes += read

                val now = System.nanoTime()
                if (targetTotal > 0 && totalRead >= targetTotal) break
                if (now >= deadlineNanos) {
                    endedByDeadline = true
                    break
                }

                if (now - lastUiNanos >= UI_UPDATE_INTERVAL_MS * 1_000_000L) {
                    val windowSec = (now - windowStartNanos) / 1_000_000_000f
                    if (windowSec > 0.1f) {
                        instKbps = (windowBytes * 8f) / windowSec / 1000f
                        windowStartNanos = now
                        windowBytes = 0L
                    }
                    val elapsedMs = (now - startTime) / 1_000_000L
                    val avgKbps = if (elapsedMs > 0) (totalRead * 8f) / (elapsedMs / 1000f) / 1000f else 0f
                    val progress = if (targetTotal > 0) {
                        (totalRead.toFloat() / targetTotal.toFloat()).coerceIn(0f, 1f)
                    } else {
                        // Host without a HEAD content-length: use the time
                        // fraction against the deadline as a visualization proxy.
                        (elapsedMs.toFloat() / MAX_TEST_DURATION_MS).coerceIn(0f, 1f)
                    }

                    if (totalRead - lastRegionAt >= (targetTotal / 20).coerceAtLeast(MIN_BYTES_FOR_REGION)) {
                        lastRegionAt = totalRead
                        bandBitmap = decodeBandFrom(accumulator.toByteArray(), progress) ?: bandBitmap
                    }

                    _testState.value = _testState.value.copy(
                        status = TestStatus.RUNNING,
                        bytesRead = totalRead,
                        totalBytes = targetTotal,
                        progress = progress,
                        elapsedTimeMs = elapsedMs,
                        currentSpeedKbps = instKbps,
                        averageSpeedKbps = avgKbps,
                        imageBitmap = bandBitmap,
                        partialImage = true,
                        shapingExemptTransport = false
                    )
                    lastUiNanos = now
                }
            }
            input.close()
            val finalElapsedMs = ((System.nanoTime() - startTime) / 1_000_000L).coerceAtLeast(1L)
            val finalAvgKbps = (totalRead * 8f) / (finalElapsedMs / 1000f) / 1000f

            val data = accumulator.toByteArray()
            val full = BitmapFactory.decodeByteArray(data, 0, data.size)?.asImageBitmap()
            if (full == null) {
                Log.w(TAG, "Shizuku curl stream is not a decodable image; transport failed")
                process.destroy()
                return false
            }

            val completed = if (targetTotal > 0) totalRead >= targetTotal else !endedByDeadline
            if (endedByDeadline && !completed) {
                Log.w(TAG, "Shizuku transport timed out (${totalRead}B in ${finalElapsedMs}ms)")
                process.destroy()
                return false
            }

            // Shell uid is shaping-governed, so the band check is a real verdict.
            val verified = completed && targetKbps > 0f &&
                finalAvgKbps >= targetKbps * 0.4f && finalAvgKbps <= targetKbps * 1.6f

            val finalState = _testState.value.copy(
                status = TestStatus.COMPLETED,
                bytesRead = totalRead,
                totalBytes = maxOf(targetTotal, totalRead),
                progress = 1.0f,
                elapsedTimeMs = finalElapsedMs,
                currentSpeedKbps = 0f,
                averageSpeedKbps = finalAvgKbps,
                targetKbps = targetKbps,
                isThrottlingVerified = verified,
                imageBitmap = full,
                partialImage = false,
                shapingExemptTransport = false
            )
            _testState.value = finalState
            Log.d(
                TAG,
                "Shizuku transport completed bytes=$totalRead elapsedMs=$finalElapsedMs " +
                    "avgKbps=$finalAvgKbps target=$targetKbps verified=$verified"
            )
            finishOnMain(onComplete, finalState)
            true
        } catch (e: CancellationException) {
            process.destroy()
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Shizuku transport unavailable: ${e.message}")
            runCatching { process.destroy() }
            false
        }
    }

    /** Content-Length of the remote test image via a HEAD request (shell uid). */
    private fun queryRemoteSize(): Long {
        val process = shizuku.runCommand(listOf("curl", "-s", "-I", "-L", DEFAULT_TEST_IMAGE_URL))
            ?: return 0L
        return try {
            val head = process.inputStream.bufferedReader().use { it.readText() }
            Regex("(?i)content-length:\\s*(\\d+)")
                .find(head)
                ?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        } finally {
            runCatching { process.destroy() }
        }
    }

    /**
     * Decodes the top band of the JPEG prefix delivered so far: as many scan
     * rows as [progress] has delivered. Returns null while the header is
     * missing or the decoder cannot read the partial data (E3b degradation
     * path: the measurement keeps running, visualization starts later).
     */
    private fun decodeBandFrom(data: ByteArray, progress: Float): ImageBitmap? {
        if (data.size < MIN_BYTES_FOR_REGION) return null
        return runCatching {
            val decoder = BitmapRegionDecoder.newInstance(ByteArrayInputStream(data), false)
                ?: return@runCatching null
            try {
                val rows = (decoder.height * progress).toInt().coerceAtLeast(1)
                decoder.decodeRegion(
                    Rect(0, 0, decoder.width, rows),
                    BitmapFactory.Options().apply { inSampleSize = 2 }
                )?.asImageBitmap()
            } finally {
                runCatching { decoder.recycle() }
            }
        }.getOrNull()
    }

    // ─── Fallback transport: app's own (shaping-exempt) stack ──────────────

    /** Opens the remote test stream, falling back to the bundled asset (S3-06). */
    private fun openTestStream(): Pair<TestStream?, String?> {
        return try {
            val request = Request.Builder().url(DEFAULT_TEST_IMAGE_URL).build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                throw IOException("HTTP ${response.code}: ${response.message}")
            }
            val body = response.body ?: throw IOException("Empty response body")
            val declaredLength = body.contentLength().coerceAtLeast(0L)
            TestStream(body, declaredLength, usingLocalAsset = false) to null
        } catch (e: Exception) {
            Log.w(TAG, "Remote test image unavailable (${e.message}); using bundled asset")
            try {
                val assetLength = context.assets.openFd(ASSET_FALLBACK_PATH).use { it.length }
                val assetStream = context.assets.open(ASSET_FALLBACK_PATH)
                val body = object : okhttp3.ResponseBody() {
                    override fun contentType() = "image/jpeg".toMediaType()
                    override fun contentLength() = assetLength
                    override fun source() = assetStream.source().buffer()
                }
                TestStream(body, assetLength, usingLocalAsset = true) to null
            } catch (assetError: Exception) {
                Log.e(TAG, "Bundled test asset unavailable: ${assetError.message}", assetError)
                null to (assetError.localizedMessage ?: "Test source unavailable")
            }
        }
    }

    private suspend fun measureStream(
        stream: TestStream,
        rateBytesPerSec: Long,
        onComplete: ((TestState) -> Unit)?
    ) {
        val targetKbps = (rateBytesPerSec * 8f) / 1000f
        val buffer = ByteArray(READ_BUFFER_SIZE)
        val accumulator = ByteArrayOutputStream()
        val input = stream.source.byteStream()

        val startTime = System.nanoTime()
        val deadlineNanos = startTime + MAX_TEST_DURATION_MS * 1_000_000L
        var totalRead = 0L
        var lastUiNanos = startTime
        var windowStartNanos = startTime
        var windowBytes = 0L
        var instKbps = 0f
        var endedByDeadline = false

        val targetTotal = stream.totalBytesHint

        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            accumulator.write(buffer, 0, read)
            totalRead += read
            windowBytes += read

            val now = System.nanoTime()
            if (targetTotal > 0 && totalRead >= targetTotal) break
            if (now >= deadlineNanos) {
                endedByDeadline = true
                break
            }

            if (now - lastUiNanos >= UI_UPDATE_INTERVAL_MS * 1_000_000L) {
                val windowSec = (now - windowStartNanos) / 1_000_000_000f
                if (windowSec > 0.1f) {
                    instKbps = (windowBytes * 8f) / windowSec / 1000f
                    windowStartNanos = now
                    windowBytes = 0L
                }
                val elapsedMs = (now - startTime) / 1_000_000L
                val avgKbps = if (elapsedMs > 0) (totalRead * 8f) / (elapsedMs / 1000f) / 1000f else 0f
                val progress = if (targetTotal > 0) {
                    (totalRead.toFloat() / targetTotal.toFloat()).coerceIn(0f, 1f)
                } else 0f

                _testState.value = _testState.value.copy(
                    status = TestStatus.RUNNING,
                    bytesRead = totalRead,
                    totalBytes = targetTotal,
                    progress = progress,
                    elapsedTimeMs = elapsedMs,
                    currentSpeedKbps = instKbps,
                    averageSpeedKbps = avgKbps,
                    usingLocalAsset = stream.usingLocalAsset,
                    shapingExemptTransport = true
                )
                lastUiNanos = now
            }
        }
        input.close()

        val finalElapsedMs = ((System.nanoTime() - startTime) / 1_000_000L).coerceAtLeast(1L)
        val finalAvgKbps = (totalRead * 8f) / (finalElapsedMs / 1000f) / 1000f
        val decoded = BitmapFactory.decodeByteArray(accumulator.toByteArray(), 0, accumulator.size())
            ?.asImageBitmap()

        val completed = !(endedByDeadline && !(targetTotal > 0 && totalRead >= targetTotal))
        // Fallback (app-uid) downloads run on the shaping-exempt controller uid,
        // so they can never verify the cap; the report must say so (S1-01).
        val verified = completed && targetKbps > 0f &&
            finalAvgKbps >= targetKbps * 0.4f && finalAvgKbps <= targetKbps * 1.6f

        val finalState = _testState.value.copy(
            status = if (completed) TestStatus.COMPLETED else TestStatus.ERROR,
            bytesRead = totalRead,
            totalBytes = maxOf(targetTotal, totalRead),
            progress = 1.0f,
            elapsedTimeMs = finalElapsedMs,
            currentSpeedKbps = 0f,
            averageSpeedKbps = finalAvgKbps,
            targetKbps = targetKbps,
            isThrottlingVerified = if (completed) verified && !stream.usingLocalAsset else null,
            imageBitmap = decoded,
            shapingExemptTransport = true,
            errorMessage = if (endedByDeadline && decoded == null) {
                "Timed out before the stream completed"
            } else {
                _testState.value.errorMessage
            }
        )
        _testState.value = finalState
        finishOnMain(onComplete, finalState)
    }

    /**
     * Delivers [onComplete] exactly once per test (S1-05) and never lets a
     * callback exception (e.g. a rejected service start in the background)
     * escape into the engine's coroutine.
     */
    private suspend fun finishOnMain(onComplete: ((TestState) -> Unit)?, state: TestState) {
        if (!completionDelivered.compareAndSet(false, true)) return
        runCatching {
            withContext(Dispatchers.Main) {
                onComplete?.invoke(state)
            }
        }.onFailure { e -> Log.e(TAG, "Completion callback failed", e) }
    }

    fun reportLimitNotConfirmed(message: String) {
        testJob?.cancel()
        testJob = null
        val errorState = _testState.value.copy(
            status = TestStatus.ERROR,
            currentSpeedKbps = 0f,
            averageSpeedKbps = 0f,
            errorMessage = message
        )
        _testState.value = errorState
    }

    fun cancelTest() {
        testJob?.cancel()
        testJob = null
        _testState.value = _testState.value.copy(
            status = TestStatus.CANCELLED,
            currentSpeedKbps = 0f
        )
    }

    fun reset() {
        testJob?.cancel()
        testJob = null
        _testState.value = TestState()
    }

    /** S1-05: tears the whole engine scope down (ViewModel onCleared). */
    fun shutdown() {
        testJob?.cancel()
        testJob = null
        engineScope.cancel()
    }
}
