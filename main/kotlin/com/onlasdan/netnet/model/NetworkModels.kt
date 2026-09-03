package com.onlasdan.netnet.model

import androidx.compose.runtime.Immutable
import java.util.Locale

enum class SpeedUnit(val label: String) {
    BYTES("Bytes (B/s, KB/s, MB/s)"),
    BITS("Bits (bps, Kbps, Mbps)")
}

enum class AppThemeMode(val label: String, val description: String) {
    SYSTEM("Follow System", "Automatically matches your device's light or dark mode"),
    LIGHT("Light Mode", "Crisp high-contrast light theme with optimal daytime readability"),
    DARK("Dark Mode", "Sleek dark theme optimized for low-light environments"),
    OLED("OLED Pure Black", "True pitch black (#000000) for maximum AMOLED battery savings"),
    PINK("Sakura Pink", "Vibrant neon sakura and pastel rose theme with warm aesthetics")
}

enum class DisplayMode(val label: String) {
    BOTH("Download & Upload"),
    DOWNLOAD_ONLY("Download Only"),
    UPLOAD_ONLY("Upload Only"),
    AUTO_HIGHEST("Auto (Highest Speed)")
}

enum class NotificationColorTheme(val label: String, val hexColor: Long, val colorInt: Int) {
    SYSTEM("System Dynamic", 0xFF0284C7, 0xFF0284C7.toInt()),
    CYAN("Electric Cyan", 0xFF06B6D4, 0xFF06B6D4.toInt()),
    PINK("Sakura Pink", 0xFFEF9DFA, 0xFFEF9DFA.toInt()),
    EMERALD("Emerald Green", 0xFF10B981, 0xFF10B981.toInt()),
    PURPLE("Neon Purple", 0xFFA855F7, 0xFFA855F7.toInt()),
    AMBER("Solar Amber", 0xFFF59E0B, 0xFFF59E0B.toInt()),
    ROSE("Vibrant Rose", 0xFFF43F5E, 0xFFF43F5E.toInt()),
    MONOCHROME("Pure White / Slate", 0xFFF8FAFC, 0xFFF8FAFC.toInt())
}

enum class NotificationIconStyle(val label: String, val iconResName: String) {
    DYNAMIC_SPEED("Live Speed Number (Dynamic)", "ic_speed_indicator"),
    SPEEDOMETER("Speedometer Gauge", "ic_speed_indicator"),
    ARROWS("Transfer Arrows", "ic_notif_arrows"),
    SIGNAL("Signal Wave", "ic_notif_signal"),
    MINIMAL_DOT("Minimal Dot", "ic_notif_dot")
}

/**
 * Controls the visible content width/format of the Android 16 promoted status bar chip
 * (the "stuck indicator" pill that appears in the status bar).
 *
 * Per the official Live Updates guidance the chip is capped at 96dp: text under
 * 7 characters renders fully; if less than half fits, only the icon remains.
 * Both options therefore use the guaranteed-readable single-direction form.
 *
 * COMPACT  → just the speed value, e.g. "2.4M" (narrowest)
 * STANDARD → arrow + speed value, e.g. "↓2.4M" (fits ~6-7 chars)
 */
enum class StatusBarChipSize(val label: String, val description: String) {
    COMPACT("Compact", "Only speed value — narrowest pill"),
    STANDARD("Standard", "Arrow + speed (fits the 96dp chip limit)")
}

/**
 * Visual size scale for the notification status bar icon.
 *
 * Applies to BOTH the dynamic-speed icon (which renders speed text into a bitmap,
 * e.g. "2.4M") AND the static vector icons (Gauge / Arrows / Signal / Dot) — the
 * latter is rendered as a scaled bitmap so its visible size in the status bar
 * also respects this setting.
 *
 * SMALL  → ~80% of normal size (subtle, blends into the status bar)
 * NORMAL → 100% (system default)
 * LARGE  → ~125% (more visible, easier to read at a glance)
 */
enum class NotificationIconScale(val label: String, val scale: Float) {
    SMALL("Small", 0.80f),
    NORMAL("Normal", 1.00f),
    LARGE("Large", 1.25f)
}

@Immutable
data class DailyUsageRecord(
    val dateKey: String,
    val dateFormatted: String,
    val dayShortLabel: String,
    val wifiBytes: Long,
    val cellBytes: Long,
    val otherBytes: Long = 0L
) {
    val totalBytes: Long get() = wifiBytes + cellBytes + otherBytes
}

