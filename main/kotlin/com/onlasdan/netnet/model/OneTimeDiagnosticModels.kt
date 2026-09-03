package com.onlasdan.netnet.model

import androidx.compose.runtime.Immutable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class OneTimeDiagnosticStage(val label: String) {
    IDLE("Ready"),
    PING_PHASE("Testing Latency & Jitter"),
    DOWNLOAD_PHASE("Benchmarking Download Throughput"),
    UPLOAD_PHASE("Benchmarking Upload Throughput"),
    ANALYZING("Evaluating Health & Rating"),
    COMPLETED("Test Completed"),
    FAILED("Diagnostic Failed"),
    CANCELLED("Test Cancelled")
}

enum class ServiceSuitabilityGrade(val label: String) {
    EXCELLENT("Excellent"),
    GOOD("Good"),
    FAIR("Fair"),
    POOR("Poor")
}

@Immutable
data class ServiceSuitability(
    val category: String,
    val grade: ServiceSuitabilityGrade,
    val statusDescription: String,
    val detailMetric: String,
    val iconKey: String
)

@Immutable
data class OneTimeDiagnosticResult(
    val timestamp: Long = System.currentTimeMillis(),
    val networkType: NetworkType = NetworkType.WIFI,
    val networkName: String = "Wi-Fi",
    val ipAddress: String = "192.168.1.100",
    val gatewayIp: String? = null,
    val downloadSpeedBytesPerSec: Long = 0L,
    val peakDownloadBytesPerSec: Long = 0L,
    val downloadTotalBytesTransferred: Long = 0L,
    val downloadDurationMs: Long = 0L,
    val uploadSpeedBytesPerSec: Long = 0L,
    val peakUploadBytesPerSec: Long = 0L,
    val uploadTotalBytesTransferred: Long = 0L,
    val uploadDurationMs: Long = 0L,
    val pingResult: PingDiagnosticResult = PingDiagnosticResult(),
    val networkGrade: String = "A+",
    val gradeTitle: String = "Ultra-Fast & Stable",
    val gradeSubtitle: String = "Exceptional throughput with negligible latency and zero packet loss.",
    val suitabilityList: List<ServiceSuitability> = emptyList(),
    val keyInsights: List<String> = emptyList(),
    val shareableReport: String = ""
) {
    val formattedDate: String get() {
        val sdf = SimpleDateFormat("MMM dd, yyyy • HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}

@Immutable
data class OneTimeDiagnosticState(
    val stage: OneTimeDiagnosticStage = OneTimeDiagnosticStage.IDLE,
    val progress: Float = 0f,
    val statusMessage: String = "Ready to start full diagnostic test",
    val currentLiveSpeedBytesPerSec: Long = 0L,
    val currentBytesTransferred: Long = 0L,
    val currentPingMs: Long? = null,
    val result: OneTimeDiagnosticResult? = null,
    val errorMessage: String? = null
) {
    val isRunning: Boolean
        get() = stage == OneTimeDiagnosticStage.PING_PHASE ||
                stage == OneTimeDiagnosticStage.DOWNLOAD_PHASE ||
                stage == OneTimeDiagnosticStage.UPLOAD_PHASE ||
                stage == OneTimeDiagnosticStage.ANALYZING
}
