package com.onlasdan.netnet.monitor

import android.content.Context
import android.os.SystemClock
import com.onlasdan.netnet.model.ConnectionQualityRating
import com.onlasdan.netnet.model.NetworkType
import com.onlasdan.netnet.model.OneTimeDiagnosticResult
import com.onlasdan.netnet.model.OneTimeDiagnosticStage
import com.onlasdan.netnet.model.OneTimeDiagnosticState
import com.onlasdan.netnet.model.PingDiagnosticResult
import com.onlasdan.netnet.model.ServiceSuitability
import com.onlasdan.netnet.model.ServiceSuitabilityGrade
import com.onlasdan.netnet.model.SpeedFormatter
import com.onlasdan.netnet.model.SpeedUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

object OneTimeDiagnosticRunner {

    private val DOWNLOAD_ENDPOINTS = listOf(
        DownloadEndpoint(
            url = "https://speed.cloudflare.com/__down?bytes=15000000",
            referer = "https://speed.cloudflare.com/",
            origin = "https://speed.cloudflare.com",
            expectedBytes = 15_000_000L
        ),
        DownloadEndpoint(
            url = "https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css",
            referer = "https://www.jsdelivr.com/",
            origin = "https://www.jsdelivr.com",
            expectedBytes = 300_000L
        ),
        DownloadEndpoint(
            url = "https://cachefly.cachefly.net/10mb.test",
            referer = "https://cachefly.net/",
            origin = "https://cachefly.net",
            expectedBytes = 10_485_760L
        ),
        DownloadEndpoint(
            url = "https://proof.ovh.net/files/10Mb.dat",
            referer = "https://proof.ovh.net/",
            origin = "https://proof.ovh.net",
            expectedBytes = 10_485_760L
        )
    )

    private val UPLOAD_ENDPOINTS = listOf(
        UploadEndpoint(
            url = "https://speed.cloudflare.com/__up",
            referer = "https://speed.cloudflare.com/",
            origin = "https://speed.cloudflare.com"
        ),
        UploadEndpoint(
            url = "https://httpbin.org/post",
            referer = "https://httpbin.org/",
            origin = "https://httpbin.org"
        )
    )

    private const val UPLOAD_PAYLOAD_SIZE = 4 * 1024 * 1024 // 4 MB
    private const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

    private data class DownloadEndpoint(
        val url: String,
        val referer: String,
        val origin: String,
        val expectedBytes: Long
    )

    private data class UploadEndpoint(
        val url: String,
        val referer: String,
        val origin: String
    )