@Immutable
data class UsageAnalyticsSummary(
    val totalWifiBytes: Long = 0L,
    val totalCellBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val dailyAverageBytes: Long = 0L,
    val peakDayBytes: Long = 0L,
    val peakDayLabel: String = "-",
    val wifiPercentage: Float = 0f,
    val cellPercentage: Float = 0f,
    val records: List<DailyUsageRecord> = emptyList()
)

enum class NetworkType(val title: String) {
    WIFI("Wi-Fi"),
    CELLULAR("Mobile Data"),
    ETHERNET("Ethernet"),
    VPN("VPN"),
    OFFLINE("Disconnected")
}

@Immutable
data class SpeedPoint(
    val timestamp: Long,
    val downloadBytesPerSec: Long,
    val uploadBytesPerSec: Long
)

@Immutable
data class SpeedSnapshot(
    val downloadBytesPerSec: Long = 0L,
    val uploadBytesPerSec: Long = 0L,
    val peakDownloadBytesPerSec: Long = 0L,
    val peakUploadBytesPerSec: Long = 0L,
    val sessionRxBytes: Long = 0L,
    val sessionTxBytes: Long = 0L,
    val todayRxBytes: Long = 0L,
    val todayTxBytes: Long = 0L,
    val totalDeviceRxBytes: Long = 0L,
    val totalDeviceTxBytes: Long = 0L,
    val networkType: NetworkType = NetworkType.OFFLINE,
    val networkName: String = "Unknown",
    val ipAddress: String = "127.0.0.1",
    val linkSpeedMbps: Int = 0,
    val pingMs: Long = -1L,
    val timestamp: Long = System.currentTimeMillis()
) {
    val sessionTotalBytes: Long get() = sessionRxBytes + sessionTxBytes
    val todayTotalBytes: Long get() = todayRxBytes + todayTxBytes
}

object SpeedFormatter {
    fun formatSpeed(bytesPerSec: Long, unit: SpeedUnit = SpeedUnit.BYTES): String {
        return if (unit == SpeedUnit.BYTES) {
            formatBytesSpeed(bytesPerSec)
        } else {
            formatBitsSpeed(bytesPerSec)
        }
    }

    fun formatSpeedValue(bytesPerSec: Long, unit: SpeedUnit = SpeedUnit.BYTES): Pair<String, String> {
        return if (unit == SpeedUnit.BYTES) {
            val (value, suffix) = getByteComponents(bytesPerSec)
            Pair(value, "$suffix/s")
        } else {
            val bits = bytesPerSec * 8
            val (value, suffix) = getBitComponents(bits)
            Pair(value, "$suffix/s")
        }
    }

    fun formatDataSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        val tb = gb / 1024.0

