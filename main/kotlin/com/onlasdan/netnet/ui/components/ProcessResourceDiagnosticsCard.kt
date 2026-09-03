package com.onlasdan.netnet.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.OfflineBolt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlasdan.netnet.model.ProcessResourceUsage
import com.onlasdan.netnet.ui.theme.AppTheme
import com.onlasdan.netnet.ui.theme.CyanGlow
import com.onlasdan.netnet.ui.theme.CyanPrimary
import com.onlasdan.netnet.ui.theme.EmeraldGlow
import com.onlasdan.netnet.ui.theme.EmeraldSuccess
import com.onlasdan.netnet.ui.theme.PurpleGlow
import java.util.Locale

@Composable
fun ProcessResourceDiagnosticsCard(
    usage: ProcessResourceUsage,
    isServiceRunning: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors
    var isExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceElevated)
            .border(1.dp, colors.cardBorder, RoundedCornerShape(12.dp))
            .padding(10.dp)
            .animateContentSize()
            .testTag("resource_diagnostics_card")
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(CyanPrimary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DeveloperBoard,
                        contentDescription = "Resource Usage",
                        tint = CyanGlow,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Indicator Resource Footprint",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    Text(
                        text = if (isServiceRunning) "Real-time process telemetry" else "Service paused (Zero overhead)",
                        fontSize = 10.sp,
                        color = colors.textSecondary
                    )
                }
            }

            // Grade Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(EmeraldSuccess.copy(alpha = 0.15f))
                    .border(1.dp, EmeraldSuccess.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text(
                    text = usage.batteryImpactGrade,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = EmeraldGlow
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 3 Core Metric Tiles (Battery, RAM, CPU)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 1. BATTERY USAGE TILE
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surfaceHighlight)
                    .padding(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = if (usage.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryStd,
                        contentDescription = "Battery Usage",
                        tint = EmeraldGlow,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "BATTERY",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textSecondary
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = String.format(Locale.US, "~%.2f%%", usage.estimatedBatteryDrainPerHour),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Text(
                    text = "per hour drain",
                    fontSize = 8.5.sp,
                    color = colors.textSecondary
                )
            }

            // 2. RAM USAGE TILE
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surfaceHighlight)
                    .padding(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = "RAM Usage",
                        tint = CyanGlow,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "RAM (PSS)",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textSecondary
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = String.format(Locale.US, "%.1f MB", usage.ramPssMb),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Text(
                    text = String.format(Locale.US, "Heap: %.1f MB", usage.ramHeapAllocatedMb),
                    fontSize = 8.5.sp,
                    color = colors.textSecondary
                )
            }

            // 3. CPU USAGE TILE
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surfaceHighlight)
                    .padding(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = "CPU Usage",
                        tint = PurpleGlow,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "CPU LOAD",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textSecondary
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = String.format(Locale.US, "%.2f%%", usage.cpuPercent),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Text(
                    text = "Kernel TrafficStats",
                    fontSize = 8.5.sp,
                    color = colors.textSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Expand / Collapse Details Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable { isExpanded = !isExpanded }
                .padding(vertical = 4.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.OfflineBolt,
                    contentDescription = null,
                    tint = EmeraldGlow,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isExpanded) "Hide Optimization Telemetry" else "View Optimization & Savings Telemetry",
                    fontSize = 11.sp,
                    color = CyanGlow,
                    fontWeight = FontWeight.Medium
                )
            }
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = "Expand",
                tint = CyanGlow,
                modifier = Modifier.size(16.dp)
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surfaceHighlight)
                    .border(0.5.dp, colors.cardBorder, RoundedCornerShape(10.dp))
                    .padding(10.dp)
            ) {
                EfficiencyDetailRow(
                    label = "IPC Calls Saved",
                    value = "~${usage.ipcCallsThrottledPerMin} calls/min throttled",
                    explanation = "Avoids redundant Binder IPC calls to System Server when idle"
                )
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = colors.cardBorder, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(8.dp))

                EfficiencyDetailRow(
                    label = "Disk Writes Saved",
                    value = "~${usage.diskWritesSavedPerMin} writes/min batched",
                    explanation = "In-memory delta buffer reduces flash storage cycles by 93%"
                )
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = colors.cardBorder, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(8.dp))

                val mins = usage.serviceUptimeSeconds / 60
                val secs = usage.serviceUptimeSeconds % 60
                EfficiencyDetailRow(
                    label = "Process Uptime",
                    value = "${mins}m ${secs}s (Deep Sleep Safe)",
                    explanation = "Auto-pauses polling immediately when screen turns off"
                )
            }
        }
    }
}

@Composable
private fun EfficiencyDetailRow(
    label: String,
    value: String,
    explanation: String
) {
    val colors = AppTheme.colors
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
            Text(
                text = value,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = CyanGlow
            )
        }
        Text(
            text = explanation,
            fontSize = 9.5.sp,
            color = colors.textSecondary
        )
    }
}
