package com.onlasdan.netnet.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.onlasdan.netnet.data.SpeedSettings
import com.onlasdan.netnet.model.DetailedNetworkDiagnostics
import com.onlasdan.netnet.model.OneTimeDiagnosticState
import com.onlasdan.netnet.model.PingDiagnosticState
import com.onlasdan.netnet.model.ProcessResourceUsage
import com.onlasdan.netnet.model.SpeedSnapshot
import com.onlasdan.netnet.model.SpeedUnit
import com.onlasdan.netnet.ui.components.DiagnosticPollingCard
import com.onlasdan.netnet.ui.components.NetworkInfoCard
import com.onlasdan.netnet.ui.components.OneTimeDiagnosticCard
import com.onlasdan.netnet.ui.components.ProcessResourceDiagnosticsCard
import com.onlasdan.netnet.ui.components.StaggeredAnimatedItem

@Composable
fun NetworkDiagnosticsScreen(
    snapshot: SpeedSnapshot,
    diagnostics: DetailedNetworkDiagnostics,
    oneTimeDiagnosticState: OneTimeDiagnosticState,
    settings: SpeedSettings,
    resourceUsage: ProcessResourceUsage,
    isServiceRunning: Boolean,
    onRunOneTimeDiagnostic: () -> Unit,
    onCancelOneTimeDiagnostic: () -> Unit,
    onUpdateSettings: ((SpeedSettings) -> SpeedSettings) -> Unit = {},
    onRefreshPingNow: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Unified One-Time Diagnostic Tool (Ping, Jitter, Download/Upload Throughput & UI Summary)
        StaggeredAnimatedItem(index = 0, modifier = Modifier.fillMaxWidth()) {
            OneTimeDiagnosticCard(
                diagnosticState = oneTimeDiagnosticState,
                speedUnit = settings.speedUnit,
                onStartDiagnostic = onRunOneTimeDiagnostic,
                onCancelDiagnostic = onCancelOneTimeDiagnostic
            )
        }

        // 2. Telemetry Polling Cadence & Customization Settings Panel (NetworkStateManager)
        StaggeredAnimatedItem(index = 1, modifier = Modifier.fillMaxWidth()) {
            DiagnosticPollingCard(
                settings = settings,
                onUpdateSettings = onUpdateSettings,
                onRefreshPingNow = onRefreshPingNow
            )
        }

        // 3. Comprehensive Network Telemetry, Signal Strength, DNS Resolver & Interfaces
        StaggeredAnimatedItem(index = 2, modifier = Modifier.fillMaxWidth()) {
            NetworkInfoCard(
                snapshot = snapshot,
                diagnostics = diagnostics
            )
        }

        // 4. Indicator Process Resource Footprint Card (CPU, RAM PSS, Battery savings)
        StaggeredAnimatedItem(index = 3, modifier = Modifier.fillMaxWidth()) {
            ProcessResourceDiagnosticsCard(
                usage = resourceUsage,
                isServiceRunning = isServiceRunning
            )
        }

        Spacer(modifier = Modifier.height(112.dp))
    }
}