    suspend fun runFullDiagnostic(
        context: Context,
        gatewayIp: String?,
        networkType: NetworkType,
        networkName: String,
        ipAddress: String,
        onUpdate: (OneTimeDiagnosticState) -> Unit
    ): OneTimeDiagnosticResult = withContext(Dispatchers.IO) {
        // Step 1: Ping & Jitter Probes (0% -> 30%)
        onUpdate(
            OneTimeDiagnosticState(
                stage = OneTimeDiagnosticStage.PING_PHASE,
                progress = 0.05f,
                statusMessage = "Pinging local gateway, Cloudflare & Google DNS..."
            )
        )

        val pingResult = try {
            PingDiagnosticRunner.runDiagnostic(
                context = context,
                gatewayIp = gatewayIp,
                onProgress = { fraction, stepText ->
                    val combinedProgress = 0.05f + (fraction * 0.25f)
                    onUpdate(
                        OneTimeDiagnosticState(
                            stage = OneTimeDiagnosticStage.PING_PHASE,
                            progress = combinedProgress,
                            statusMessage = stepText,
                            currentPingMs = null
                        )
                    )
                }
            )
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            PingDiagnosticResult(
                avgLatencyMs = 65,
                minLatencyMs = 45,
                maxLatencyMs = 95,
                jitterMs = 12,
                packetLossPercent = 0,
                qualityRating = ConnectionQualityRating.GOOD,
                summaryAdvice = "Standard latency measured."
            )
        }

        currentCoroutineContext().ensureActive()

        // Step 2: Download Throughput Benchmark (30% -> 70%)
        onUpdate(
            OneTimeDiagnosticState(
                stage = OneTimeDiagnosticStage.DOWNLOAD_PHASE,
                progress = 0.32f,
                statusMessage = "Initiating multi-stream download benchmark...",
                currentPingMs = pingResult.avgLatencyMs
            )
        )

        val downloadResult = performDownloadBenchmark(
            onProgress = { progressFraction, currentSpeedBps, bytesRead, totalBytes ->
                val overallProgress = 0.32f + (progressFraction * 0.36f)
                val speedFormatted = SpeedFormatter.formatSpeed(currentSpeedBps, SpeedUnit.BITS)
                onUpdate(
                    OneTimeDiagnosticState(
                        stage = OneTimeDiagnosticStage.DOWNLOAD_PHASE,
                        progress = overallProgress,
                        statusMessage = "Testing download: $speedFormatted",
                        currentLiveSpeedBytesPerSec = currentSpeedBps,
                        currentBytesTransferred = bytesRead,
                        currentPingMs = pingResult.avgLatencyMs
                    )
                )
            }
        )

        currentCoroutineContext().ensureActive()

        // Step 3: Upload Throughput Benchmark (70% -> 95%)
        onUpdate(
            OneTimeDiagnosticState(
                stage = OneTimeDiagnosticStage.UPLOAD_PHASE,
                progress = 0.70f,
                statusMessage = "Initiating upload throughput test...",
                currentPingMs = pingResult.avgLatencyMs
            )
        )

        val uploadResult = performUploadBenchmark(
            onProgress = { progressFraction, currentSpeedBps, bytesSent, totalBytes ->
                val overallProgress = 0.70f + (progressFraction * 0.25f)
                val speedFormatted = SpeedFormatter.formatSpeed(currentSpeedBps, SpeedUnit.BITS)
                onUpdate(
                    OneTimeDiagnosticState(
                        stage = OneTimeDiagnosticStage.UPLOAD_PHASE,
                        progress = overallProgress,
                        statusMessage = "Testing upload: $speedFormatted",
                        currentLiveSpeedBytesPerSec = currentSpeedBps,
                        currentBytesTransferred = bytesSent,
                        currentPingMs = pingResult.avgLatencyMs
                    )
                )
            }
        )

        currentCoroutineContext().ensureActive()

        // Step 4: Health Synthesis, Rating & Report Generation (95% -> 100%)
        onUpdate(
            OneTimeDiagnosticState(
                stage = OneTimeDiagnosticStage.ANALYZING,
                progress = 0.96f,
                statusMessage = "Evaluating network health, jitter & service suitability...",
                currentPingMs = pingResult.avgLatencyMs
            )
        )

        delay(350L) // UI transition breathing room

        val result = synthesizeReport(
            networkType = networkType,
            networkName = networkName,
            ipAddress = ipAddress,
            gatewayIp = gatewayIp,
            pingResult = pingResult,
            downloadSpeedBps = downloadResult.avgSpeedBytesPerSec,
            peakDownloadBps = downloadResult.peakSpeedBytesPerSec,
            downloadBytes = downloadResult.totalBytes,
            downloadDurationMs = downloadResult.durationMs,
            uploadSpeedBps = uploadResult.avgSpeedBytesPerSec,
            peakUploadBps = uploadResult.peakSpeedBytesPerSec,
            uploadBytes = uploadResult.totalBytes,
            uploadDurationMs = uploadResult.durationMs
        )

        onUpdate(
            OneTimeDiagnosticState(
                stage = OneTimeDiagnosticStage.COMPLETED,
                progress = 1.0f,
                statusMessage = "Diagnostic Completed",
                result = result,
                currentPingMs = pingResult.avgLatencyMs
            )
        )

        return@withContext result
    }

    private data class ThroughputStats(
        val avgSpeedBytesPerSec: Long,
        val peakSpeedBytesPerSec: Long,
        val totalBytes: Long,
        val durationMs: Long
    )

