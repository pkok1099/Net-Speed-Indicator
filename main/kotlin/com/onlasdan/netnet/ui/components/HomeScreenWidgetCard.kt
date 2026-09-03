package com.onlasdan.netnet.ui.components

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.widget.Toast
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlasdan.netnet.data.SpeedSettings
import com.onlasdan.netnet.model.SpeedFormatter
import com.onlasdan.netnet.model.SpeedSnapshot
import com.onlasdan.netnet.ui.theme.AppTheme
import com.onlasdan.netnet.ui.theme.CyanGlow
import com.onlasdan.netnet.ui.theme.CyanPrimary
import com.onlasdan.netnet.ui.theme.EmeraldGlow
import com.onlasdan.netnet.ui.theme.EmeraldSuccess
import com.onlasdan.netnet.ui.theme.PurpleAccent
import com.onlasdan.netnet.ui.theme.PurpleGlow
import com.onlasdan.netnet.widget.NetSpeedWidgetProvider

@Composable
fun HomeScreenWidgetCard(
    snapshot: SpeedSnapshot,
    settings: SpeedSettings,
    isServiceRunning: Boolean,
    isPaused: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = AppTheme.colors

    val (dlVal, dlUnit) = SpeedFormatter.formatSpeedValue(snapshot.downloadBytesPerSec, settings.speedUnit)
    val (ulVal, ulUnit) = SpeedFormatter.formatSpeedValue(snapshot.uploadBytesPerSec, settings.speedUnit)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceElevated)
            .border(1.dp, colors.cardBorder, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.accentPrimary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Widgets,
                        contentDescription = "Widget",
                        tint = colors.accentGlow,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Home Screen Widget",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "Real-time speeds without opening app",
                        fontSize = 11.sp,
                        color = colors.textSecondary
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(EmeraldSuccess.copy(alpha = 0.15f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "READY",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = EmeraldGlow
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Widget Preview Box
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surfaceHighlight)
                .border(1.5.dp, colors.accentPrimary.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                .padding(12.dp)
        ) {
            // Widget Header Preview
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = null,
                        tint = colors.accentGlow,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (snapshot.networkName.isNotEmpty() && snapshot.networkName != "Unknown") snapshot.networkName else snapshot.networkType.title,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.accentPrimary.copy(alpha = 0.2f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = snapshot.networkType.name,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.accentGlow
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Speeds Preview Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Download
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surfaceElevated)
                        .border(0.5.dp, colors.cardBorder, RoundedCornerShape(10.dp))
                        .padding(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = null,
                            tint = colors.accentGlow,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "DOWNLOAD",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textSecondary
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = dlVal,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = dlUnit,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.accentGlow
                        )
                    }
                    Text(
                        text = "Peak: " + SpeedFormatter.formatSpeed(snapshot.peakDownloadBytesPerSec, settings.speedUnit),
                        fontSize = 8.sp,
                        color = colors.textTertiary
                    )
                }

                // Upload
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surfaceElevated)
                        .border(0.5.dp, colors.cardBorder, RoundedCornerShape(10.dp))
                        .padding(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = null,
                            tint = colors.accentSecondaryGlow,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "UPLOAD",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textSecondary
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = ulVal,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = ulUnit,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.accentSecondaryGlow
                        )
                    }
                    Text(
                        text = "Peak: " + SpeedFormatter.formatSpeed(snapshot.peakUploadBytesPerSec, settings.speedUnit),
                        fontSize = 8.sp,
                        color = colors.textTertiary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Today: " + SpeedFormatter.formatDataSize(snapshot.todayTotalBytes),
                    fontSize = 9.sp,
                    color = colors.textSecondary
                )
                Text(
                    text = if (isPaused) "PAUSED" else if (!isServiceRunning) "STANDBY" else if (snapshot.pingMs >= 0) "${snapshot.pingMs} ms" else "LIVE",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isPaused) Color(0xFFF59E0B) else if (!isServiceRunning) Color(0xFF94A3B8) else Color(0xFF10B981)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    val appWidgetManager = context.getSystemService(AppWidgetManager::class.java)
                    if (appWidgetManager != null && appWidgetManager.isRequestPinAppWidgetSupported) {
                        val provider = ComponentName(context, NetSpeedWidgetProvider::class.java)
                        appWidgetManager.requestPinAppWidget(provider, null, null)
                    } else {
                        Toast.makeText(
                            context,
                            "Touch & hold home screen -> Widgets -> Net Speed Widget",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                },
                modifier = Modifier
                    .weight(1.4f)
                    .height(44.dp)
                    .testTag("pin_widget_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentPrimary,
                    contentColor = colors.background
                )
            ) {
                Icon(
                    imageVector = Icons.Default.GridView,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Pin to Home Screen",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            OutlinedButton(
                onClick = {
                    NetSpeedWidgetProvider.updateAllWidgets(context, snapshot, settings)
                    Toast.makeText(context, "Widgets updated with live speeds!", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .testTag("sync_widget_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = colors.accentGlow
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.cardBorder)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Sync",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
