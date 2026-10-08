package com.datathrottle.core

import java.io.File
import java.io.RandomAccessFile

/**
 * Tracks the DownloadThread write cursor on the preallocated destination file
 * so the scanline UI advances at ~8 KiB granularity instead of the DB
 * progress column's 73,728 B staircase.
 *
 * Why this works (verified on-device, docs/VERIFICATION.md "write-watermark
 * probe"):
 *  - The HyperOS DownloadProvider preallocates the destination file to the
 *    full content-length ~3 s after enqueue (the provider dex calls
 *    `setLength`; `stat` shows size=396,874 while the transfer is still at
 *    the cap), and unwritten preallocated space reads as zero.
 *  - AOSP `DownloadThread.transferData()` writes sequentially in
 *    BUFFER_SIZE (8 KiB) chunks; only the DB column update is throttled
 *    (MIN_PROGRESS_STEP=64 KiB AND MIN_PROGRESS_TIME=2 s in Constants.java).
 *  - Therefore the end of the last non-zero byte is a lower bound on the
 *    true write cursor: a partial chunk at the cursor can contain trailing
 *    zeros, but every byte before the watermark has definitely been written.
 *
 * Safety: a 512 B all-zero window inside already-written data would stall
 * the watermark (never overshoot it). The test asset (sunrise JPEG,
 * sha256 a048d17e…) has a maximum zero run of 8 bytes, so stalling cannot
 * occur in practice; the DB column remains the floor via maxOf() and the
 * verdict never uses this value.
 */
internal class WriteWatermarkProbe(private val dest: File) {

    private var scanPos = 0L
    private var watermark = 0L
    private val window = ByteArray(WINDOW_BYTES)

    /** Scans forward from the last scanned offset and returns the watermark. */
    fun advance(): Long {
        runCatching {
            RandomAccessFile(dest, "r").use { raf ->
                val len = raf.length()
                while (scanPos < len) {
                    raf.seek(scanPos)
                    val n = raf.read(window, 0, minOf(WINDOW_BYTES.toLong(), len - scanPos).toInt())
                    if (n <= 0) break
                    var lastNonZero = -1
                    for (i in 0 until n) {
                        if (window[i] != 0.toByte()) lastNonZero = i
                    }
                    if (lastNonZero < 0) break
                    watermark = scanPos + lastNonZero + 1
                    scanPos += n
                }
            }
        }
        return watermark
    }

    companion object {
        private const val WINDOW_BYTES = 512
    }
}
