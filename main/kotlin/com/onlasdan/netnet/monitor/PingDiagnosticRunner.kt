package com.onlasdan.netnet.monitor

import android.content.Context
import android.os.SystemClock
import com.onlasdan.netnet.model.ConnectionQualityRating
import com.onlasdan.netnet.model.PingDiagnosticResult
import com.onlasdan.netnet.model.PingTargetResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import kotlin.math.abs
import kotlin.math.roundToInt

object PingDiagnosticRunner {

    private data class TargetConfig(
        val name: String,
        val host: String,
        val port: Int,
        val isHttp: Boolean = false
    )

    suspend fun runDiagnostic(
        context: Context,
        gatewayIp: String?,
        onProgress: (progress: Float, step: String) -> Unit
    ): PingDiagnosticResult = withContext(Dispatchers.IO) {
        val targets = mutableListOf<TargetConfig>()

        // 1. Gateway (if known)
        if (!gatewayIp.isNullOrBlank() && gatewayIp != "0.0.0.0" && gatewayIp != "127.0.0.1") {
            targets.add(TargetConfig(name = "Local Gateway", host = gatewayIp, port = 53))
        }

        // 2. Cloudflare DNS
        targets.add(TargetConfig(name = "Cloudflare DNS", host = "1.1.1.1", port = 53))

        // 3. Google Public DNS
        targets.add(TargetConfig(name = "Google DNS", host = "8.8.8.8", port = 53))

        // 4. Quad9 Secure DNS
        targets.add(TargetConfig(name = "Quad9 DNS", host = "9.9.9.9", port = 53))

        // 5. HTTP Web Endpoint
        targets.add(TargetConfig(name = "Google HTTP 204", host = "https://www.google.com/generate_204", port = 443, isHttp = true))

        val allLatencySamples = mutableListOf<Long>()
        val targetResults = mutableListOf<PingTargetResult>()
        var gatewayLatency: Long? = null
        var isGatewayReachable = true
        var isDnsHealthy = true
        var isHttpReachable = true
        var totalProbes = 0
        var failedProbes = 0

        val totalSteps = targets.size
        targets.forEachIndexed { index, target ->
            val stepFraction = index.toFloat() / totalSteps
            onProgress(stepFraction, "Pinging ${target.name}...")

            val targetSamples = mutableListOf<Long>()
            val sampleCount = if (target.isHttp) 2 else 3

            for (i in 0 until sampleCount) {
                totalProbes++
                val latency = if (target.isHttp) {
                    measureHttpLatency(target.host)
                } else {
                    measureSocketLatency(target.host, target.port)
                }

                if (latency >= 0) {
                    targetSamples.add(latency)
                    allLatencySamples.add(latency)
                } else {
                    failedProbes++
                }
                delay(80L)
            }

            val isSuccess = targetSamples.isNotEmpty()
            val avgTargetLatency = if (isSuccess) targetSamples.average().roundToInt().toLong() else -1L

            if (target.name == "Local Gateway") {
                if (isSuccess) {
                    gatewayLatency = avgTargetLatency
                    isGatewayReachable = true
                } else {
                    isGatewayReachable = false
                }
            } else if (target.isHttp) {
                isHttpReachable = isSuccess
            } else {
                if (!isSuccess && isDnsHealthy) {
                    // check if public dns failing
                    if (target.host == "1.1.1.1" && !isSuccess) {
                        isDnsHealthy = false
                    }
                }
            }

            targetResults.add(
                PingTargetResult(
                    name = target.name,
                    host = target.host,
                    latencyMs = avgTargetLatency,
                    isSuccess = isSuccess,
                    errorMessage = if (!isSuccess) "Connection timed out" else null
                )
            )
        }

        onProgress(0.95f, "Analyzing Jitter & Connection Quality...")
        delay(120L)

        // Calculate metrics
        val validSamples = allLatencySamples.filter { it >= 0 }
        val packetLossPercent = if (totalProbes > 0) ((failedProbes.toFloat() / totalProbes) * 100).roundToInt() else 0

        val avgLatency = if (validSamples.isNotEmpty()) validSamples.average().roundToInt().toLong() else 0L
        val minLatency = validSamples.minOrNull() ?: 0L
        val maxLatency = validSamples.maxOrNull() ?: 0L

        // Jitter calculation (Mean Absolute Difference between successive packets)
        val jitterMs = if (validSamples.size >= 2) {
            var sumDiff = 0L
            for (i in 0 until validSamples.size - 1) {
                sumDiff += abs(validSamples[i + 1] - validSamples[i])
            }
            (sumDiff.toDouble() / (validSamples.size - 1)).roundToInt().toLong()
        } else {
            0L
        }

        // Determine Quality Rating & Troubleshooting Advice
        val qualityRating: ConnectionQualityRating = when {
            validSamples.isEmpty() || packetLossPercent >= 80 -> ConnectionQualityRating.OFFLINE
            packetLossPercent > 15 -> ConnectionQualityRating.POOR
            jitterMs > 40 -> ConnectionQualityRating.UNSTABLE
            avgLatency < 40 && packetLossPercent == 0 && jitterMs <= 12 -> ConnectionQualityRating.EXCELLENT
            avgLatency < 85 && packetLossPercent <= 5 && jitterMs <= 25 -> ConnectionQualityRating.GOOD
            avgLatency < 160 && packetLossPercent <= 15 -> ConnectionQualityRating.FAIR
            else -> ConnectionQualityRating.POOR
        }

        val advice = generateTroubleshootAdvice(
            quality = qualityRating,
            avgLatency = avgLatency,
            jitterMs = jitterMs,
            packetLoss = packetLossPercent,
            gatewayReachable = isGatewayReachable,
            dnsHealthy = isDnsHealthy,
            httpReachable = isHttpReachable
        )

        onProgress(1f, "Diagnostic Completed")

        PingDiagnosticResult(
            avgLatencyMs = avgLatency,
            minLatencyMs = minLatency,
            maxLatencyMs = maxLatency,
            jitterMs = jitterMs,
            packetLossPercent = packetLossPercent,
            qualityRating = qualityRating,
            summaryAdvice = advice,
            targets = targetResults,
            gatewayLatencyMs = gatewayLatency,
            isDnsHealthy = isDnsHealthy,
            isGatewayReachable = isGatewayReachable,
            isHttpReachable = isHttpReachable
        )
    }