        return when {
            tb >= 1.0 -> String.format(Locale.US, "%.2f TB", tb)
            gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
            mb >= 1.0 -> String.format(Locale.US, "%.2f MB", mb)
            kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
            else -> "$bytes B"
        }
    }

    private fun formatBytesSpeed(bytesPerSec: Long): String {
        val (value, suffix) = getByteComponents(bytesPerSec)
        return "$value $suffix/s"
    }

    private fun getByteComponents(bytesPerSec: Long): Pair<String, String> {
        if (bytesPerSec <= 0) return Pair("0.0", "KB")
        val kb = bytesPerSec / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0

        return when {
            gb >= 1.0 -> Pair(String.format(Locale.US, "%.2f", gb), "GB")
            mb >= 1.0 -> Pair(String.format(Locale.US, "%.2f", mb), "MB")
            kb >= 1.0 -> Pair(String.format(Locale.US, "%.1f", kb), "KB")
            else -> Pair(String.format(Locale.US, "%d", bytesPerSec), "B")
        }
    }

    private fun formatBitsSpeed(bytesPerSec: Long): String {
        val bits = bytesPerSec * 8
        val (value, suffix) = getBitComponents(bits)
        return "$value $suffix/s"
    }

    private fun getBitComponents(bits: Long): Pair<String, String> {
        if (bits <= 0) return Pair("0.0", "Kbps")
        val kb = bits / 1000.0
        val mb = kb / 1000.0
        val gb = mb / 1000.0

        return when {
            gb >= 1.0 -> Pair(String.format(Locale.US, "%.2f", gb), "Gbps")
            mb >= 1.0 -> Pair(String.format(Locale.US, "%.2f", mb), "Mbps")
            kb >= 1.0 -> Pair(String.format(Locale.US, "%.1f", kb), "Kbps")
            else -> Pair(String.format(Locale.US, "%d", bits), "bps")
        }
    }

    fun formatCompactSpeed(bytesPerSec: Long, unit: SpeedUnit): String {
        if (bytesPerSec <= 0) return "0K"
        if (unit == SpeedUnit.BITS) {
            val bits = bytesPerSec * 8
            val kb = bits / 1000.0
            val mb = kb / 1000.0
            val gb = mb / 1000.0
            return when {
                gb >= 1.0 -> String.format(Locale.US, "%.1fG", gb)
                mb >= 10.0 -> String.format(Locale.US, "%.0fM", mb)
                mb >= 1.0 -> String.format(Locale.US, "%.1fM", mb)
                kb >= 10.0 -> String.format(Locale.US, "%.0fK", kb)
                kb >= 1.0 -> String.format(Locale.US, "%.1fK", kb)
                else -> "${bits}b"
            }
        } else {
            val kb = bytesPerSec / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0
            return when {
                gb >= 1.0 -> String.format(Locale.US, "%.1fG", gb)
                mb >= 10.0 -> String.format(Locale.US, "%.0fM", mb)
                mb >= 1.0 -> String.format(Locale.US, "%.1fM", mb)
                kb >= 10.0 -> String.format(Locale.US, "%.0fK", kb)
                kb >= 1.0 -> String.format(Locale.US, "%.1fK", kb)
                else -> "${bytesPerSec}B"
            }
        }
    }

    fun formatShortChip(snapshot: SpeedSnapshot, mode: DisplayMode, unit: SpeedUnit): String {
        val dlCompact = formatCompactSpeed(snapshot.downloadBytesPerSec, unit)
        val ulCompact = formatCompactSpeed(snapshot.uploadBytesPerSec, unit)

        return when (mode) {
            DisplayMode.BOTH -> "↓$dlCompact ↑$ulCompact"
            DisplayMode.DOWNLOAD_ONLY -> "↓$dlCompact"
            DisplayMode.UPLOAD_ONLY -> "↑$ulCompact"
            DisplayMode.AUTO_HIGHEST -> {
                if (snapshot.uploadBytesPerSec > snapshot.downloadBytesPerSec) {
                    "↑$ulCompact"
                } else {
                    "↓$dlCompact"
                }
            }
        }
    }
}

@Immutable
data class ProcessResourceUsage(
    val cpuPercent: Float = 0.08f,
    val ramPssMb: Float = 14.5f,
    val ramHeapAllocatedMb: Float = 4.2f,
    val ramHeapMaxMb: Float = 256.0f,
    val estimatedBatteryDrainPerHour: Float = 0.04f,
    val batteryImpactGrade: String = "Ultra-Low (A+)",
    val systemBatteryPercent: Int = 100,
    val isCharging: Boolean = false,
    val serviceUptimeSeconds: Long = 0L,
    val ipcCallsThrottledPerMin: Int = 50,
    val diskWritesSavedPerMin: Int = 56
)

@Immutable
data class NetworkInterfaceItem(
    val name: String,
    val displayName: String,
    val type: NetworkType,
    val isUp: Boolean,
    val isLoopback: Boolean,
    val isVirtual: Boolean,
    val isActiveDefault: Boolean,
    val ipAddresses: List<String>,
    val mtu: Int = 1500
)

@Immutable
data class DetailedNetworkDiagnostics(
    val activeInterfaceName: String = "wlan0",
    val networkType: NetworkType = NetworkType.WIFI,
    val networkName: String = "Wi-Fi Network",
    val connectionStatus: String = "Connected & Validated",
    val isValidated: Boolean = true,
    val isMetered: Boolean = false,
    val isVpn: Boolean = false,
    val downstreamBandwidthMbps: Int = 0,
    val upstreamBandwidthMbps: Int = 0,
    val signalStrengthDbm: Int? = null,
    val signalLevelPercent: Int? = null,
    val wifiFrequencyMhz: Int? = null,
    val wifiBand: String? = null,
    val wifiStandard: String? = null,
    val cellularGeneration: String? = null,
    val cellularOperatorName: String? = null,
    val ipv4Address: String = "127.0.0.1",
    val ipv6Address: String? = null,
    val gatewayAddress: String? = null,
    val dnsServers: List<String> = emptyList(),
    val interfaceList: List<NetworkInterfaceItem> = emptyList()
)

