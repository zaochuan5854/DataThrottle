package com.datathrottle.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile

/**
 * Pins the write-watermark contract against a simulated DownloadProvider:
 * a file preallocated to full length (zeros) that a sequential writer fills
 * in ≤8 KiB chunks.
 */
class WriteWatermarkProbeTest {

    private fun tempFile(): File = File.createTempFile("watermark", ".bin").apply { deleteOnExit() }

    @Test
    fun `watermark tracks sequential writes into a preallocated file`() {
        val f = tempFile()
        RandomAccessFile(f, "rw").use { it.setLength(100_000) }
        val probe = WriteWatermarkProbe(f)

        RandomAccessFile(f, "rw").use { raf ->
            raf.seek(0)
            raf.write(ByteArray(30_000) { 0x41 })
        }
        assertEquals(30_000L, probe.advance())

        RandomAccessFile(f, "rw").use { raf ->
            raf.seek(30_000)
            raf.write(ByteArray(30_000) { 0x42 })
        }
        assertEquals(60_000L, probe.advance())
    }

    @Test
    fun `watermark is the last non-zero byte when a partial chunk trails zeros`() {
        val f = tempFile()
        RandomAccessFile(f, "rw").use { raf ->
            raf.setLength(100_000)
            raf.seek(0)
            // 8 KiB chunk whose last 200 bytes were not yet written.
            val chunk = ByteArray(8192) { 0x55 }
            chunk.fill(0, 8192 - 200, 8192)
            raf.write(chunk)
        }
        val probe = WriteWatermarkProbe(f)
        assertEquals(8192L - 200L, probe.advance())
    }

    @Test
    fun `zero runs shorter than the window do not stall the watermark`() {
        val f = tempFile()
        RandomAccessFile(f, "rw").use { raf ->
            raf.setLength(20_000)
            raf.seek(0)
            // JPEG-like payload: occasional short zero runs (the real asset's max is 8 B).
            val payload = ByteArray(10_000) { (it % 251 + 1).toByte() }
            for (i in 0 until 9000 step 997) payload.fill(0, i, i + 8)
            raf.write(payload)
        }
        val probe = WriteWatermarkProbe(f)
        assertEquals(10_000L, probe.advance())
    }

    @Test
    fun `watermark is monotonic and never exceeds written data`() {
        val f = tempFile()
        val probe = WriteWatermarkProbe(f)
        assertEquals(0L, probe.advance())
        RandomAccessFile(f, "rw").use { raf ->
            raf.setLength(50_000)
            raf.seek(0)
            raf.write(ByteArray(5_000) { 0x33 })
        }
        val first = probe.advance()
        val second = probe.advance()
        assertTrue(first in 1..5_000)
        assertEquals(first, second)
    }
}
