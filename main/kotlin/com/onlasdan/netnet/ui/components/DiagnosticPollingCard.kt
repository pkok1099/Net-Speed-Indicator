package com.onlasdan.netnet.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlasdan.netnet.data.SpeedSettings
import com.onlasdan.netnet.ui.theme.AppTheme
import com.onlasdan.netnet.ui.theme.CyanGlow
import com.onlasdan.netnet.ui.theme.CyanPrimary
import com.onlasdan.netnet.ui.theme.EmeraldSuccess
import com.onlasdan.netnet.ui.theme.PurpleAccent
import com.onlasdan.netnet.ui.theme.PurpleGlow
import java.util.Locale

@Composable
fun DiagnosticPollingCard(
    settings: SpeedSettings,
    onUpdateSettings: ((SpeedSettings) -> SpeedSettings) -> Unit,
    onRefreshPingNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors
    var showCustomDialog by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceElevated)
            .border(1.dp, colors.cardBorder, RoundedCornerShape(12.dp))
            .padding(10.dp)
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
                        imageVector = Icons.Default.Sensors,
                        contentDescription = "Diagnostics Polling",
                        tint = CyanGlow,
                        modifier = Modifier.size(15.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Telemetry Polling Cadence",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "NetworkStateManager live probe interval",
                        fontSize = 10.sp,
                        color = colors.textSecondary
                    )
                }
            }

            // Live status badge
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surfaceHighlight)
                    .border(1.dp, CyanPrimary.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(EmeraldSuccess)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "${String.format(Locale.US, "%.1f", settings.diagnosticPollingIntervalMs / 1000f)}s",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyanGlow
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Slider for Smooth Adjustments
        Slider(
            value = settings.diagnosticPollingIntervalMs.toFloat().coerceIn(1000f, 15000f),
            onValueChange = { newValue ->
                val rounded = (Math.round(newValue / 500f) * 500).toLong().coerceIn(1000L, 15000L)
                onUpdateSettings { it.copy(diagnosticPollingIntervalMs = rounded) }
            },
            valueRange = 1000f..15000f,
            steps = 27,
            colors = SliderDefaults.colors(
                thumbColor = CyanGlow,
                activeTrackColor = CyanPrimary,
                inactiveTrackColor = colors.background
            )
        )

        // Preset Chips Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val presets = listOf(
                1000L to "1.0s (Fast)",
                2000L to "2.0s (Active)",
                4000L to "4.0s (Default)",
                8000L to "8.0s (Relaxed)",
                15000L to "15.0s (Eco)"
            )
            val isCustom = presets.none { it.first == settings.diagnosticPollingIntervalMs }

            presets.forEach { (ms, label) ->
                val isSelected = settings.diagnosticPollingIntervalMs == ms
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) CyanPrimary.copy(alpha = 0.22f) else colors.surfaceHighlight)
                        .border(1.dp, if (isSelected) CyanPrimary else colors.cardBorder, RoundedCornerShape(8.dp))
                        .clickable { onUpdateSettings { it.copy(diagnosticPollingIntervalMs = ms) } }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 10.sp,
                        maxLines = 1,
                        softWrap = false,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) CyanGlow else colors.textSecondary
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isCustom) PurpleAccent.copy(alpha = 0.2f) else colors.surfaceHighlight)
                .border(1.dp, if (isCustom) PurpleAccent else colors.cardBorder, RoundedCornerShape(8.dp))
                .clickable { showCustomDialog = true }
                .padding(horizontal = 10.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Custom Interval",
                        tint = if (isCustom) PurpleGlow else colors.textSecondary,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isCustom) "${settings.diagnosticPollingIntervalMs}ms" else "Custom",
                        fontSize = 10.sp,
                        maxLines = 1,
                        softWrap = false,
                        fontWeight = if (isCustom) FontWeight.Bold else FontWeight.Medium,
                        color = if (isCustom) PurpleGlow else colors.textSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(color = colors.cardBorder, thickness = 1.dp)
        Spacer(modifier = Modifier.height(8.dp))

        // Action Row: Info + "Probe Now" Instant Trigger
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.NetworkCheck,
                    contentDescription = null,
                    tint = colors.textTertiary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "Controls live ICMP socket ping & jitter cadence",
                    fontSize = 10.sp,
                    color = colors.textSecondary,
                    lineHeight = 13.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = onRefreshPingNow,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = "Probe Now",
                    tint = colors.background,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Probe Now",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.background
                )
            }
        }
    }

    // Custom Interval Dialog
    if (showCustomDialog) {
        var input by remember { mutableStateOf(settings.diagnosticPollingIntervalMs.toString()) }
        var isError by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showCustomDialog = false },
            containerColor = colors.surfaceElevated,
            title = {
                Text(
                    text = "Custom Diagnostic Polling",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter polling delay in milliseconds (500ms – 60000ms):",
                        fontSize = 13.sp,
                        color = colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = input,
                        onValueChange = {
                            input = it
                            val parsed = it.toLongOrNull()
                            isError = parsed == null || parsed !in 500L..60000L
                        },
                        isError = isError,
                        label = { Text("Interval (ms)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanPrimary,
                            unfocusedBorderColor = colors.cardBorder,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = input.toLongOrNull()
                        if (parsed != null && parsed in 500L..60000L) {
                            onUpdateSettings { it.copy(diagnosticPollingIntervalMs = parsed) }
                            showCustomDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary)
                ) {
                    Text("Apply", color = colors.background)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }
}
