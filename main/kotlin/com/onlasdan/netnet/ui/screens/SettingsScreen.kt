package com.onlasdan.netnet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SpaceDashboard
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.onlasdan.netnet.R
import com.onlasdan.netnet.data.SpeedSettings
import com.onlasdan.netnet.model.AppThemeMode
import com.onlasdan.netnet.model.DisplayMode
import com.onlasdan.netnet.model.NotificationColorTheme
import com.onlasdan.netnet.model.NotificationIconStyle
import com.onlasdan.netnet.model.NotificationIconScale
import com.onlasdan.netnet.model.SpeedUnit
import com.onlasdan.netnet.model.StatusBarChipSize
import com.onlasdan.netnet.ui.theme.AmberWarning
import com.onlasdan.netnet.ui.theme.AppTheme
import com.onlasdan.netnet.ui.theme.CyanGlow
import com.onlasdan.netnet.ui.theme.CyanPrimary
import com.onlasdan.netnet.ui.theme.EmeraldGlow
import com.onlasdan.netnet.ui.theme.EmeraldSuccess
import java.util.Locale

@Composable
fun SettingsScreen(
    settings: SpeedSettings,
    onUpdateSettings: ((SpeedSettings) -> SpeedSettings) -> Unit,
    onResetTodayUsage: () -> Unit,
    isIgnoringBatteryOptimizations: Boolean = false,
    onRequestIgnoreBatteryOptimizations: () -> Unit = {},
    onTriggerWatchdogSync: () -> Unit = {},
    onSetDailySummaryTime: (hour: Int, minute: Int) -> Unit = { _, _ -> },
    onPreviewDailySummary: () -> Boolean = { false },
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors
    val context = LocalContext.current
    var showCustomIntervalDialog by remember { mutableStateOf(false) }
    var showCustomDiagnosticIntervalDialog by remember { mutableStateOf(false) }
    var showCustomThresholdDialog by remember { mutableStateOf(false) }
    var showCustomStuckIntervalDialog by remember { mutableStateOf(false) }
    var showDailySummaryTimeDialog by remember { mutableStateOf(false) }

    // Slider drag values live in local state and are persisted ONLY when the
    // drag finishes — onUpdateSettings writes SharedPreferences AND re-arms an
    // AlarmManager alarm (binder IPC), which must never run per drag frame.
    var idleThresholdDrag by remember { mutableStateOf<Float?>(null) }
    var stuckIntervalDrag by remember { mutableStateOf<Float?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. Appearance & Theme Mode Selector
        SettingsSection(
            icon = Icons.Default.Brightness4,
            title = "Appearance"
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surfaceHighlight)
                    .border(1.dp, colors.cardBorder, RoundedCornerShape(12.dp))
                    .padding(8.dp)
            ) {
                AppThemeMode.entries.forEachIndexed { index, mode ->
                    val isSelected = settings.appThemeMode == mode
                    val modeIcon = when (mode) {
                        AppThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
                        AppThemeMode.LIGHT -> Icons.Default.LightMode
                        AppThemeMode.DARK -> Icons.Default.DarkMode
                        AppThemeMode.OLED -> Icons.Default.Contrast
                        AppThemeMode.PINK -> Icons.Default.Palette
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) colors.accentPrimary.copy(alpha = 0.12f) else Color.Transparent)
                            .border(
                                width = if (isSelected) 1.dp else 0.dp,
                                color = if (isSelected) colors.accentPrimary.copy(alpha = 0.4f) else Color.Transparent,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                onUpdateSettings {
                                    it.copy(
                                        appThemeMode = mode,
                                        isOledTheme = mode == AppThemeMode.OLED
                                    )
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = {
                                onUpdateSettings {
                                    it.copy(
                                        appThemeMode = mode,
                                        isOledTheme = mode == AppThemeMode.OLED
                                    )
                                }
                            },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = colors.accentPrimary,
                                unselectedColor = colors.textTertiary
                            ),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = modeIcon,
                            contentDescription = mode.label,
                            tint = if (isSelected) colors.accentGlow else colors.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = mode.label,
                            fontSize = 12.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) colors.textPrimary else colors.textSecondary,
                            modifier = Modifier.weight(1f)
                        )
                        InfoTooltip(description = mode.description)
                    }
                    if (index < AppThemeMode.entries.size - 1) {
                        HorizontalDivider(color = colors.cardBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
                    }
                }
            }
        }

        // 2. Android 16 Promoted Status Bar Chip
        SettingsSection(
            icon = Icons.Default.NotificationsActive,
            title = "Live Notification"
        ) {
            ToggleSettingRow(
                title = "Status Bar Live Speed Chip",
                subtitle = "Promoted ongoing notification pill directly in Android 16 status bar",
                checked = settings.showStatusBarChip,
                testTag = "status_bar_chip_toggle_page",
                onCheckedChange = { checked ->
                    onUpdateSettings { it.copy(showStatusBarChip = checked) }
                    // Toast reports the actually-applied system state (notification permission,
                    // promoted-notification capability, service running) — not just the switch position.
                    SettingsToggleFeedback.onStatusBarChipToggled(context, checked)
                }
            )
        }

        // 3. Speed Measurement Units
        SettingsSection(
            icon = Icons.Default.Speed,
            title = "Units"
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surfaceHighlight)
                    .border(1.dp, colors.cardBorder, RoundedCornerShape(12.dp))
            ) {
                SpeedUnit.entries.forEachIndexed { index, unit ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onUpdateSettings { it.copy(speedUnit = unit) }
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = settings.speedUnit == unit,
                            onClick = { onUpdateSettings { it.copy(speedUnit = unit) } },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = colors.accentPrimary,
                                unselectedColor = colors.textTertiary
                            ),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = unit.label,
                            fontSize = 12.5.sp,
                            color = if (settings.speedUnit == unit) colors.textPrimary else colors.textSecondary,
                            fontWeight = if (settings.speedUnit == unit) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                    if (index < SpeedUnit.entries.size - 1) {
                        HorizontalDivider(color = colors.cardBorder, thickness = 0.5.dp)
                    }
                }
            }
        }

        // 4. Notification Style & Theme Palette
        SettingsSection(
            icon = Icons.Default.Palette,
            title = "Notification Style"
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surfaceHighlight)
                    .border(1.dp, colors.cardBorder, RoundedCornerShape(12.dp))
                    .padding(10.dp)
            ) {
                // Accent Color Selector
                Text(
                    text = "Notification Accent Color",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    NotificationColorTheme.entries.forEach { theme ->
                        val isSelected = settings.notificationColorTheme == theme
                        val themeColor = Color(theme.colorInt)

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(30.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(themeColor.copy(alpha = if (isSelected) 0.3f else 0.12f))
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) themeColor else colors.cardBorder,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    onUpdateSettings { it.copy(notificationColorTheme = theme) }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = themeColor,
                                    modifier = Modifier.size(14.dp)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(themeColor)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = colors.cardBorder, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(8.dp))

                // Icon Style Selector
                Text(
                    text = "Status Bar & Notification Icon",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    NotificationIconStyle.entries.forEach { iconStyle ->
                        val isSelected = settings.notificationIconStyle == iconStyle
                        val iconDrawableRes = when (iconStyle) {
                            NotificationIconStyle.DYNAMIC_SPEED -> R.drawable.ic_speed_indicator
                            NotificationIconStyle.SPEEDOMETER -> R.drawable.ic_speed_indicator
                            NotificationIconStyle.ARROWS -> R.drawable.ic_notif_arrows
                            NotificationIconStyle.SIGNAL -> R.drawable.ic_notif_signal
                            NotificationIconStyle.MINIMAL_DOT -> R.drawable.ic_notif_dot
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) colors.accentPrimary.copy(alpha = 0.2f) else colors.background)
                                .border(
                                    1.dp,
                                    if (isSelected) colors.accentPrimary else colors.cardBorder,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    onUpdateSettings { it.copy(notificationIconStyle = iconStyle) }
                                }
                                .padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (iconStyle == NotificationIconStyle.DYNAMIC_SPEED) {
                                Text(
                                    text = "2.4M",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) colors.accentGlow else colors.textSecondary
                                )
                            } else {
                                Icon(
                                    painter = painterResource(id = iconDrawableRes),
                                    contentDescription = iconStyle.label,
                                    tint = if (isSelected) colors.accentGlow else colors.textSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = when (iconStyle) {
                                    NotificationIconStyle.DYNAMIC_SPEED -> "Live"
                                    NotificationIconStyle.SPEEDOMETER -> "Gauge"
                                    NotificationIconStyle.ARROWS -> "Arrows"
                                    NotificationIconStyle.SIGNAL -> "Signal"
                                    NotificationIconStyle.MINIMAL_DOT -> "Dot"
                                },
                                fontSize = 9.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) colors.accentGlow else colors.textSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = colors.cardBorder, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(8.dp))

                // Status Bar Icon Scale — applies to ALL icon styles (Live / Gauge / Arrows / Signal / Dot)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Status Bar Icon Size",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    InfoTooltip(description = "Controls the visible size of the notification icon in the status bar — applies to ALL icon styles (Live / Gauge / Arrows / Signal / Dot). Small blends in; Large is easier to read at a glance.")
                }
                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    NotificationIconScale.entries.forEach { scale ->
                        val isSelected = settings.notificationIconScale == scale
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) colors.accentPrimary.copy(alpha = 0.2f) else colors.background)
                                .border(
                                    1.dp,
                                    if (isSelected) colors.accentPrimary else colors.cardBorder,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    onUpdateSettings { it.copy(notificationIconScale = scale) }
                                }
                                .padding(horizontal = 4.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Visual preview: a small circle whose size reflects the scale
                            Box(
                                modifier = Modifier
                                    .size(
                                        when (scale) {
                                            NotificationIconScale.SMALL -> 10.dp
                                            NotificationIconScale.NORMAL -> 14.dp
                                            NotificationIconScale.LARGE -> 18.dp
                                        }
                                    )
                                    .clip(CircleShape)
                                    .background(if (isSelected) colors.accentGlow else colors.textSecondary)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = scale.label,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) colors.accentGlow else colors.textSecondary
                            )
                        }
                    }
                }

            }
        }

        // 5. Notification Layout — content & arrangement of the speed notification
        SettingsSection(
            icon = Icons.Default.SpaceDashboard,
            title = "Notification Layout"
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surfaceHighlight)
                    .border(1.dp, colors.cardBorder, RoundedCornerShape(12.dp))
                    .padding(10.dp)
            ) {
                // Status Bar Chip Size — Compact / Standard / Detailed (custom size for stuck indicator)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Status Bar Chip Size",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    InfoTooltip(description = "Controls the width of the promoted speed pill in the Android 16 status bar. The chip is capped at 96dp by the system: Compact shows only the speed value; Standard adds the direction arrow. Both are guaranteed fully readable — longer two-speed text gets clipped to just the icon.")
                }
                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    StatusBarChipSize.entries.forEach { size ->
                        val isSelected = settings.statusBarChipSize == size
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) colors.accentPrimary.copy(alpha = 0.2f) else colors.background)
                                .border(
                                    1.dp,
                                    if (isSelected) colors.accentPrimary else colors.cardBorder,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    onUpdateSettings { it.copy(statusBarChipSize = size) }
                                }
                                .padding(horizontal = 4.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Live preview of how the chip will look
                            Text(
                                text = when (size) {
                                    StatusBarChipSize.COMPACT -> "2.4M"
                                    StatusBarChipSize.STANDARD -> "↓2.4M"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) colors.accentGlow else colors.textSecondary,
                                maxLines = 1,
                                softWrap = false
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = size.label,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) colors.accentGlow else colors.textSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = colors.cardBorder, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(8.dp))

                // Detailed layout switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onUpdateSettings { it.copy(notificationDetailedLayout = !it.notificationDetailedLayout) }
                        },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Detailed Notification Layout",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        InfoTooltip(description = "Expands the notification to show session & today's data metrics.")
                    }
                    Switch(
                        checked = settings.notificationDetailedLayout,
                        onCheckedChange = { checked ->
                            onUpdateSettings { it.copy(notificationDetailedLayout = checked) }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.accentPrimary,
                            checkedTrackColor = colors.accentPrimary.copy(alpha = 0.3f),
                            uncheckedThumbColor = colors.textTertiary,
                            uncheckedTrackColor = colors.background
                        )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = colors.cardBorder, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(8.dp))

                // Display mode radio group
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Speed Display Format",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    InfoTooltip(description = "Choose which direction(s) to display in the speed notification & status bar chip.")
                }
                Spacer(modifier = Modifier.height(4.dp))

                DisplayMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onUpdateSettings { it.copy(displayMode = mode) }
                            }
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = settings.displayMode == mode,
                            onClick = { onUpdateSettings { it.copy(displayMode = mode) } },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = colors.accentPrimary,
                                unselectedColor = colors.textTertiary
                            ),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = mode.label,
                            fontSize = 12.sp,
                            color = if (settings.displayMode == mode) colors.textPrimary else colors.textSecondary,
                            fontWeight = if (settings.displayMode == mode) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        // 6. Polling Refresh Rate Interval
        SettingsSection(
            icon = Icons.Default.Timer,
            title = "Refresh Rate"
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surfaceHighlight)
                    .border(1.dp, colors.cardBorder, RoundedCornerShape(12.dp))
                    .padding(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Polling Interval",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        InfoTooltip(description = "How often traffic counters are sampled and the live notification refreshes. Tap the value to enter a custom interval (250–10000 ms).")
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.accentPrimary.copy(alpha = 0.15f))
                            .border(1.dp, colors.accentPrimary.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                            .clickable { showCustomIntervalDialog = true }
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit polling interval",
                                tint = colors.accentGlow,
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            val intervalSec = settings.updateIntervalMs / 1000f
                            Text(
                                text = "${settings.updateIntervalMs} ms (${String.format(Locale.US, "%.1f", intervalSec)}s)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.accentGlow
                            )
                        }
                    }
                }
            }
        }

        // 7. Network Diagnostic Polling (NetworkStateManager)
        SettingsSection(
            icon = Icons.Default.NetworkCheck,
            title = "Diagnostics"
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surfaceHighlight)
                    .border(1.dp, colors.cardBorder, RoundedCornerShape(12.dp))
                    .padding(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Diagnostic Refresh Rate",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        InfoTooltip(description = "Centralized cadence for ping, jitter, DNS, and interface health probes. Pauses during screen-off and Smart Battery Saver mode. Tap the value to set a custom interval, or use the Off switch to disable background diagnostics entirely (live speeds and the notification are unaffected).")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // OFF switch: 0 interval stops the ping/jitter probe loop.
                        Text(
                            text = "Off",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (settings.diagnosticPollingIntervalMs == 0L) EmeraldGlow else colors.textSecondary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Switch(
                            checked = settings.diagnosticPollingIntervalMs == 0L,
                            onCheckedChange = { turnOff ->
                                if (turnOff) {
                                    onUpdateSettings { it.copy(diagnosticPollingIntervalMs = 0L) }
                                    SettingsToggleFeedback.onDiagnosticsToggled(context, false)
                                } else {
                                    // Re-enable with the default cadence.
                                    onUpdateSettings { it.copy(diagnosticPollingIntervalMs = 4000L) }
                                    SettingsToggleFeedback.onDiagnosticsToggled(context, true)
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = EmeraldSuccess,
                                checkedTrackColor = EmeraldSuccess.copy(alpha = 0.3f),
                                uncheckedThumbColor = colors.textTertiary,
                                uncheckedTrackColor = colors.background
                            ),
                            modifier = Modifier.testTag("diagnostics_off_toggle")
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(CyanPrimary.copy(alpha = 0.15f))
                                .border(1.dp, CyanPrimary.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                .clickable(enabled = settings.diagnosticPollingIntervalMs != 0L) { showCustomDiagnosticIntervalDialog = true }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit diagnostic interval",
                                    tint = if (settings.diagnosticPollingIntervalMs != 0L) CyanGlow else colors.textTertiary,
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (settings.diagnosticPollingIntervalMs == 0L) "Disabled"
                                    else "${settings.diagnosticPollingIntervalMs}ms (${String.format(Locale.US, "%.1f", settings.diagnosticPollingIntervalMs / 1000f)}s)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (settings.diagnosticPollingIntervalMs != 0L) CyanGlow else colors.textTertiary
                                )
                            }
                        }
                    }
                }
            }
        }

        // 8. Stuck Detector & Low-Speed Throttling
        SettingsSection(
            icon = Icons.Default.Tune,
            title = "Stuck Detector"
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surfaceHighlight)
                    .border(1.dp, colors.cardBorder, RoundedCornerShape(12.dp))
                    .padding(10.dp)
            ) {
                // Master Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val next = !settings.isStuckDetectorEnabled
                            onUpdateSettings { it.copy(isStuckDetectorEnabled = next) }
                            SettingsToggleFeedback.onStuckDetectorToggled(context, next)
                        },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Stuck Detector Mode",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        InfoTooltip(description = "Throttles notification updates while speed is BELOW the threshold (or fully idle) to save battery & CPU, with instant interrupt when speed surges above it. Sampling and chart accuracy are unaffected.")
                    }
                    Switch(
                        checked = settings.isStuckDetectorEnabled,
                        onCheckedChange = { checked ->
                            onUpdateSettings { it.copy(isStuckDetectorEnabled = checked) }
                            SettingsToggleFeedback.onStuckDetectorToggled(context, checked)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.accentPrimary,
                            checkedTrackColor = colors.accentPrimary.copy(alpha = 0.3f),
                            uncheckedThumbColor = colors.textTertiary,
                            uncheckedTrackColor = colors.background
                        ),
                        modifier = Modifier.testTag("stuck_detector_toggle")
                    )
                }

                if (settings.isStuckDetectorEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = colors.cardBorder.copy(alpha = 0.6f), thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(8.dp))

                    // 1. Threshold Control
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Speed Threshold",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )
                        val thresholdDisplay = when {
                            settings.idleThresholdKbps == 0L -> "0 KB/s (throttle only at 0 B/s)"
                            settings.idleThresholdKbps >= 1024L && settings.idleThresholdKbps % 1024L == 0L ->
                                "${settings.idleThresholdKbps / 1024L} MB/s (${settings.idleThresholdKbps} KB/s)"
                            else -> "${settings.idleThresholdKbps} KB/s"
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (settings.idleThresholdKbps > 0) colors.accentPrimary.copy(alpha = 0.15f) else colors.background)
                                .border(1.dp, if (settings.idleThresholdKbps > 0) colors.accentPrimary.copy(alpha = 0.4f) else colors.cardBorder, RoundedCornerShape(6.dp))
                                .clickable { showCustomThresholdDialog = true }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = thresholdDisplay,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (settings.idleThresholdKbps > 0) colors.accentGlow else colors.textSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Slider(
                        value = idleThresholdDrag ?: settings.idleThresholdKbps.toFloat().coerceIn(0f, 5120f),
                        onValueChange = { newValue ->
                            idleThresholdDrag = newValue.coerceIn(0f, 5120f)
                        },
                        onValueChangeFinished = {
                            val dragged = idleThresholdDrag
                            idleThresholdDrag = null
                            if (dragged != null) {
                                val rounded = (Math.round(dragged / 50f) * 50).toLong().coerceIn(0L, 5120L)
                                onUpdateSettings { it.copy(idleThresholdKbps = rounded) }
                            }
                        },
                        valueRange = 0f..5120f,
                        steps = 101,
                        colors = SliderDefaults.colors(
                            thumbColor = colors.accentGlow,
                            activeTrackColor = colors.accentPrimary,
                            inactiveTrackColor = colors.background
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Freeze below threshold: zero notification posts while below
                    // the threshold — the ultimate battery setting for the
                    // notification layer (crossing the threshold resumes instantly).
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val next = !settings.isThresholdFreezeEnabled
                                onUpdateSettings { it.copy(isThresholdFreezeEnabled = next) }
                                SettingsToggleFeedback.onThresholdFreezeToggled(context, next)
                            },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Freeze Below Threshold",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (settings.idleThresholdKbps > 0L) colors.textPrimary else colors.textTertiary,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            InfoTooltip(
                                description = "While speed is below the threshold, the notification is not updated AT ALL (the last value stays visible). The moment speed crosses the threshold, updates resume instantly. Requires a non-zero threshold."
                            )
                        }
                        Switch(
                            checked = settings.isThresholdFreezeEnabled && settings.idleThresholdKbps > 0L,
                            enabled = settings.idleThresholdKbps > 0L,
                            onCheckedChange = { checked ->
                                onUpdateSettings { it.copy(isThresholdFreezeEnabled = checked) }
                                SettingsToggleFeedback.onThresholdFreezeToggled(context, checked)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.accentPrimary,
                                checkedTrackColor = colors.accentPrimary.copy(alpha = 0.3f),
                                uncheckedThumbColor = colors.textTertiary,
                                uncheckedTrackColor = colors.background
                            ),
                            modifier = Modifier.testTag("threshold_freeze_toggle")
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 2. Low-Speed Tick Interval Control
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Low-Speed Tick Interval",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            InfoTooltip(
                                description = "Notification updates every ${settings.stuckDetectorIntervalSec}s while speed is below threshold. When speed exceeds it, the notification instantly interrupts and switches to 1s with zero delay."
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(colors.accentPrimary.copy(alpha = 0.15f))
                                .border(1.dp, colors.accentPrimary.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                .clickable { showCustomStuckIntervalDialog = true }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "${settings.stuckDetectorIntervalSec}s",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.accentGlow
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Slider(
                        value = stuckIntervalDrag ?: settings.stuckDetectorIntervalSec.toFloat().coerceIn(2f, 30f),
                        onValueChange = { newValue ->
                            stuckIntervalDrag = newValue.coerceIn(2f, 30f)
                        },
                        onValueChangeFinished = {
                            val dragged = stuckIntervalDrag
                            stuckIntervalDrag = null
                            if (dragged != null) {
                                val rounded = Math.round(dragged).toInt().coerceIn(2, 30)
                                onUpdateSettings { it.copy(stuckDetectorIntervalSec = rounded) }
                            }
                        },
                        valueRange = 2f..30f,
                        steps = 27,
                        colors = SliderDefaults.colors(
                            thumbColor = colors.accentGlow,
                            activeTrackColor = colors.accentPrimary,
                            inactiveTrackColor = colors.background
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // (Instant Interrupt behavior is documented in the InfoTooltip above — no extra callout needed.)
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = colors.cardBorder, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val next = !settings.hideWhenIdle
                            onUpdateSettings { it.copy(hideWhenIdle = next) }
                            SettingsToggleFeedback.onHideWhenIdleToggled(context, next)
                        },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Hide Notification When Idle",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        InfoTooltip(description = "Hides the notification bar when total speed drops below the configured threshold.")
                    }
                    Switch(
                        checked = settings.hideWhenIdle,
                        onCheckedChange = { checked ->
                            onUpdateSettings { it.copy(hideWhenIdle = checked) }
                            SettingsToggleFeedback.onHideWhenIdleToggled(context, checked)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.accentPrimary,
                            checkedTrackColor = colors.accentPrimary.copy(alpha = 0.3f),
                            uncheckedThumbColor = colors.textTertiary,
                            uncheckedTrackColor = colors.background
                        )
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                HorizontalDivider(color = colors.cardBorder, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(6.dp))

                // Minimal (icon-only) notification — maximum battery efficiency.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val next = !settings.isMinimalNotificationEnabled
                            onUpdateSettings { it.copy(isMinimalNotificationEnabled = next) }
                            SettingsToggleFeedback.onMinimalNotificationToggled(context, next)
                        },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Minimal Notification (Icon Only)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        InfoTooltip(description = "Always render a bare foreground notification: only the status-bar icon remains, the shade entry is empty, and no text is ever rebuilt or re-posted per tick. Maximum battery efficiency for the notification layer.")
                    }
                    Switch(
                        checked = settings.isMinimalNotificationEnabled,
                        onCheckedChange = { checked ->
                            onUpdateSettings { it.copy(isMinimalNotificationEnabled = checked) }
                            SettingsToggleFeedback.onMinimalNotificationToggled(context, checked)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.accentPrimary,
                            checkedTrackColor = colors.accentPrimary.copy(alpha = 0.3f),
                            uncheckedThumbColor = colors.textTertiary,
                            uncheckedTrackColor = colors.background
                        ),
                        modifier = Modifier.testTag("minimal_notification_toggle")
                    )
                }
            }
        }

        // 9. Battery Saver & Power Management
        SettingsSection(
            icon = Icons.Default.BatterySaver,
            title = "Battery"
        ) {
            ToggleSettingRow(
                title = "Smart Battery Saver Mode",
                subtitle = "Keeps ONLY the core loop running: traffic speed counter + notification + in-app chart. Pauses ping diagnostics, watchdog, and daily summary. Your sampling interval is NOT changed.",
                checked = settings.isBatterySaverMode,
                testTag = "battery_saver_toggle_page",
                onCheckedChange = { checked ->
                    onUpdateSettings { it.copy(isBatterySaverMode = checked) }
                    SettingsToggleFeedback.onBatterySaverToggled(context, checked)
                }
            )

            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(color = colors.cardBorder, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(6.dp))

            ToggleSettingRow(
                title = "Screen-Off Deep Sleep (Auto-Pause)",
                subtitle = "Throttles traffic polling to 10s and suspends notification updates when the screen turns off",
                checked = settings.autoPauseOnScreenOff,
                testTag = "screen_off_pause_toggle_page",
                onCheckedChange = { checked ->
                    onUpdateSettings { it.copy(autoPauseOnScreenOff = checked) }
                    SettingsToggleFeedback.onScreenOffPauseToggled(context, checked)
                }
            )
        }

        // 10. Daily Data Usage Summary (alarm-driven recap notification)
        SettingsSection(
            icon = Icons.Default.Summarize,
            title = "Daily Summary"
        ) {
            ToggleSettingRow(
                title = "Daily Data Usage Recap",
                subtitle = "Posts a recap notification (Wi-Fi vs Mobile usage, 7-day stats) at your chosen time. Paused while Smart Battery Saver is on.",
                checked = settings.isDailySummaryEnabled,
                testTag = "daily_summary_toggle",
                onCheckedChange = { checked ->
                    onUpdateSettings { it.copy(isDailySummaryEnabled = checked) }
                    SettingsToggleFeedback.onDailySummaryToggled(context, checked)
                }
            )

            if (settings.isDailySummaryEnabled) {
                Spacer(modifier = Modifier.height(6.dp))
                HorizontalDivider(color = colors.cardBorder, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(6.dp))

                // Summary time picker entry
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recap Time",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        InfoTooltip(description = "The recap fires once a day at this time via a battery-friendly windowed alarm. Tap the value to change it.")
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(CyanPrimary.copy(alpha = 0.15f))
                            .border(1.dp, CyanPrimary.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                            .clickable { showDailySummaryTimeDialog = true }
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                            .testTag("daily_summary_time_value")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit recap time",
                                tint = CyanGlow,
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = String.format(
                                    Locale.US, "%02d:%02d",
                                    settings.dailySummaryHour.coerceIn(0, 23),
                                    settings.dailySummaryMinute.coerceIn(0, 59)
                                ),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyanGlow
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                HorizontalDivider(color = colors.cardBorder, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(6.dp))

                // Send-now preview button
                Button(
                    onClick = {
                        val posted = onPreviewDailySummary()
                        SettingsToggleFeedback.onDailySummaryPreview(context, posted)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanPrimary.copy(alpha = 0.2f),
                        contentColor = CyanGlow
                    ),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("daily_summary_preview_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Send Preview Recap Now", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // 10. WorkManager Watchdog & System Automation
        SettingsSection(
            icon = Icons.Default.PowerSettingsNew,
            title = "System & Watchdog"
        ) {
            ToggleSettingRow(
                title = "Start on Device Boot",
                subtitle = "Automatically launches network speed monitor when the device powers on",
                checked = settings.autoStartOnBoot,
                testTag = "boot_start_toggle_page",
                onCheckedChange = { checked ->
                    onUpdateSettings { it.copy(autoStartOnBoot = checked) }
                    SettingsToggleFeedback.onBootStartToggled(context, checked)
                }
            )

            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(color = colors.cardBorder, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(6.dp))

            // WorkManager Keep-Alive Status Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surfaceElevated)
                    .border(1.dp, CyanPrimary.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "WorkManager Watchdog",
                            tint = CyanGlow,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "WorkManager Watchdog",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        InfoTooltip(
                            description = if (settings.isBatterySaverMode)
                                "Disabled while Smart Battery Saver is ON — only core traffic counter & notification are active."
                            else
                                "Recovers from low-memory kills while respecting Android Doze & power limits."
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (settings.isServiceEnabled) EmeraldSuccess.copy(alpha = 0.15f) else colors.surfaceHighlight)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (settings.isServiceEnabled) "Active" else "Standby",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (settings.isServiceEnabled) EmeraldGlow else colors.textTertiary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = onTriggerWatchdogSync,
                    enabled = !settings.isBatterySaverMode,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanPrimary.copy(alpha = 0.2f),
                        contentColor = CyanGlow,
                        disabledContainerColor = colors.surfaceHighlight,
                        disabledContentColor = colors.textTertiary
                    ),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Trigger Watchdog Sync", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Battery Optimization Whitelist Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surfaceElevated)
                    .border(
                        1.dp,
                        if (isIgnoringBatteryOptimizations) EmeraldSuccess.copy(alpha = 0.3f) else AmberWarning.copy(alpha = 0.3f),
                        RoundedCornerShape(10.dp)
                    )
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isIgnoringBatteryOptimizations) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = "Battery Optimization",
                            tint = if (isIgnoringBatteryOptimizations) EmeraldGlow else AmberWarning,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Battery Optimization Status",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        InfoTooltip(
                            description = if (isIgnoringBatteryOptimizations)
                                "App is exempt from aggressive Android Doze kills. Continuous 24/7 speed monitoring is fully supported."
                            else
                                "Android may throttle or freeze background services during deep sleep. For uninterrupted 24/7 monitoring, whitelist the app."
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isIgnoringBatteryOptimizations) EmeraldSuccess.copy(alpha = 0.15f) else AmberWarning.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isIgnoringBatteryOptimizations) "Unrestricted" else "Optimized",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isIgnoringBatteryOptimizations) EmeraldGlow else AmberWarning
                        )
                    }
                }
                if (!isIgnoringBatteryOptimizations) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = onRequestIgnoreBatteryOptimizations,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AmberWarning.copy(alpha = 0.2f),
                            contentColor = AmberWarning
                        ),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Allow Unrestricted Background", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }

    // Custom Interval Dialog
    if (showCustomIntervalDialog) {
        var intervalInput by remember { mutableStateOf(settings.updateIntervalMs.toString()) }
        var isError by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showCustomIntervalDialog = false },
            containerColor = colors.surfaceElevated,
            title = {
                Text(
                    text = "Custom Update Interval",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter a polling interval in milliseconds (250ms – 10000ms):",
                        fontSize = 13.sp,
                        color = colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = intervalInput,
                        onValueChange = {
                            intervalInput = it
                            val parsed = it.toLongOrNull()
                            isError = parsed == null || parsed !in 250L..10000L
                        },
                        isError = isError,
                        label = { Text("Interval (ms)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.accentPrimary,
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
                        val parsed = intervalInput.toLongOrNull()
                        if (parsed != null && parsed in 250L..10000L) {
                            onUpdateSettings { it.copy(updateIntervalMs = parsed) }
                            showCustomIntervalDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentPrimary)
                ) {
                    Text("Apply", color = colors.background)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomIntervalDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }

    // Custom Diagnostic Polling Interval Dialog
    if (showCustomDiagnosticIntervalDialog) {
        var diagIntervalInput by remember {
            // Show the last ACTIVE interval when diagnostics are OFF (0), so the
            // edit dialog never starts from a misleading "0".
            mutableStateOf(
                if (settings.diagnosticPollingIntervalMs == 0L) 4000L.toString()
                else settings.diagnosticPollingIntervalMs.toString()
            )
        }
        var isError by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showCustomDiagnosticIntervalDialog = false },
            containerColor = colors.surfaceElevated,
            title = {
                Text(
                    text = "Custom Diagnostic Polling Interval",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter network diagnostic probe interval in milliseconds (500ms – 60000ms):",
                        fontSize = 13.sp,
                        color = colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = diagIntervalInput,
                        onValueChange = {
                            diagIntervalInput = it
                            val parsed = it.toLongOrNull()
                            isError = parsed == null || parsed !in 500L..60000L
                        },
                        isError = isError,
                        label = { Text("Probe Interval (ms)") },
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
                        val parsed = diagIntervalInput.toLongOrNull()
                        if (parsed != null && parsed in 500L..60000L) {
                            onUpdateSettings { it.copy(diagnosticPollingIntervalMs = parsed) }
                            showCustomDiagnosticIntervalDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary)
                ) {
                    Text("Apply", color = colors.background)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDiagnosticIntervalDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }

    // Custom Threshold Dialog
    if (showCustomThresholdDialog) {
        var thresholdInput by remember { mutableStateOf(settings.idleThresholdKbps.toString()) }
        var isError by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showCustomThresholdDialog = false },
            containerColor = colors.surfaceElevated,
            title = {
                Text(
                    text = "Custom Speed Threshold",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter speed threshold in KB/s (0 – 100000 KB/s):\n• E.g. 1024 = 1 MB/s, 2048 = 2 MB/s",
                        fontSize = 13.sp,
                        color = colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = thresholdInput,
                        onValueChange = {
                            thresholdInput = it
                            val parsed = it.toLongOrNull()
                            isError = parsed == null || parsed !in 0L..100000L
                        },
                        isError = isError,
                        label = { Text("Threshold (KB/s)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.accentPrimary,
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
                        val parsed = thresholdInput.toLongOrNull()
                        if (parsed != null && parsed in 0L..100000L) {
                            onUpdateSettings { it.copy(idleThresholdKbps = parsed) }
                            showCustomThresholdDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentPrimary)
                ) {
                    Text("Apply", color = colors.background)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomThresholdDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }

    // Custom Stuck Detector Low-Speed Interval Dialog
    if (showCustomStuckIntervalDialog) {
        var intervalInput by remember { mutableStateOf(settings.stuckDetectorIntervalSec.toString()) }
        var isError by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showCustomStuckIntervalDialog = false },
            containerColor = colors.surfaceElevated,
            title = {
                Text(
                    text = "Custom Low-Speed Interval",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter notification tick interval in seconds (2 – 60s) for speeds below threshold:",
                        fontSize = 13.sp,
                        color = colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = intervalInput,
                        onValueChange = {
                            intervalInput = it
                            val parsed = it.toIntOrNull()
                            isError = parsed == null || parsed !in 2..60
                        },
                        isError = isError,
                        label = { Text("Interval (seconds)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.accentPrimary,
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
                        val parsed = intervalInput.toIntOrNull()
                        if (parsed != null && parsed in 2..60) {
                            onUpdateSettings { it.copy(stuckDetectorIntervalSec = parsed) }
                            showCustomStuckIntervalDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentPrimary)
                ) {
                    Text("Apply", color = colors.background)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomStuckIntervalDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }

    // Daily Summary time picker (hour:minute, 24h)
    if (showDailySummaryTimeDialog) {
        var hourInput by remember {
            mutableStateOf(settings.dailySummaryHour.coerceIn(0, 23).toString())
        }
        var minuteInput by remember {
            mutableStateOf(settings.dailySummaryMinute.coerceIn(0, 59).toString())
        }
        var isError by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showDailySummaryTimeDialog = false },
            containerColor = colors.surfaceElevated,
            title = {
                Text(
                    text = "Daily Recap Time",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            },
            text = {
                Column {
                    Text(
                        text = "Post the daily data-usage recap at this time (24-hour clock). The alarm re-arms itself for the next day after each post.",
                        fontSize = 13.sp,
                        color = colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = hourInput,
                            onValueChange = {
                                hourInput = it
                                val h = it.toIntOrNull()
                                val m = minuteInput.toIntOrNull()
                                isError = h == null || m == null || h !in 0..23 || m !in 0..59
                            },
                            isError = isError,
                            label = { Text("Hour (0-23)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.accentPrimary,
                                unfocusedBorderColor = colors.cardBorder,
                                focusedTextColor = colors.textPrimary,
                                unfocusedTextColor = colors.textPrimary
                            )
                        )
                        OutlinedTextField(
                            value = minuteInput,
                            onValueChange = {
                                minuteInput = it
                                val h = hourInput.toIntOrNull()
                                val m = it.toIntOrNull()
                                isError = h == null || m == null || h !in 0..23 || m !in 0..59
                            },
                            isError = isError,
                            label = { Text("Minute (0-59)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.accentPrimary,
                                unfocusedBorderColor = colors.cardBorder,
                                focusedTextColor = colors.textPrimary,
                                unfocusedTextColor = colors.textPrimary
                            )
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val h = hourInput.toIntOrNull()
                        val m = minuteInput.toIntOrNull()
                        if (h != null && m != null && h in 0..23 && m in 0..59) {
                            onSetDailySummaryTime(h, m)
                            showDailySummaryTimeDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentPrimary)
                ) {
                    Text("Apply", color = colors.background)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDailySummaryTimeDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }
}

@Composable
private fun SettingsSection(
    icon: ImageVector,
    title: String,
    initiallyExpanded: Boolean = false,
    content: @Composable () -> Unit
) {
    val colors = AppTheme.colors
    var expanded by remember { mutableStateOf(initiallyExpanded) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(durationMillis = 180))
    ) {
        // Header — always visible, tap to toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = !expanded }
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.accentPrimary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.accentGlow,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowRight,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = colors.textSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
        if (expanded) {
            Spacer(modifier = Modifier.height(4.dp))
            content()
        }
    }
}

@Composable
private fun ToggleSettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceHighlight)
            .border(1.dp, colors.cardBorder, RoundedCornerShape(12.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(modifier = Modifier.width(6.dp))
            InfoTooltip(description = subtitle)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.accentPrimary,
                checkedTrackColor = colors.accentPrimary.copy(alpha = 0.3f),
                uncheckedThumbColor = colors.textTertiary,
                uncheckedTrackColor = colors.background
            ),
            modifier = Modifier.testTag(testTag)
        )
    }
}

/**
 * Small ⓘ info icon that shows a popup tooltip with the description text when tapped.
 * Tapping again (or tapping outside the popup) dismisses it.
 *
 * Pass `description = null` or blank to render nothing — handy when callers want to
 * conditionally show the tooltip only for options that actually have a description.
 */
@Composable
private fun InfoTooltip(
    description: String?,
    modifier: Modifier = Modifier
) {
    if (description.isNullOrBlank()) return
    val colors = AppTheme.colors
    var showPopup by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(colors.accentPrimary.copy(alpha = 0.15f))
                .clickable { showPopup = !showPopup },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "More info",
                tint = colors.accentGlow,
                modifier = Modifier.size(12.dp)
            )
        }

        if (showPopup) {
            Popup(
                alignment = Alignment.TopStart,
                onDismissRequest = { showPopup = false },
                properties = PopupProperties(focusable = true)
            ) {
                Box(
                    modifier = Modifier
                        .widthIn(max = 240.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.surfaceElevated)
                        .border(1.dp, colors.cardBorder, RoundedCornerShape(8.dp))
                        .padding(8.dp)
                        .clickable { showPopup = false }
                ) {
                    Text(
                        text = description,
                        fontSize = 10.5.sp,
                        color = colors.textSecondary,
                        lineHeight = 13.sp
                    )
                }
            }
        }
    }
}
