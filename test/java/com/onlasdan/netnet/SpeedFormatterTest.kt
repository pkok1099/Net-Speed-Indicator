package com.onlasdan.netnet

import com.onlasdan.netnet.model.DisplayMode
import com.onlasdan.netnet.model.NetworkType
import com.onlasdan.netnet.model.SpeedFormatter
import com.onlasdan.netnet.model.SpeedSnapshot
import com.onlasdan.netnet.model.SpeedUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedFormatterTest {

    @Test
    fun testFormatSpeedBytes() {
        assertEquals("0.0 KB/s", SpeedFormatter.formatSpeed(0L, SpeedUnit.BYTES))
        assertEquals("500 B/s", SpeedFormatter.formatSpeed(500L, SpeedUnit.BYTES))
        assertEquals("1.50 MB/s", SpeedFormatter.formatSpeed(1_572_864L, SpeedUnit.BYTES))
        assertEquals("10.00 MB/s", SpeedFormatter.formatSpeed(10_485_760L, SpeedUnit.BYTES))
    }

    @Test
    fun testFormatSpeedBits() {
        assertEquals("0.0 Kbps/s", SpeedFormatter.formatSpeed(0L, SpeedUnit.BITS))
        val oneMbps = 125_000L // 125,000 * 8 = 1,000,000 bits = 1 Mbps
        assertTrue(SpeedFormatter.formatSpeed(oneMbps, SpeedUnit.BITS).contains("Mbps"))
    }

    @Test
    fun testFormatDataSize() {
        assertEquals("0 B", SpeedFormatter.formatDataSize(0L))
        assertEquals("100 B", SpeedFormatter.formatDataSize(100L))
        assertEquals("1.0 KB", SpeedFormatter.formatDataSize(1024L))
        assertEquals("1.50 MB", SpeedFormatter.formatDataSize((1.5 * 1024 * 1024).toLong()))
        assertEquals("2.00 GB", SpeedFormatter.formatDataSize(2L * 1024 * 1024 * 1024))
    }

    @Test
    fun testFormatCompactSpeed() {
        assertEquals("0K", SpeedFormatter.formatCompactSpeed(0L, SpeedUnit.BYTES))
        assertEquals("500B", SpeedFormatter.formatCompactSpeed(500L, SpeedUnit.BYTES))
        assertEquals("1.5M", SpeedFormatter.formatCompactSpeed(1_572_864L, SpeedUnit.BYTES))
        assertEquals("10M", SpeedFormatter.formatCompactSpeed(10_485_760L, SpeedUnit.BYTES))
    }

    @Test
    fun testFormatShortChip() {
        val snapshot = SpeedSnapshot(
            downloadBytesPerSec = 2_097_152L, // 2 MB/s
            uploadBytesPerSec = 1_048_576L,   // 1 MB/s
            networkType = NetworkType.WIFI
        )

        val bothChip = SpeedFormatter.formatShortChip(snapshot, DisplayMode.BOTH, SpeedUnit.BYTES)
        assertTrue(bothChip.contains("↓") && bothChip.contains("↑"))

        val downloadChip = SpeedFormatter.formatShortChip(snapshot, DisplayMode.DOWNLOAD_ONLY, SpeedUnit.BYTES)
        assertTrue(downloadChip.startsWith("↓"))

        val uploadChip = SpeedFormatter.formatShortChip(snapshot, DisplayMode.UPLOAD_ONLY, SpeedUnit.BYTES)
        assertTrue(uploadChip.startsWith("↑"))
    }

    @Test
    fun testDynamicSpeedIconComponents() {
        val (zeroVal, zeroUnit) = com.onlasdan.netnet.notification.DynamicSpeedIconRenderer.getSpeedComponents(0L, SpeedUnit.BYTES)
        assertEquals("0", zeroVal)
        assertEquals("K/s", zeroUnit)

        val (mbVal, mbUnit) = com.onlasdan.netnet.notification.DynamicSpeedIconRenderer.getSpeedComponents(2_500_000L, SpeedUnit.BYTES)
        assertEquals("2.4", mbVal)
        assertEquals("M/s", mbUnit)

        val (kbVal, kbUnit) = com.onlasdan.netnet.notification.DynamicSpeedIconRenderer.getSpeedComponents(450_000L, SpeedUnit.BYTES)
        assertEquals("439", kbVal)
        assertEquals("K/s", kbUnit)
    }
}
