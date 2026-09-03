package com.onlasdan.netnet.ui.screens

import android.Manifest
import android.os.Build
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlasdan.netnet.data.SpeedSettings
import com.onlasdan.netnet.model.SpeedPoint
import com.onlasdan.netnet.model.SpeedSnapshot
import com.onlasdan.netnet.ui.components.LiveSpeedGraph
import com.onlasdan.netnet.ui.components.SpeedMetricCards
import com.onlasdan.netnet.ui.theme.AmberWarning
import com.onlasdan.netnet.ui.theme.AppTheme
import com.onlasdan.netnet.ui.theme.CyanGlow
import com.onlasdan.netnet.ui.theme.CyanPrimary
import com.onlasdan.netnet.ui.theme.EmeraldGlow
import com.onlasdan.netnet.ui.theme.EmeraldSuccess
import com.onlasdan.netnet.ui.theme.RoseError

@Composable
fun SpeedMonitorScreen(
    snapshot: SpeedSnapshot,
    history: List<SpeedPoint>,
    settings: SpeedSettings,
    isRunning: Boolean,
    isPaused: Boolean,
    isTestingSpeed: Boolean,
    hasNotificationPermission: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onToggleService: () -> Unit,
    onTogglePause: () -> Unit,
    onRunSpeedTest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Android 16 Promoted Status Bar Notification Badge Card
        PromotedNotificationBadgeCard(
            hasPermission = hasNotificationPermission,
            onRequestPermission = onRequestNotificationPermission,
            isServiceRunning = isRunning,
            isPaused = isPaused
        )

        // 2. Active Network Mini Banner
        ActiveNetworkStatusBanner(
            snapshot = snapshot,
            isRunning = isRunning && !isPaused
        )

        // 3. Dual Download & Upload Metric Cards
        SpeedMetricCards(
            snapshot = snapshot,
            unit = settings.speedUnit
        )

        // 4. Service Controls Bar (Start/Stop, Pause, Burst Test)
        ServiceControlsBar(
            isRunning = isRunning,
            isPaused = isPaused,
            isTestingSpeed = isTestingSpeed,
            onToggleService = onToggleService,
            onTogglePause = onTogglePause,
            onRunSpeedTest = onRunSpeedTest
        )

        // 5. Real-Time Waveform Chart
        LiveSpeedGraph(
            history = history,
            unit = settings.speedUnit
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun ActiveNetworkStatusBanner(
    snapshot: SpeedSnapshot,
    isRunning: Boolean
) {
    val colors = AppTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceElevated)
            .border(1.dp, colors.cardBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(colors.accentPrimary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Wifi,
                    contentDescription = null,
                    tint = colors.accentGlow,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = if (snapshot.networkName.isNotEmpty() && snapshot.networkName != "Unknown") snapshot.networkName else snapshot.networkType.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Text(
                    text = "Type: ${snapshot.networkType.name} • IP: ${snapshot.ipAddress.ifEmpty { "Dynamic" }}",
                    fontSize = 10.5.sp,
                    color = colors.textSecondary,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        if (snapshot.pingMs >= 0) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (snapshot.pingMs <= 80) EmeraldSuccess.copy(alpha = 0.15f) else AmberWarning.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${snapshot.pingMs} ms",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = if (snapshot.pingMs <= 80) EmeraldGlow else AmberWarning
                )
            }
        }
    }
}

@Composable
private fun PromotedNotificationBadgeCard(
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    isServiceRunning: Boolean,
    isPaused: Boolean
) {
    val colors = AppTheme.colors
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    if (!hasPermission) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(AmberWarning.copy(alpha = 0.12f))
                .border(1.dp, AmberWarning.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                .clickable { onRequestPermission() }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = null,
                    tint = AmberWarning,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Notification Permission Needed",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "Enable to show live speed chip in Android 16 status bar",
                        fontSize = 11.sp,
                        color = colors.textSecondary
                    )
                }
            }
            Text(
                text = "ALLOW",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = AmberWarning,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surfaceElevated)
                .border(1.dp, colors.cardBorder, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            if (isServiceRunning && !isPaused) EmeraldSuccess.copy(alpha = pulseAlpha) else colors.textTertiary,
                            CircleShape
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Android 16 Promoted Live Notification",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = EmeraldGlow,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isServiceRunning) "Chip Live" else "Ready",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = EmeraldGlow
                )
            }
        }
    }
}

@Composable
private fun ServiceControlsBar(
    isRunning: Boolean,
    isPaused: Boolean,
    isTestingSpeed: Boolean,
    onToggleService: () -> Unit,
    onTogglePause: () -> Unit,
    onRunSpeedTest: () -> Unit
) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Main Start/Stop Button
        Button(
            onClick = onToggleService,
            modifier = Modifier
                .weight(1.2f)
                .height(48.dp)
                .testTag("toggle_service_button"),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRunning) RoseError.copy(alpha = 0.2f) else colors.accentPrimary,
                contentColor = if (isRunning) RoseError else colors.background
            ),
            border = if (isRunning) androidx.compose.foundation.BorderStroke(1.dp, RoseError.copy(alpha = 0.6f)) else null
        ) {
            Icon(
                imageVector = if (isRunning) Icons.Default.PowerSettingsNew else Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (isRunning) "Stop Monitor" else "Start Monitor",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Pause / Resume Button
        if (isRunning) {
            OutlinedButton(
                onClick = onTogglePause,
                modifier = Modifier
                    .height(48.dp)
                    .testTag("pause_service_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (isPaused) EmeraldSuccess else AmberWarning
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isPaused) EmeraldSuccess.copy(alpha = 0.5f) else AmberWarning.copy(alpha = 0.5f)
                )
            ) {
                Icon(
                    imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = if (isPaused) "Resume" else "Pause",
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Speed Burst Test Button
        Button(
            onClick = onRunSpeedTest,
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("speed_test_button"),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.surfaceHighlight,
                contentColor = colors.accentGlow
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.cardBorder)
        ) {
            if (isTestingSpeed) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = colors.accentGlow,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Testing...",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = null,
                    tint = AmberWarning,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Burst Test",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
