package com.onlasdan.netnet.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlasdan.netnet.model.OneTimeDiagnosticResult
import com.onlasdan.netnet.model.OneTimeDiagnosticStage
import com.onlasdan.netnet.model.OneTimeDiagnosticState
import com.onlasdan.netnet.model.ServiceSuitability
import com.onlasdan.netnet.model.ServiceSuitabilityGrade
import com.onlasdan.netnet.model.SpeedFormatter
import com.onlasdan.netnet.model.SpeedUnit
import com.onlasdan.netnet.ui.theme.AmberWarning
import com.onlasdan.netnet.ui.theme.AppTheme
import com.onlasdan.netnet.ui.theme.CyanGlow
import com.onlasdan.netnet.ui.theme.CyanPrimary
import com.onlasdan.netnet.ui.theme.EmeraldGlow
import com.onlasdan.netnet.ui.theme.EmeraldSuccess
import com.onlasdan.netnet.ui.theme.PurpleAccent
import com.onlasdan.netnet.ui.theme.PurpleGlow
import com.onlasdan.netnet.ui.theme.RoseError

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OneTimeDiagnosticCard(
    diagnosticState: OneTimeDiagnosticState,
    speedUnit: SpeedUnit,
    onStartDiagnostic: () -> Unit,
    onCancelDiagnostic: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = AppTheme.colors
    var showPingDetails by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceElevated)
            .border(1.dp, colors.cardBorder, RoundedCornerShape(12.dp))
            .padding(10.dp)
            .animateContentSize()
            .testTag("one_time_diagnostic_card")
    ) {
        // --- 1. Top Header ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(CyanPrimary.copy(alpha = 0.25f), PurpleAccent.copy(alpha = 0.25f))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.NetworkCheck,
                        contentDescription = "Diagnostic Tool",
                        tint = CyanGlow,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "One-Time Network Audit",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "Ping, Jitter & Throughput Benchmark",
                        fontSize = 10.sp,
                        color = colors.textSecondary
                    )
                }
            }

            if (!diagnosticState.isRunning && diagnosticState.result != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = EmeraldSuccess.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldSuccess.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "Grade ${diagnosticState.result.networkGrade}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldGlow,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // --- 2. State-Driven Body ---
        when {
            diagnosticState.isRunning -> {
                RunningDiagnosticView(
                    state = diagnosticState,
                    speedUnit = speedUnit,
                    onCancel = onCancelDiagnostic
                )
            }
            diagnosticState.result != null -> {
                CompletedDiagnosticSummaryView(
                    result = diagnosticState.result,
                    speedUnit = speedUnit,
                    showPingDetails = showPingDetails,
                    onTogglePingDetails = { showPingDetails = !showPingDetails },
                    onRetest = onStartDiagnostic,
                    onCopyReport = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("NetSpeed Diagnostic Report", diagnosticState.result.shareableReport)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Diagnostic report copied to clipboard!", Toast.LENGTH_SHORT).show()
                    },
                    onShareReport = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "Network Diagnostic Report - ${diagnosticState.result.networkName}")
                            putExtra(Intent.EXTRA_TEXT, diagnosticState.result.shareableReport)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Diagnostic Report"))
                    }
                )
            }
            else -> {
                IdleDiagnosticView(
                    onStart = onStartDiagnostic
                )
            }
        }
    }
}

