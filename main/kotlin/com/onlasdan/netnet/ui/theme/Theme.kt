package com.onlasdan.netnet.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.onlasdan.netnet.model.AppThemeMode

data class AppColors(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceHighlight: Color,
    val cardBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val isDark: Boolean,
    val isOled: Boolean,
    val gaugeTrack: Color,
    val gaugeInnerTrack: Color,
    val chartBg: Color,
    val chartGridLine: Color,
    val accentPrimary: Color,
    val accentGlow: Color,
    val accentSecondary: Color,
    val accentSecondaryGlow: Color,
    val accentSuccess: Color,
    val accentSuccessGlow: Color
)

val LightColors = AppColors(
    background = LightBackground,
    surface = LightSurface,
    surfaceElevated = LightSurfaceElevated,
    surfaceHighlight = LightSurfaceHighlight,
    cardBorder = LightCardBorder,
    textPrimary = LightTextPrimary,
    textSecondary = LightTextSecondary,
    textTertiary = LightTextTertiary,
    isDark = false,
    isOled = false,
    gaugeTrack = LightGaugeTrack,
    gaugeInnerTrack = LightGaugeInnerTrack,
    chartBg = LightChartBg,
    chartGridLine = LightChartGridLine,
    accentPrimary = LightCyanPrimary,
    accentGlow = LightCyanGlow,
    accentSecondary = LightPurpleAccent,
    accentSecondaryGlow = LightPurpleGlow,
    accentSuccess = LightEmeraldSuccess,
    accentSuccessGlow = LightEmeraldGlow
)

val DefaultColors = AppColors(
    background = DarkBackground,
    surface = DarkSurface,
    surfaceElevated = DarkSurfaceElevated,
    surfaceHighlight = DarkSurfaceHighlight,
    cardBorder = CardBorder,
    textPrimary = TextPrimary,
    textSecondary = TextSecondary,
    textTertiary = TextTertiary,
    isDark = true,
    isOled = false,
    gaugeTrack = Color(0xFF1E293B),
    gaugeInnerTrack = Color(0xFF161F30),
    chartBg = Color(0xFF0C101A),
    chartGridLine = Color(0x6622304A),
    accentPrimary = CyanPrimary,
    accentGlow = CyanGlow,
    accentSecondary = PurpleAccent,
    accentSecondaryGlow = PurpleGlow,
    accentSuccess = EmeraldSuccess,
    accentSuccessGlow = EmeraldGlow
)

val OledColors = AppColors(
    background = OledBackground,
    surface = OledSurface,
    surfaceElevated = OledSurfaceElevated,
    surfaceHighlight = OledSurfaceHighlight,
    cardBorder = OledCardBorder,
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFD4D4D8),
    textTertiary = Color(0xFFA1A1AA),
    isDark = true,
    isOled = true,
    gaugeTrack = Color(0xFF141416),
    gaugeInnerTrack = Color(0xFF0C0C0E),
    chartBg = Color(0xFF000000),
    chartGridLine = Color(0xFF262628),
    accentPrimary = CyanPrimary,
    accentGlow = CyanGlow,
    accentSecondary = PurpleAccent,
    accentSecondaryGlow = PurpleGlow,
    accentSuccess = EmeraldSuccess,
    accentSuccessGlow = EmeraldGlow
)

val PinkColors = AppColors(
    background = PinkBackground,
    surface = PinkSurface,
    surfaceElevated = PinkSurfaceElevated,
    surfaceHighlight = PinkSurfaceHighlight,
    cardBorder = PinkCardBorder,
    textPrimary = Color(0xFFFFF2FD),
    textSecondary = Color(0xFFF3E8FF),
    textTertiary = Color(0xFFD8B4FE),
    isDark = true,
    isOled = false,
    gaugeTrack = PinkGaugeTrack,
    gaugeInnerTrack = PinkGaugeInnerTrack,
    chartBg = PinkChartBg,
    chartGridLine = PinkChartGridLine,
    accentPrimary = PinkPrimary,
    accentGlow = PinkGlow,
    accentSecondary = PinkSecondary,
    accentSecondaryGlow = PinkSecondaryGlow,
    accentSuccess = PinkSuccess,
    accentSuccessGlow = PinkSuccessGlow
)

val LocalAppColors = staticCompositionLocalOf { DefaultColors }

object AppTheme {
    val colors: AppColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAppColors.current
}

@Composable
fun NetSpeedTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    isOled: Boolean = false,
    content: @Composable () -> Unit
) {
    val systemInDark = isSystemInDarkTheme()
    val effectiveMode = when (themeMode) {
        AppThemeMode.SYSTEM -> if (systemInDark) AppThemeMode.DARK else AppThemeMode.LIGHT
        AppThemeMode.LIGHT -> AppThemeMode.LIGHT
        AppThemeMode.DARK -> AppThemeMode.DARK
        AppThemeMode.OLED -> AppThemeMode.OLED
        AppThemeMode.PINK -> AppThemeMode.PINK
    }

    val appColors = when (effectiveMode) {
        AppThemeMode.LIGHT -> LightColors
        AppThemeMode.DARK -> DefaultColors
        AppThemeMode.OLED -> OledColors
        AppThemeMode.PINK -> PinkColors
        AppThemeMode.SYSTEM -> if (systemInDark) DefaultColors else LightColors
    }

    val colorScheme = if (appColors.isDark) {
        darkColorScheme(
            primary = appColors.accentPrimary,
            onPrimary = appColors.background,
            primaryContainer = appColors.surfaceHighlight,
            onPrimaryContainer = appColors.accentPrimary,
            secondary = appColors.accentSuccess,
            onSecondary = appColors.background,
            secondaryContainer = appColors.surfaceElevated,
            onSecondaryContainer = appColors.accentSuccessGlow,
            tertiary = appColors.accentSecondary,
            onTertiary = appColors.background,
            background = appColors.background,
            onBackground = appColors.textPrimary,
            surface = appColors.surface,
            onSurface = appColors.textPrimary,
            surfaceVariant = appColors.surfaceElevated,
            onSurfaceVariant = appColors.textSecondary,
            outline = appColors.cardBorder
        )
    } else {
        lightColorScheme(
            primary = appColors.accentPrimary,
            onPrimary = Color.White,
            primaryContainer = appColors.surfaceHighlight,
            onPrimaryContainer = appColors.accentPrimary,
            secondary = appColors.accentSuccess,
            onSecondary = Color.White,
            secondaryContainer = appColors.surfaceElevated,
            onSecondaryContainer = appColors.accentSuccessGlow,
            tertiary = appColors.accentSecondary,
            onTertiary = Color.White,
            background = appColors.background,
            onBackground = appColors.textPrimary,
            surface = appColors.surface,
            onSurface = appColors.textPrimary,
            surfaceVariant = appColors.surfaceElevated,
            onSurfaceVariant = appColors.textSecondary,
            outline = appColors.cardBorder
        )
    }

    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