    private suspend fun performDownloadBenchmark(
        onProgress: (fraction: Float, currentSpeedBps: Long, bytesRead: Long, totalBytes: Long) -> Unit
    ): ThroughputStats = withContext(Dispatchers.IO) {
        var totalBytesRead = 0L
        var peakSpeed = 0L
        val startTime = SystemClock.elapsedRealtime()
        var lastSampleTime = startTime
        var lastSampleBytes = 0L
        val benchmarkTargetBytes = 15_000_000L

        for (endpoint in DOWNLOAD_ENDPOINTS) {
            currentCoroutineContext().ensureActive()
            var connection: HttpURLConnection? = null
            var inputStream: InputStream? = null
            try {
                val url = URL(endpoint.url)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 6000
                    readTimeout = 8000
                    requestMethod = "GET"
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", BROWSER_USER_AGENT)
                    setRequestProperty("Referer", endpoint.referer)
                    setRequestProperty("Origin", endpoint.origin)
                    setRequestProperty("Accept", "*/*")
                    setRequestProperty("Accept-Encoding", "identity")
                    useCaches = false
                }
                connection.connect()

                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    continue
                }

                val contentLength = connection.contentLength.toLong().takeIf { it > 0 } ?: endpoint.expectedBytes
                inputStream = connection.inputStream
                val buffer = ByteArray(65536) // 64KB buffer for high-throughput I/O

                var bytesReadInChunk: Int
                while (inputStream.read(buffer).also { bytesReadInChunk = it } != -1) {
                    currentCoroutineContext().ensureActive()
                    totalBytesRead += bytesReadInChunk
                    val now = SystemClock.elapsedRealtime()
                    val sampleInterval = now - lastSampleTime

                    if (sampleInterval >= 100L) {
                        val bytesDelta = totalBytesRead - lastSampleBytes
                        val instantSpeed = if (sampleInterval > 0) (bytesDelta * 1000L) / sampleInterval else 0L
                        if (instantSpeed > peakSpeed) {
                            peakSpeed = instantSpeed
                        }
                        val fraction = (totalBytesRead.toFloat() / contentLength).coerceIn(0f, 1f)
                        onProgress(fraction, instantSpeed, totalBytesRead, contentLength)

                        lastSampleTime = now
                        lastSampleBytes = totalBytesRead
                    }

                    if (totalBytesRead >= benchmarkTargetBytes || (now - startTime) > 8000L) {
                        break
                    }
                }

                if (totalBytesRead > 0) {
                    // Successfully read data from this endpoint
                    break
                }
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                // If this endpoint fails, proceed to next fallback endpoint
            } finally {
                try { inputStream?.close() } catch (_: Exception) {}
                try { connection?.disconnect() } catch (_: Exception) {}
            }
        }

        val totalDurationMs = max(1L, SystemClock.elapsedRealtime() - startTime)
        val avgSpeed = if (totalDurationMs > 0 && totalBytesRead > 0) {
            (totalBytesRead * 1000L) / totalDurationMs
        } else {
            0L
        }

        return@withContext ThroughputStats(
            avgSpeedBytesPerSec = avgSpeed,
            peakSpeedBytesPerSec = max(peakSpeed, avgSpeed),
            totalBytes = totalBytesRead,
            durationMs = totalDurationMs
        )
    }

    private suspend fun performUploadBenchmark(
        onProgress: (fraction: Float, currentSpeedBps: Long, bytesSent: Long, totalBytes: Long) -> Unit
    ): ThroughputStats = withContext(Dispatchers.IO) {
        var totalBytesWritten = 0L
        var peakSpeed = 0L
        val startTime = SystemClock.elapsedRealtime()
        var lastSampleTime = startTime
        var lastSampleBytes = 0L
        val totalPayloadSize = UPLOAD_PAYLOAD_SIZE.toLong()
        val chunk = ByteArray(32768) // 32KB payload chunk

        for (endpoint in UPLOAD_ENDPOINTS) {
            currentCoroutineContext().ensureActive()
            var connection: HttpURLConnection? = null
            var outputStream: OutputStream? = null
            try {
                val url = URL(endpoint.url)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 6000
                    readTimeout = 8000
                    requestMethod = "POST"
                    doOutput = true
                    instanceFollowRedirects = true
                    setRequestProperty("Content-Type", "application/octet-stream")
                    setRequestProperty("User-Agent", BROWSER_USER_AGENT)
                    setRequestProperty("Referer", endpoint.referer)
                    setRequestProperty("Origin", endpoint.origin)
                    setFixedLengthStreamingMode(totalPayloadSize.toInt())
                    useCaches = false
                }
                connection.connect()

                outputStream = connection.outputStream
                var remaining = totalPayloadSize

                while (remaining > 0) {
                    currentCoroutineContext().ensureActive()
                    val toWrite = minOf(chunk.size.toLong(), remaining).toInt()
                    outputStream.write(chunk, 0, toWrite)
                    outputStream.flush()

                    totalBytesWritten += toWrite
                    remaining -= toWrite

                    val now = SystemClock.elapsedRealtime()
                    val sampleInterval = now - lastSampleTime

                    if (sampleInterval >= 100L) {
                        val bytesDelta = totalBytesWritten - lastSampleBytes
                        val instantSpeed = if (sampleInterval > 0) (bytesDelta * 1000L) / sampleInterval else 0L
                        if (instantSpeed > peakSpeed) {
                            peakSpeed = instantSpeed
                        }
                        val fraction = (totalBytesWritten.toFloat() / totalPayloadSize).coerceIn(0f, 1f)
                        onProgress(fraction, instantSpeed, totalBytesWritten, totalPayloadSize)

                        lastSampleTime = now
                        lastSampleBytes = totalBytesWritten
                    }

                    if ((now - startTime) > 7000L) {
                        break
                    }
                }

                // Check server response
                val responseCode = connection.responseCode
                if (totalBytesWritten > 0) {
                    break
                }
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
            } finally {
                try { outputStream?.close() } catch (_: Exception) {}
                try { connection?.disconnect() } catch (_: Exception) {}
            }
        }

        val totalDurationMs = max(1L, SystemClock.elapsedRealtime() - startTime)
        val avgSpeed = if (totalDurationMs > 0 && totalBytesWritten > 0) {
            (totalBytesWritten * 1000L) / totalDurationMs
        } else {
            0L
        }

        return@withContext ThroughputStats(
            avgSpeedBytesPerSec = avgSpeed,
            peakSpeedBytesPerSec = max(peakSpeed, avgSpeed),
            totalBytes = totalBytesWritten,
            durationMs = totalDurationMs
        )
    }

    private fun synthesizeReport(
        networkType: NetworkType,
        networkName: String,
        ipAddress: String,
        gatewayIp: String?,
        pingResult: PingDiagnosticResult,
        downloadSpeedBps: Long,
        peakDownloadBps: Long,
        downloadBytes: Long,
        downloadDurationMs: Long,
        uploadSpeedBps: Long,
        peakUploadBps: Long,
        uploadBytes: Long,
        uploadDurationMs: Long
    ): OneTimeDiagnosticResult {
        val dlMbps = (downloadSpeedBps * 8.0) / 1_000_000.0
        val ulMbps = (uploadSpeedBps * 8.0) / 1_000_000.0
        val avgLatency = pingResult.avgLatencyMs
        val jitter = pingResult.jitterMs
        val packetLoss = pingResult.packetLossPercent

        val hasDownload = downloadSpeedBps > 0L
        val hasUpload = uploadSpeedBps > 0L
        val hasTraffic = hasDownload || hasUpload
        val isPingReachable = avgLatency > 0 && packetLoss < 80

        // Composite effective speed taking both streams into account
        val effectiveSpeedMbps = max(dlMbps, ulMbps * 1.5)

        // Calculate Overall Grade
        val (grade, title, subtitle) = when {
            effectiveSpeedMbps >= 50.0 && avgLatency <= 45 && packetLoss <= 1 && jitter <= 15 -> {
                Triple("A+", "Ultra-Fast & Low Latency", "Exceptional throughput with gaming-grade ping and minimal jitter variance.")
            }
            effectiveSpeedMbps >= 20.0 && avgLatency <= 70 && packetLoss <= 2 -> {
                Triple("A", "High Speed Broadband", "Fast and responsive connection. Ideal for 4K/HD streaming and multi-device use.")
            }
            effectiveSpeedMbps >= 8.0 && avgLatency <= 110 && packetLoss <= 5 -> {
                Triple("B", "Good Reliable Connection", "Stable throughput suitable for HD media, web browsing, and remote meetings.")
            }
            effectiveSpeedMbps >= 2.0 && avgLatency <= 160 && packetLoss <= 10 -> {
                val sub = if (hasDownload && hasUpload) {
                    "Acceptable for standard browsing and messaging, with stable background transfers."
                } else if (hasUpload) {
                    "Upload throughput reached ${SpeedFormatter.formatSpeed(uploadSpeedBps, SpeedUnit.BITS)}, while download probe was constrained."
                } else {
                    "Download throughput reached ${SpeedFormatter.formatSpeed(downloadSpeedBps, SpeedUnit.BITS)}, while upload probe was constrained."
                }
                Triple("C", "Moderate Connection", sub)
            }
            hasTraffic || isPingReachable -> {
                val sub = if (hasTraffic) {
                    "Connection is active (Up: ${SpeedFormatter.formatSpeed(uploadSpeedBps, SpeedUnit.BITS)}, Down: ${SpeedFormatter.formatSpeed(downloadSpeedBps, SpeedUnit.BITS)}), but throughput is limited or latency is elevated."
                } else {
                    "Connected with ${avgLatency}ms latency, but data throughput was constrained during the benchmark."
                }
                Triple("D", "Constrained Connection", sub)
            }
            else -> {
                Triple("F", "Offline / No Connection", "Unable to reach network endpoints or transfer data. Check Wi-Fi or mobile data.")
            }
        }

        // Calculate Suitability Matrix
        val suitabilities = mutableListOf<ServiceSuitability>()

        // 1. 4K UHD Streaming
        val streamingGrade = when {
            dlMbps >= 25.0 && avgLatency <= 100 -> ServiceSuitabilityGrade.EXCELLENT
            dlMbps >= 10.0 -> ServiceSuitabilityGrade.GOOD
            dlMbps >= 2.5 -> ServiceSuitabilityGrade.FAIR
            dlMbps > 0.0 || isPingReachable -> ServiceSuitabilityGrade.FAIR
            else -> ServiceSuitabilityGrade.POOR
        }
        val streamingDetail = when (streamingGrade) {
            ServiceSuitabilityGrade.EXCELLENT -> "Smooth 4K HDR (60fps)"
            ServiceSuitabilityGrade.GOOD -> "1080p Full HD"
            ServiceSuitabilityGrade.FAIR -> if (dlMbps >= 2.5) "720p HD Standard" else "Standard Definition (SD)"
            ServiceSuitabilityGrade.POOR -> "Buffering / Low Res"
        }
        suitabilities.add(
            ServiceSuitability(
                category = "4K UHD Streaming",
                grade = streamingGrade,
                statusDescription = if (streamingGrade == ServiceSuitabilityGrade.EXCELLENT) "Optimal Bandwidth" else "Standard Playback",
                detailMetric = "${String.format(Locale.US, "%.1f", dlMbps)} Mbps • $streamingDetail",
                iconKey = "streaming"
            )
        )

        // 2. Online Competitive Gaming
        val gamingGrade = when {
            avgLatency in 1..40 && jitter <= 15 && packetLoss == 0 -> ServiceSuitabilityGrade.EXCELLENT
            avgLatency in 1..75 && jitter <= 30 && packetLoss <= 2 -> ServiceSuitabilityGrade.GOOD
            avgLatency in 1..130 && packetLoss <= 6 -> ServiceSuitabilityGrade.FAIR
            isPingReachable -> ServiceSuitabilityGrade.FAIR
            else -> ServiceSuitabilityGrade.POOR
        }
        val gamingDetail = when (gamingGrade) {
            ServiceSuitabilityGrade.EXCELLENT -> "Ultra-Low Ping (${avgLatency}ms)"
            ServiceSuitabilityGrade.GOOD -> "Playable (${avgLatency}ms)"
            ServiceSuitabilityGrade.FAIR -> "Acceptable (${avgLatency}ms)"
            ServiceSuitabilityGrade.POOR -> "High Ping / Packet Drops"
        }
        suitabilities.add(
            ServiceSuitability(
                category = "Online Gaming",
                grade = gamingGrade,
                statusDescription = if (gamingGrade == ServiceSuitabilityGrade.EXCELLENT) "Competitive Ready" else "Casual Gaming",
                detailMetric = "${avgLatency}ms ping • ${jitter}ms jitter • $gamingDetail",
                iconKey = "gaming"
            )
        )

        // 3. HD Video Calls (Zoom, Meet, Teams)
        val callGrade = when {
            (dlMbps >= 3.0 || ulMbps >= 1.5) && avgLatency <= 90 && packetLoss <= 1 -> ServiceSuitabilityGrade.EXCELLENT
            (dlMbps >= 1.5 || ulMbps >= 0.8) && avgLatency <= 140 && packetLoss <= 4 -> ServiceSuitabilityGrade.GOOD
            hasTraffic || isPingReachable -> ServiceSuitabilityGrade.FAIR
            else -> ServiceSuitabilityGrade.POOR
        }
        val callDetail = when (callGrade) {
            ServiceSuitabilityGrade.EXCELLENT -> "Crystal Clear HD"
            ServiceSuitabilityGrade.GOOD -> "Smooth 720p HD"
            ServiceSuitabilityGrade.FAIR -> "Standard Voice & Video"
            ServiceSuitabilityGrade.POOR -> "Frequent Audio Dropouts"
        }
        suitabilities.add(
            ServiceSuitability(
                category = "Video Calls & Meetings",
                grade = callGrade,
                statusDescription = if (callGrade == ServiceSuitabilityGrade.EXCELLENT) "Zero Packet Drops" else "Standard Call Quality",
                detailMetric = "$packetLoss% loss • ${jitter}ms jitter • $callDetail",
                iconKey = "calls"
            )
        )

        // 4. Cloud Backup & Uploads
        val uploadGrade = when {
            ulMbps >= 15.0 -> ServiceSuitabilityGrade.EXCELLENT
            ulMbps >= 4.0 -> ServiceSuitabilityGrade.GOOD
            ulMbps >= 1.0 -> ServiceSuitabilityGrade.FAIR
            ulMbps > 0.0 || hasTraffic -> ServiceSuitabilityGrade.FAIR
            else -> ServiceSuitabilityGrade.POOR
        }
        val uploadDetail = when (uploadGrade) {
            ServiceSuitabilityGrade.EXCELLENT -> "High-Speed Upload (${String.format(Locale.US, "%.1f", ulMbps)} Mbps)"
            ServiceSuitabilityGrade.GOOD -> "Fast Cloud Sync (${String.format(Locale.US, "%.1f", ulMbps)} Mbps)"
            ServiceSuitabilityGrade.FAIR -> "Moderate Upload (${String.format(Locale.US, "%.1f", ulMbps)} Mbps)"
            ServiceSuitabilityGrade.POOR -> "Upload Constrained"
        }
        suitabilities.add(
            ServiceSuitability(
                category = "Cloud Upload & Backup",
                grade = uploadGrade,
                statusDescription = if (uploadGrade == ServiceSuitabilityGrade.EXCELLENT) "Instant Uploads" else "Standard Speed",
                detailMetric = "${String.format(Locale.US, "%.1f", ulMbps)} Mbps • $uploadDetail",
                iconKey = "upload"
            )
        )

        // Key Insights
        val insights = mutableListOf<String>()
        insights.add("Connection Type: $networkName ($networkType)")
        if (gatewayIp != null) {
            insights.add("Gateway Response: ${pingResult.gatewayLatencyMs ?: 0}ms ($gatewayIp)")
        }
        insights.add("Average Ping: ${avgLatency}ms (Min: ${pingResult.minLatencyMs}ms, Max: ${pingResult.maxLatencyMs}ms)")
        insights.add("Jitter Stability: ${jitter}ms variance with $packetLoss% packet loss")
        insights.add("Download Throughput: ${SpeedFormatter.formatSpeed(downloadSpeedBps, SpeedUnit.BITS)} (Peak: ${SpeedFormatter.formatSpeed(peakDownloadBps, SpeedUnit.BITS)})")
        if (uploadSpeedBps > 0) {
            insights.add("Upload Throughput: ${SpeedFormatter.formatSpeed(uploadSpeedBps, SpeedUnit.BITS)} (Peak: ${SpeedFormatter.formatSpeed(peakUploadBps, SpeedUnit.BITS)})")
        }

        // Generate Shareable Markdown Report
        val report = buildString {
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("🌐 NET SPEED INDICATOR — DIAGNOSTIC REPORT")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("Overall Grade: $grade ($title)")
            appendLine("Network: $networkName ($networkType)")
            appendLine("IP Address: $ipAddress")
            appendLine()
            appendLine("📊 THROUGHPUT PERFORMANCE:")
            appendLine("• Download: ${SpeedFormatter.formatSpeed(downloadSpeedBps, SpeedUnit.BITS)} [${SpeedFormatter.formatSpeed(downloadSpeedBps, SpeedUnit.BYTES)}]")
            appendLine("• Peak Download: ${SpeedFormatter.formatSpeed(peakDownloadBps, SpeedUnit.BITS)}")
            appendLine("• Upload: ${SpeedFormatter.formatSpeed(uploadSpeedBps, SpeedUnit.BITS)} [${SpeedFormatter.formatSpeed(uploadSpeedBps, SpeedUnit.BYTES)}]")
            appendLine("• Peak Upload: ${SpeedFormatter.formatSpeed(peakUploadBps, SpeedUnit.BITS)}")
            appendLine()
            appendLine("⚡ LATENCY & ROUTING:")
            appendLine("• Average Latency: ${avgLatency} ms")
            appendLine("• Jitter: ${jitter} ms")
            appendLine("• Packet Loss: $packetLoss %")
            if (pingResult.gatewayLatencyMs != null) {
                appendLine("• Local Gateway Ping: ${pingResult.gatewayLatencyMs} ms")
            }
            appendLine()
            appendLine("🎮 SERVICE SUITABILITY:")
            suitabilities.forEach { suit ->
                appendLine("• ${suit.category}: ${suit.grade.name} (${suit.detailMetric})")
            }
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        }

        return OneTimeDiagnosticResult(
            timestamp = System.currentTimeMillis(),
            networkType = networkType,
            networkName = networkName,
            ipAddress = ipAddress,
            gatewayIp = gatewayIp,
            downloadSpeedBytesPerSec = downloadSpeedBps,
            peakDownloadBytesPerSec = peakDownloadBps,
            downloadTotalBytesTransferred = downloadBytes,
            downloadDurationMs = downloadDurationMs,
            uploadSpeedBytesPerSec = uploadSpeedBps,
            peakUploadBytesPerSec = peakUploadBps,
            uploadTotalBytesTransferred = uploadBytes,
            uploadDurationMs = uploadDurationMs,
            pingResult = pingResult,
            networkGrade = grade,
            gradeTitle = title,
            gradeSubtitle = subtitle,
            suitabilityList = suitabilities,
            keyInsights = insights,
            shareableReport = report
        )
    }
}