@Composable
private fun IdleDiagnosticView(
    onStart: () -> Unit
) {
    val colors = AppTheme.colors

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(colors.surfaceHighlight)
                .padding(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = "Perform an instant, end-to-end network audit:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary
                )
                DiagnosticFeatureBullet(
                    icon = Icons.Default.Timer,
                    text = "Multi-target Ping, Jitter & Packet Loss analysis",
                    tint = CyanGlow
                )
                DiagnosticFeatureBullet(
                    icon = Icons.Default.ArrowDownward,
                    text = "Active Download throughput speed benchmark",
                    tint = CyanGlow
                )
                DiagnosticFeatureBullet(
                    icon = Icons.Default.ArrowUpward,
                    text = "Active Upload throughput speed benchmark",
                    tint = PurpleGlow
                )
                DiagnosticFeatureBullet(
                    icon = Icons.Default.CheckCircle,
                    text = "Service suitability report for 4K Streaming, Gaming & Calls",
                    tint = EmeraldGlow
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = onStart,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = CyanPrimary,
                contentColor = Color.Black
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .testTag("start_full_diagnostic_button")
        ) {
            Icon(
                imageVector = Icons.Default.Speed,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Run Full Diagnostic Test",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun DiagnosticFeatureBullet(
    icon: ImageVector,
    text: String,
    tint: Color
) {
    val colors = AppTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(13.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            fontSize = 11.sp,
            color = colors.textSecondary
        )
    }
}

@Composable
private fun RunningDiagnosticView(
    state: OneTimeDiagnosticState,
    speedUnit: SpeedUnit,
    onCancel: () -> Unit
) {
    val colors = AppTheme.colors
    val animatedProgress by animateFloatAsState(
        targetValue = state.progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "diag_progress"
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Step Stepper Row
        DiagnosticStepIndicators(stage = state.stage)

        Spacer(modifier = Modifier.height(10.dp))

        // Large Live Metric Display Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(colors.surfaceHighlight)
                .border(1.dp, CyanPrimary.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                .padding(vertical = 12.dp, horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = state.stage.label.uppercase(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyanGlow,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))

                if (state.stage == OneTimeDiagnosticStage.DOWNLOAD_PHASE || state.stage == OneTimeDiagnosticStage.UPLOAD_PHASE) {
                    val formattedSpeed = SpeedFormatter.formatSpeed(state.currentLiveSpeedBytesPerSec, speedUnit)
                    Text(
                        text = formattedSpeed,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = colors.textPrimary,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "${SpeedFormatter.formatDataSize(state.currentBytesTransferred)} transferred",
                        fontSize = 11.sp,
                        color = colors.textSecondary
                    )
                } else if (state.stage == OneTimeDiagnosticStage.PING_PHASE) {
                    Text(
                        text = if (state.currentPingMs != null) "${state.currentPingMs} ms" else "Measuring Latency...",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = colors.textPrimary,
                        fontFamily = FontFamily.Monospace
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = CyanGlow,
                            strokeWidth = 2.5.dp
                        )
                        Text(
                            text = "Synthesizing Network Report...",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Progress Bar
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = state.statusMessage,
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                    maxLines = 1
                )
                Text(
                    text = "${(animatedProgress * 100).toInt()}%",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyanGlow
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = CyanPrimary,
                trackColor = colors.surfaceHighlight
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedButton(
            onClick = onCancel,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = RoseError),
            border = androidx.compose.foundation.BorderStroke(1.dp, RoseError.copy(alpha = 0.5f)),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            modifier = Modifier.testTag("cancel_diagnostic_button")
        ) {
            Icon(
                imageVector = Icons.Default.Cancel,
                contentDescription = null,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Cancel Diagnostic", fontSize = 12.sp)
        }
    }
}

@Composable
private fun DiagnosticStepIndicators(stage: OneTimeDiagnosticStage) {
    val steps = listOf(
        Pair("1. Ping", stage == OneTimeDiagnosticStage.PING_PHASE),
        Pair("2. Download", stage == OneTimeDiagnosticStage.DOWNLOAD_PHASE),
        Pair("3. Upload", stage == OneTimeDiagnosticStage.UPLOAD_PHASE),
        Pair("4. Analyze", stage == OneTimeDiagnosticStage.ANALYZING)
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEach { (name, isActive) ->
            val stepColor = if (isActive) CyanGlow else AppTheme.colors.textTertiary
            val bgColor = if (isActive) CyanPrimary.copy(alpha = 0.2f) else AppTheme.colors.surfaceHighlight
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(bgColor)
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Text(
                    text = name,
                    fontSize = 9.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    color = stepColor
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompletedDiagnosticSummaryView(
    result: OneTimeDiagnosticResult,
    speedUnit: SpeedUnit,
    showPingDetails: Boolean,
    onTogglePingDetails: () -> Unit,
    onRetest: () -> Unit,
    onCopyReport: () -> Unit,
    onShareReport: () -> Unit
) {
    val colors = AppTheme.colors

    val gradeColor = when (result.networkGrade) {
        "A+", "A" -> EmeraldSuccess
        "B" -> CyanPrimary
        "C" -> AmberWarning
        "D" -> Color(0xFFFB923C) // Warm Orange
        else -> RoseError
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. Grade & Summary Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            gradeColor.copy(alpha = 0.15f),
                            colors.surfaceHighlight
                        )
                    )
                )
                .border(1.dp, gradeColor.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                .padding(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(gradeColor.copy(alpha = 0.25f))
                        .border(2.dp, gradeColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = result.networkGrade,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = gradeColor
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = result.gradeTitle,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    Text(
                        text = result.gradeSubtitle,
                        fontSize = 10.5.sp,
                        color = colors.textSecondary,
                        lineHeight = 14.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${result.networkName} • ${result.ipAddress}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = CyanGlow
                    )
                }
            }
        }

        // 2. Primary 4-Metric Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Download Card
            MetricSummaryBox(
                modifier = Modifier.weight(1f),
                title = "DOWNLOAD",
                icon = Icons.Default.ArrowDownward,
                iconTint = CyanGlow,
                primaryValue = SpeedFormatter.formatSpeed(result.downloadSpeedBytesPerSec, speedUnit),
                secondaryValue = "Peak: ${SpeedFormatter.formatSpeed(result.peakDownloadBytesPerSec, speedUnit)}",
                accentColor = CyanPrimary
            )

            // Upload Card
            MetricSummaryBox(
                modifier = Modifier.weight(1f),
                title = "UPLOAD",
                icon = Icons.Default.ArrowUpward,
                iconTint = PurpleGlow,
                primaryValue = SpeedFormatter.formatSpeed(result.uploadSpeedBytesPerSec, speedUnit),
                secondaryValue = "Peak: ${SpeedFormatter.formatSpeed(result.peakUploadBytesPerSec, speedUnit)}",
                accentColor = PurpleAccent
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Latency Card
            MetricSummaryBox(
                modifier = Modifier.weight(1f),
                title = "PING LATENCY",
                icon = Icons.Default.Timer,
                iconTint = EmeraldGlow,
                primaryValue = "${result.pingResult.avgLatencyMs} ms",
                secondaryValue = "Min ${result.pingResult.minLatencyMs}ms / Max ${result.pingResult.maxLatencyMs}ms",
                accentColor = EmeraldSuccess
            )

            // Jitter & Loss Card
            MetricSummaryBox(
                modifier = Modifier.weight(1f),
                title = "JITTER & LOSS",
                icon = Icons.Default.Speed,
                iconTint = AmberWarning,
                primaryValue = "${result.pingResult.jitterMs} ms",
                secondaryValue = "${result.pingResult.packetLossPercent}% Packet Loss",
                accentColor = AmberWarning
            )
        }

        // 3. Service Suitability Section (Organized in 2x2 Grid)
        Text(
            text = "Service Experience & Suitability",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
            modifier = Modifier.padding(top = 2.dp)
        )

        val suitabilityChunks = result.suitabilityList.chunked(2)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            suitabilityChunks.forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    rowItems.forEach { item ->
                        ServiceSuitabilityGridItem(
                            item = item,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (rowItems.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // 4. Target Ping Breakdown (Expandable)
        if (result.pingResult.targets.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onTogglePingDetails)
                    .background(colors.surfaceHighlight)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Endpoint Latency Breakdown (${result.pingResult.targets.size} Targets)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary
                )
                Text(
                    text = if (showPingDetails) "Hide" else "Show Details",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyanGlow
                )
            }

            AnimatedVisibility(
                visible = showPingDetails,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surfaceHighlight.copy(alpha = 0.6f))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    result.pingResult.targets.forEach { target ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = target.name,
                                fontSize = 11.sp,
                                color = colors.textSecondary
                            )
                            Text(
                                text = if (target.isSuccess) "${target.latencyMs} ms" else "Failed",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (target.isSuccess) EmeraldGlow else RoseError,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        // 5. Action Buttons (Retest, Copy, Share)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onRetest,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyanPrimary,
                    contentColor = Color.Black
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .testTag("retest_diagnostic_button"),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Test Again", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = onCopyReport,
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.cardBorder),
                modifier = Modifier
                    .height(40.dp)
                    .testTag("copy_diagnostic_report_button")
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy Report",
                    tint = colors.textPrimary,
                    modifier = Modifier.size(14.dp)
                )
            }

            OutlinedButton(
                onClick = onShareReport,
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.cardBorder),
                modifier = Modifier
                    .height(40.dp)
                    .testTag("share_diagnostic_report_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share Report",
                    tint = colors.textPrimary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun MetricSummaryBox(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    primaryValue: String,
    secondaryValue: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surfaceHighlight)
            .border(1.dp, accentColor.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
            .padding(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = title,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textSecondary
            )
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(12.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = primaryValue,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = secondaryValue,
            fontSize = 9.sp,
            color = colors.textTertiary,
            maxLines = 1
        )
    }
}

@Composable
private fun ServiceSuitabilityGridItem(
    item: ServiceSuitability,
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors

    val (badgeColor, badgeText) = when (item.grade) {
        ServiceSuitabilityGrade.EXCELLENT -> Pair(EmeraldSuccess, "EXCELLENT")
        ServiceSuitabilityGrade.GOOD -> Pair(CyanPrimary, "GOOD")
        ServiceSuitabilityGrade.FAIR -> Pair(AmberWarning, "FAIR")
        ServiceSuitabilityGrade.POOR -> Pair(RoseError, "POOR")
    }

    val icon = when (item.iconKey) {
        "streaming" -> Icons.Default.Tv
        "gaming" -> Icons.Default.Gamepad
        "calls" -> Icons.Default.Videocam
        "upload" -> Icons.Default.CloudUpload
        else -> Icons.Default.CheckCircle
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surfaceHighlight)
            .border(1.dp, badgeColor.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(badgeColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = badgeColor,
                    modifier = Modifier.size(13.dp)
                )
            }

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = badgeColor.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor.copy(alpha = 0.35f))
            ) {
                Text(
                    text = badgeText,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = badgeColor,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(5.dp))

        Text(
            text = item.category,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary,
            maxLines = 1
        )
        Text(
            text = item.detailMetric,
            fontSize = 9.5.sp,
            color = colors.textSecondary,
            maxLines = 1
        )
    }
}