@Immutable
data class CompleteNetworkState(
    val networkType: NetworkType = NetworkType.OFFLINE,
    val networkName: String = "Disconnected",
    val connectionStatus: String = "No Network Connection",
    val isConnected: Boolean = false,
    val isValidated: Boolean = false,
    val isInternetReachable: Boolean = false,
    val isVpn: Boolean = false,
    val isMetered: Boolean = false,
    val pingMs: Long = -1L,
    val jitterMs: Long = 0L,
    val packetLossPercent: Int = 0,
    val linkSpeedMbps: Int = 0,
    val downstreamBandwidthMbps: Int = 0,
    val upstreamBandwidthMbps: Int = 0,
    val signalStrengthDbm: Int? = null,
    val signalLevelPercent: Int? = null,
    val wifiFrequencyMhz: Int? = null,
    val wifiBand: String? = null,
    val wifiStandard: String? = null,
    val cellularGeneration: String? = null,
    val cellularOperatorName: String? = null,
    val ipv4Address: String = "127.0.0.1",
    val ipv6Address: String? = null,
    val gatewayAddress: String? = null,
    val dnsServers: List<String> = emptyList(),
    val interfaceList: List<NetworkInterfaceItem> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toDetailedDiagnostics(): DetailedNetworkDiagnostics {
        return DetailedNetworkDiagnostics(
            activeInterfaceName = interfaceList.firstOrNull { it.isActiveDefault }?.name ?: "wlan0",
            networkType = networkType,
            networkName = networkName,
            connectionStatus = connectionStatus,
            isValidated = isValidated,
            isMetered = isMetered,
            isVpn = isVpn,
            downstreamBandwidthMbps = downstreamBandwidthMbps,
            upstreamBandwidthMbps = upstreamBandwidthMbps,
            signalStrengthDbm = signalStrengthDbm,
            signalLevelPercent = signalLevelPercent,
            wifiFrequencyMhz = wifiFrequencyMhz,
            wifiBand = wifiBand,
            wifiStandard = wifiStandard,
            cellularGeneration = cellularGeneration,
            cellularOperatorName = cellularOperatorName,
            ipv4Address = ipv4Address,
            ipv6Address = ipv6Address,
            gatewayAddress = gatewayAddress,
            dnsServers = dnsServers,
            interfaceList = interfaceList
        )
    }
}

enum class PingDiagnosticStatus {
    IDLE,
    RUNNING,
    COMPLETED,
    FAILED
}

enum class ConnectionQualityRating(val label: String, val description: String) {
    EXCELLENT("Excellent Connection", "Ultra-low latency & zero packet loss. Ideal for competitive gaming, 4K HDR streaming & real-time calls."),
    GOOD("Good Connection", "Low latency and solid stability. Seamless video conferencing, smooth browsing & fast media streaming."),
    FAIR("Fair Connection", "Moderate latency detected. General web browsing works, but slight buffering or input delay may occur."),
    POOR("Poor / High Latency", "High latency or packet loss detected. May experience lag spikes, video buffering, or voice glitching."),
    UNSTABLE("Unstable Connection", "High jitter or intermittent packet dropouts detected. Network routing is fluctuating."),
    OFFLINE("Offline / Disconnected", "Unable to reach local gateway or public internet. Check Wi-Fi or mobile data settings.")
}

@Immutable
data class PingTargetResult(
    val name: String,
    val host: String,
    val ip: String? = null,
    val latencyMs: Long,
    val isSuccess: Boolean,
    val errorMessage: String? = null
)

@Immutable
data class PingDiagnosticResult(
    val timestamp: Long = System.currentTimeMillis(),
    val avgLatencyMs: Long = 0,
    val minLatencyMs: Long = 0,
    val maxLatencyMs: Long = 0,
    val jitterMs: Long = 0,
    val packetLossPercent: Int = 0,
    val qualityRating: ConnectionQualityRating = ConnectionQualityRating.GOOD,
    val summaryAdvice: String = "",
    val targets: List<PingTargetResult> = emptyList(),
    val gatewayLatencyMs: Long? = null,
    val isDnsHealthy: Boolean = true,
    val isGatewayReachable: Boolean = true,
    val isHttpReachable: Boolean = true
)

@Immutable
data class PingDiagnosticState(
    val status: PingDiagnosticStatus = PingDiagnosticStatus.IDLE,
    val progress: Float = 0f,
    val currentStep: String = "",
    val result: PingDiagnosticResult? = null,
    val errorMessage: String? = null
)

