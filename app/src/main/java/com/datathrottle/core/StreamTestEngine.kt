package com.datathrottle.core

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

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
    val imageBitmap: ImageBitmap? = null,
    val errorMessage: String? = null
)

class StreamTestEngine(private val context: Context) {

    companion object {
        private const val TAG = "StreamTestEngine"
        const val TARGET_RATE_BYTES_PER_SEC = 12500L // 100 kbps (12.5 KB/s)
        const val CHUNK_INTERVAL_MS = 50L // 50ms intervals
        const val DEFAULT_TEST_IMAGE_URL = "https://raw.githubusercontent.com/opencv/opencv/master/samples/data/fruits.jpg"
    }

    private val _testState = MutableStateFlow(TestState())
    val testState: StateFlow<TestState> = _testState.asStateFlow()

    private var testJob: Job? = null
    private val engineScope = CoroutineScope(Dispatchers.Default + Job())

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun startTest(
        rateBytesPerSec: Long = TARGET_RATE_BYTES_PER_SEC,
        onComplete: ((TestState) -> Unit)? = null
    ) {
        testJob?.cancel()
        testJob = engineScope.launch {
            try {
                val targetKbps = (rateBytesPerSec * 8f) / 1000f // 100 kbps

                _testState.value = TestState(
                    status = TestStatus.RUNNING,
                    bytesRead = 0L,
                    totalBytes = 0L,
                    progress = 0f,
                    elapsedTimeMs = 0L,
                    currentSpeedKbps = targetKbps
                )

                // Fetch from Remote URL (no fallback)
                val fullBytes: ByteArray = try {
                    val request = Request.Builder().url(DEFAULT_TEST_IMAGE_URL).build()
                    val response = httpClient.newCall(request).execute()
                    if (!response.isSuccessful || response.body == null) {
                        throw IllegalStateException("HTTP ${response.code}: ${response.message}")
                    }
                    response.body!!.bytes()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to fetch remote test image: ${e.message}", e)
                    val errorState = TestState(
                        status = TestStatus.ERROR,
                        errorMessage = e.localizedMessage ?: "Network error fetching test image"
                    )
                    _testState.value = errorState
                    withContext(Dispatchers.Main) {
                        onComplete?.invoke(errorState)
                    }
                    return@launch
                }

                val totalLength = fullBytes.size.toLong()
                val rawBitmap = BitmapFactory.decodeByteArray(fullBytes, 0, fullBytes.size)
                val composeBitmap = rawBitmap?.asImageBitmap()

                if (composeBitmap == null) {
                    val errorState = TestState(
                        status = TestStatus.ERROR,
                        errorMessage = "Failed to decode test image"
                    )
                    _testState.value = errorState
                    withContext(Dispatchers.Main) {
                        onComplete?.invoke(errorState)
                    }
                    return@launch
                }

                _testState.value = _testState.value.copy(
                    totalBytes = totalLength,
                    imageBitmap = composeBitmap
                )

                val startTime = System.currentTimeMillis()
                var currentBytesRead = 0L
                val chunkSize = ((rateBytesPerSec * CHUNK_INTERVAL_MS) / 1000L).toInt().coerceAtLeast(200)

                while (currentBytesRead < totalLength) {
                    val remaining = totalLength - currentBytesRead
                    val bytesToRead = remaining.coerceAtMost(chunkSize.toLong())

                    delay(CHUNK_INTERVAL_MS)
                    currentBytesRead += bytesToRead

                    val elapsed = System.currentTimeMillis() - startTime
                    val progress = (currentBytesRead.toFloat() / totalLength.toFloat()).coerceIn(0f, 1f)
                    val avgKbps = if (elapsed > 0) ((currentBytesRead * 8f) / (elapsed.toFloat() / 1000f)) / 1000f else targetKbps

                    _testState.value = _testState.value.copy(
                        status = TestStatus.RUNNING,
                        bytesRead = currentBytesRead,
                        totalBytes = totalLength,
                        progress = progress,
                        elapsedTimeMs = elapsed,
                        currentSpeedKbps = targetKbps,
                        averageSpeedKbps = avgKbps
                    )
                }

                val finalElapsed = System.currentTimeMillis() - startTime
                val finalAvgKbps = ((totalLength * 8f) / (finalElapsed.toFloat() / 1000f)) / 1000f

                val finalState = _testState.value.copy(
                    status = TestStatus.COMPLETED,
                    bytesRead = totalLength,
                    progress = 1.0f,
                    elapsedTimeMs = finalElapsed,
                    currentSpeedKbps = 0f,
                    averageSpeedKbps = finalAvgKbps
                )
                _testState.value = finalState

                withContext(Dispatchers.Main) {
                    onComplete?.invoke(finalState)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Test error", e)
                val errorState = _testState.value.copy(
                    status = TestStatus.ERROR,
                    errorMessage = e.localizedMessage ?: "Unknown error"
                )
                _testState.value = errorState
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(errorState)
                }
            }
        }
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
}
