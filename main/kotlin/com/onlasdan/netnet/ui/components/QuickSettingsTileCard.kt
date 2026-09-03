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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwipeDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlasdan.netnet.R
import com.onlasdan.netnet.data.SpeedSettings
import com.onlasdan.netnet.model.SpeedFormatter
import com.onlasdan.netnet.model.SpeedSnapshot
import com.onlasdan.netnet.ui.theme.AmberWarning
import com.onlasdan.netnet.ui.theme.AppTheme
import com.onlasdan.netnet.ui.theme.CyanGlow
import com.onlasdan.netnet.ui.theme.CyanPrimary
import com.onlasdan.netnet.ui.theme.EmeraldGlow
import com.onlasdan.netnet.ui.theme.EmeraldSuccess

@Composable
fun QuickSettingsTileCard(
    snapshot: SpeedSnapshot,
    settings: SpeedSettings,
    isServiceRunning: Boolean,
    isPaused: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors

    val speedLabel = if (isServiceRunning && !isPaused) {
        SpeedFormatter.formatShortChip(snapshot, settings.displayMode, settings.speedUnit)
    } else if (isPaused) {
        "Paused"
    } else {
        "Off"
    }

    val isActive = isServiceRunning && !isPaused

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceElevated)
            .border(1.dp, colors.cardBorder, RoundedCornerShape(20.dp))
            .padding(16.dp)
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
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(CyanPrimary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DashboardCustomize,
                        contentDescription = "Quick Settings Tile",
                        tint = CyanGlow,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Quick Settings Tile",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "Status bar dropdown shortcut for 1-tap control",
                        fontSize = 11.sp,
                        color = colors.textSecondary
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isActive) EmeraldSuccess.copy(alpha = 0.15f) else colors.surfaceHighlight)
                    .border(1.dp, if (isActive) EmeraldSuccess.copy(alpha = 0.4f) else colors.cardBorder, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (isActive) "AVAILABLE" else "IDLE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) EmeraldGlow else colors.textSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Android QS Tile Preview Mockup
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF0F172A))
                .border(1.dp, colors.cardBorder, RoundedCornerShape(14.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Simulated QS Tile Button
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isActive) CyanPrimary else Color(0xFF1E293B))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (isActive) Color.White.copy(alpha = 0.2f) else Color(0xFF334155)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_speed_indicator),
                        contentDescription = null,
                        tint = if (isActive) Color.White else Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Net Speed",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isActive) Color.White else Color(0xFFE2E8F0)
                    )
                    Text(
                        text = speedLabel,
                        fontSize = 11.sp,
                        color = if (isActive) Color.White.copy(alpha = 0.85f) else Color(0xFF94A3B8)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Status: ${if (isActive) "Active" else if (isPaused) "Paused" else "Off"}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isActive) EmeraldGlow else AmberWarning
                )
                Text(
                    text = "Tap tile in notification shade to toggle or view live speed.",
                    fontSize = 10.5.sp,
                    color = Color(0xFF94A3B8),
                    lineHeight = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Steps instructions
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surfaceHighlight)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "HOW TO ADD TILE TO QUICK SETTINGS:",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textSecondary
            )
            Text(
                text = "1. Swipe down twice from the top of your screen to open Quick Settings.\n2. Tap the Edit (Pencil ✏️) icon to customize active tiles.\n3. Scroll down to find 'Net Speed' and drag it to your active tiles.",
                fontSize = 11.sp,
                color = colors.textPrimary,
                lineHeight = 16.sp
            )
        }
    }
}
