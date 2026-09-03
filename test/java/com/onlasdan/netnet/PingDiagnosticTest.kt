package com.onlasdan.netnet

import com.onlasdan.netnet.model.ConnectionQualityRating
import com.onlasdan.netnet.model.PingDiagnosticResult
import com.onlasdan.netnet.model.PingDiagnosticState
import com.onlasdan.netnet.model.PingDiagnosticStatus
import com.onlasdan.netnet.model.PingTargetResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PingDiagnosticTest {

    @Test
    fun testPingDiagnosticStateTransitions() {
        var state = PingDiagnosticState()
        assertEquals(PingDiagnosticStatus.IDLE, state.status)
        assertEquals(0f, state.progress, 0.001f)

        state = state.copy(status = PingDiagnosticStatus.RUNNING, progress = 0.5f, currentStep = "Testing Cloudflare DNS...")
        assertEquals(PingDiagnosticStatus.RUNNING, state.status)
        assertEquals("Testing Cloudflare DNS...", state.currentStep)

        val result = PingDiagnosticResult(
            avgLatencyMs = 28,
            minLatencyMs = 18,
            maxLatencyMs = 38,
            jitterMs = 4,
            packetLossPercent = 0,
            qualityRating = ConnectionQualityRating.EXCELLENT,
            summaryAdvice = "Optimal connection health!",
            targets = listOf(
                PingTargetResult("Cloudflare DNS", "1.1.1.1", null, 18, true),
                PingTargetResult("Google DNS", "8.8.8.8", null, 24, true)
            )
        )

        state = state.copy(status = PingDiagnosticStatus.COMPLETED, progress = 1.0f, result = result)
        assertEquals(PingDiagnosticStatus.COMPLETED, state.status)
        assertNotNull(state.result)
        assertEquals(28L, state.result?.avgLatencyMs)
        assertEquals(ConnectionQualityRating.EXCELLENT, state.result?.qualityRating)
        assertEquals(2, state.result?.targets?.size)
    }

    @Test
    fun testConnectionQualityRatings() {
        assertEquals("Excellent Connection", ConnectionQualityRating.EXCELLENT.label)
        assertEquals("Good Connection", ConnectionQualityRating.GOOD.label)
        assertEquals("Fair Connection", ConnectionQualityRating.FAIR.label)
        assertEquals("Poor / High Latency", ConnectionQualityRating.POOR.label)
        assertEquals("Unstable Connection", ConnectionQualityRating.UNSTABLE.label)
        assertEquals("Offline / Disconnected", ConnectionQualityRating.OFFLINE.label)

        assertTrue(ConnectionQualityRating.EXCELLENT.description.contains("latency"))
    }

    @Test
    fun testPingTargetResultHandling() {
        val successTarget = PingTargetResult(
            name = "Local Gateway",
            host = "192.168.1.1",
            latencyMs = 3,
            isSuccess = true
        )
        assertTrue(successTarget.isSuccess)
        assertEquals(3L, successTarget.latencyMs)

        val failedTarget = PingTargetResult(
            name = "Remote Host",
            host = "198.51.100.1",
            latencyMs = -1,
            isSuccess = false,
            errorMessage = "Connection timed out"
        )
        assertFalse(failedTarget.isSuccess)
        assertEquals("Connection timed out", failedTarget.errorMessage)
    }
}