    private fun measureSocketLatency(host: String, port: Int, timeoutMs: Int = 1800): Long {
        return try {
            val startTime = SystemClock.elapsedRealtime()
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
            }
            val elapsed = SystemClock.elapsedRealtime() - startTime
            elapsed.coerceAtLeast(1L)
        } catch (_: Exception) {
            -1L
        }
    }

    private fun measureHttpLatency(urlString: String, timeoutMs: Int = 2200): Long {
        return try {
            val startTime = SystemClock.elapsedRealtime()
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.instanceFollowRedirects = true
            connection.requestMethod = "GET"
            connection.connect()
            val responseCode = connection.responseCode
            connection.disconnect()
            val elapsed = SystemClock.elapsedRealtime() - startTime
            if (responseCode in 200..399) elapsed.coerceAtLeast(1L) else -1L
        } catch (_: Exception) {
            -1L
        }
    }

    private fun generateTroubleshootAdvice(
        quality: ConnectionQualityRating,
        avgLatency: Long,
        jitterMs: Long,
        packetLoss: Int,
        gatewayReachable: Boolean,
        dnsHealthy: Boolean,
        httpReachable: Boolean
    ): String {
        return when {
            quality == ConnectionQualityRating.OFFLINE -> {
                if (!gatewayReachable) {
                    "Unable to communicate with local Wi-Fi router / gateway. Try toggling Airplane Mode or reconnecting to Wi-Fi."
                } else {
                    "Local router is reachable, but public Internet is offline. Check WAN fiber/modem cable or ISP status."
                }
            }
            packetLoss > 15 -> {
                "High packet loss ($packetLoss%) detected. Possible Wi-Fi radio interference, distance from AP, or cellular congestion. Move closer to the router or switch frequency bands."
            }
            jitterMs > 35 -> {
                "High latency jitter ($jitterMs ms) detected. Network latency is fluctuating, which causes stuttering in real-time gaming or video calls. Switching to 5GHz Wi-Fi is recommended."
            }
            !dnsHealthy -> {
                "Public DNS resolution response is sluggish or blocked. Consider enabling Private DNS (e.g. 1dot1dot1dot1.cloudflare-dns.com) in Android Settings."
            }
            !httpReachable -> {
                "DNS is active but Web HTTP probe failed. A captive portal (hotel/cafe login page) or restrictive firewall may be blocking traffic."
            }
            quality == ConnectionQualityRating.EXCELLENT -> {
                "Optimal connection health! Ping is extremely low ($avgLatency ms) with zero packet loss. Excellent for gaming, streaming & cloud sync."
            }
            quality == ConnectionQualityRating.GOOD -> {
                "Stable and responsive connection ($avgLatency ms). High reliability for streaming, browsing, and video conferencing."
            }
            quality == ConnectionQualityRating.FAIR -> {
                "Moderate latency ($avgLatency ms). Normal web browsing and video will work well, but you may notice slight delay in real-time applications."
            }
            else -> {
                "Connection latency is elevated ($avgLatency ms). Check background app updates or router bandwidth utilization."
            }
        }
    }
}
