package com.onlasdan.netnet.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlasdan.netnet.model.SpeedFormatter
import com.onlasdan.netnet.model.SpeedSnapshot
import com.onlasdan.netnet.model.SpeedUnit
import com.onlasdan.netnet.service.FloatingBubbleService
import com.onlasdan.netnet.ui.theme.AmberWarning
import com.onlasdan.netnet.ui.theme.AppTheme
import com.onlasdan.netnet.ui.theme.CyanGlow
import com.onlasdan.netnet.ui.theme.CyanPrimary
import com.onlasdan.netnet.ui.theme.EmeraldGlow
import com.onlasdan.netnet.ui.theme.EmeraldSuccess
import com.onlasdan.netnet.ui.theme.PurpleAccent

@Composable
fun FloatingBubbleCard(
    snapshot: SpeedSnapshot,
    unit: SpeedUnit,
    isBubbleActive: Boolean,
    onToggleBubble: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = AppTheme.colors

    // Deferred to LaunchedEffect below: Settings.canDrawOverlays() is binder
    // IPC and must not run synchronously inside the composable body.
    var canDrawOverlay by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        canDrawOverlay = try {
            Settings.canDrawOverlays(context)
        } catch (_: Throwable) {
            false
        }
    }

    val dlFormatted = SpeedFormatter.formatSpeed(snapshot.downloadBytesPerSec, unit)
    val ulFormatted = SpeedFormatter.formatSpeed(snapshot.uploadBytesPerSec, unit)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceElevated)
            .border(1.dp, if (isBubbleActive) CyanPrimary.copy(alpha = 0.5f) else colors.cardBorder, RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        // Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isBubbleActive) CyanPrimary.copy(alpha = 0.2f) else colors.surfaceHighlight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.BubbleChart,
                        contentDescription = "Floating Speed Bubble",
                        tint = if (isBubbleActive) CyanGlow else colors.textSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Floating Speed Bubble",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        if (isBubbleActive) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(EmeraldSuccess.copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "ON SCREEN",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldGlow
                                )
                            }
                        }
                    }
                    Text(
                        text = "Draggable on-screen overlay over any app",
                        fontSize = 10.sp,
                        color = colors.textSecondary
                    )
                }
            }

            Switch(
                checked = isBubbleActive,
                onCheckedChange = {
                    if (Settings.canDrawOverlays(context)) {
                        canDrawOverlay = true
                        onToggleBubble()
                    } else {
                        canDrawOverlay = false
                        openOverlaySettings(context)
                    }
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = CyanPrimary,
                    checkedTrackColor = CyanPrimary.copy(alpha = 0.3f),
                    uncheckedThumbColor = colors.textTertiary,
                    uncheckedTrackColor = colors.background
                ),
                modifier = Modifier.testTag("floating_bubble_toggle")
            )
        }

        // Overlay Permission Alert if needed
        if (!canDrawOverlay) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(AmberWarning.copy(alpha = 0.12f))
                    .border(1.dp, AmberWarning.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                    .clickable { openOverlaySettings(context) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LockOpen,
                        contentDescription = "Permission",
                        tint = AmberWarning,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Display Over Other Apps Permission",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Required to render floating speed pill",
                            fontSize = 9.5.sp,
                            color = colors.textSecondary
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.OpenInNew,
                    contentDescription = "Open Settings",
                    tint = AmberWarning,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Mini Interactive Preview Container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(colors.surfaceHighlight)
                .padding(8.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "BUBBLE PREVIEW (DRAGGABLE HUD)",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textSecondary
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.TouchApp,
                            contentDescription = null,
                            tint = CyanGlow,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Tap pill to expand",
                            fontSize = 9.sp,
                            color = CyanGlow
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Simulated Floating Pill
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (colors.isDark) Color(0xE60D1527) else colors.surfaceElevated)
                        .border(1.dp, CyanPrimary.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(CyanGlow, CircleShape)
                    )
                    Text(
                        text = "↓ $dlFormatted",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyanGlow,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "↑ $ulFormatted",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = PurpleAccent,
                        fontFamily = FontFamily.Monospace
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(EmeraldSuccess.copy(alpha = 0.18f))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = if (snapshot.pingMs >= 0) "${snapshot.pingMs}ms" else "24ms",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldGlow
                        )
                    }
                }
            }
        }
    }
}

private fun openOverlaySettings(context: Context) {
    try {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        val genericIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(genericIntent)
    }
}
