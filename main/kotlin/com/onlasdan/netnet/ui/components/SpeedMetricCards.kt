package com.onlasdan.netnet.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlasdan.netnet.model.SpeedFormatter
import com.onlasdan.netnet.model.SpeedSnapshot
import com.onlasdan.netnet.model.SpeedUnit
import com.onlasdan.netnet.ui.theme.AppTheme

@Composable
fun SpeedMetricCards(
    snapshot: SpeedSnapshot,
    unit: SpeedUnit,
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Download Card
        MetricCard(
            title = "DOWNLOAD",
            speedBytes = snapshot.downloadBytesPerSec,
            peakBytes = snapshot.peakDownloadBytesPerSec,
            sessionBytes = snapshot.sessionRxBytes,
            unit = unit,
            accentColor = colors.accentPrimary,
            glowColor = colors.accentGlow,
            icon = Icons.Default.ArrowDownward,
            modifier = Modifier.weight(1f)
        )

        // Upload Card
        MetricCard(
            title = "UPLOAD",
            speedBytes = snapshot.uploadBytesPerSec,
            peakBytes = snapshot.peakUploadBytesPerSec,
            sessionBytes = snapshot.sessionTxBytes,
            unit = unit,
            accentColor = colors.accentSecondary,
            glowColor = colors.accentSecondaryGlow,
            icon = Icons.Default.ArrowUpward,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun MetricCard(
    title: String,
    speedBytes: Long,
    peakBytes: Long,
    sessionBytes: Long,
    unit: SpeedUnit,
    accentColor: Color,
    glowColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    val (speedVal, speedUnit) = SpeedFormatter.formatSpeedValue(speedBytes, unit)
    val peakFormatted = SpeedFormatter.formatSpeed(peakBytes, unit)
    val sessionFormatted = SpeedFormatter.formatDataSize(sessionBytes)
    val colors = AppTheme.colors

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceElevated)
            .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(accentColor.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textSecondary,
                letterSpacing = 1.sp
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = speedVal,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = speedUnit,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = glowColor,
                modifier = Modifier.padding(bottom = 3.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Peak & Session Details
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Peak",
                    fontSize = 9.sp,
                    color = colors.textSecondary
                )
                Text(
                    text = peakFormatted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Session",
                    fontSize = 9.sp,
                    color = colors.textSecondary
                )
                Text(
                    text = sessionFormatted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary
                )
            }
        }
    }
}
