package com.onlasdan.netnet.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.onlasdan.netnet.MainActivity
import com.onlasdan.netnet.R
import com.onlasdan.netnet.data.SpeedSettings
import com.onlasdan.netnet.data.SpeedSettingsRepository
import com.onlasdan.netnet.model.DisplayMode
import com.onlasdan.netnet.model.NotificationIconScale
import com.onlasdan.netnet.model.SpeedFormatter
import com.onlasdan.netnet.model.SpeedSnapshot
import com.onlasdan.netnet.model.StatusBarChipSize
import com.onlasdan.netnet.model.UsageAnalyticsSummary
import com.onlasdan.netnet.service.NetSpeedForegroundService
import java.util.Locale

object NotificationHelper {
    private const val TAG = "NotificationHelper"
    const val CHANNEL_ID = "net_speed_indicator_channel"
    const val NOTIFICATION_ID = 1001
    const val DAILY_SUMMARY_CHANNEL_ID = "net_speed_daily_summary_channel"
    const val DAILY_SUMMARY_NOTIFICATION_ID = 2001
    const val EXTRA_NAVIGATE_TARGET = "extra_navigate_target"

    // Reflected Android 16+ (API 36) builders. Notification.Builder is final
    // on this compileSdk, so the methods cannot be referenced directly; cache
    // the Method objects once instead of re-running getMethod on EVERY speed
    // notification rebuild (the service rewrites the notification up to once
    // per second, and reflective getMethod on each tick is pure waste).
    private val setRequestPromotedOngoingMethod: java.lang.reflect.Method? =
        try {
            Notification.Builder::class.java.getMethod("setRequestPromotedOngoing", Boolean::class.javaPrimitiveType)
        } catch (_: Throwable) { null }

    private val setShortCriticalTextMethod: java.lang.reflect.Method? =
        try {
            Notification.Builder::class.java.getMethod("setShortCriticalText", CharSequence::class.java)
        } catch (_: Throwable) { null }

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                if (manager != null) {
                    val name = context.getString(R.string.notification_channel_name)
                    val descriptionText = context.getString(R.string.notification_channel_desc)
                    val importance = NotificationManager.IMPORTANCE_LOW
                    val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                        description = descriptionText
                        setShowBadge(false)
                        enableVibration(false)
                        enableLights(false)
                        setSound(null, null)
                    }
                    manager.createNotificationChannel(channel)

                    // Daily Data Usage Summary Alert Notification Channel
                    val dailyName = context.getString(R.string.daily_summary_channel_name)
                    val dailyDesc = context.getString(R.string.daily_summary_channel_desc)
                    val dailyChannel = NotificationChannel(
                        DAILY_SUMMARY_CHANNEL_ID,
                        dailyName,
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        description = dailyDesc
                        setShowBadge(true)
                        enableVibration(true)
                        enableLights(true)
                    }
                    manager.createNotificationChannel(dailyChannel)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to create notification channel: ${e.message}")
            }
        }
    }

    /**
     * Checks if the app is currently allowed to post promoted notifications in Android 16 (API 36 / 36.1).
     * Evaluates NotificationManager.canPostPromotedNotifications() accounting for user settings.
     */
    fun canPostPromotedNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 36) return false
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return false
        return try {
            val method = manager.javaClass.getMethod("canPostPromotedNotifications")
            (method.invoke(manager) as? Boolean) ?: true
        } catch (_: Throwable) {
            true
        }
    }

    /**
     * Checks whether a built notification meets the technical criteria to be promoted by Android 16.
     * (e.g., ongoing=true, has contentTitle, valid style, no custom views, not colorized, not group summary).
     */
    fun hasPromotableCharacteristics(notification: Notification): Boolean {
        if (Build.VERSION.SDK_INT < 36) return false
        return try {
            val method = notification.javaClass.getMethod("hasPromotableCharacteristics")
            (method.invoke(notification) as? Boolean) ?: true
        } catch (_: Throwable) {
            true
        }
    }

    /**
     * Creates an intent to navigate directly to the Promoted Notifications system settings page.
     */
    fun getPromotedNotificationSettingsIntent(context: Context): Intent {
        return try {
            Intent("android.settings.MANAGE_APP_PROMOTED_NOTIFICATIONS").apply {
                data = Uri.fromParts("package", context.packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } catch (_: Throwable) {
            try {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            } catch (_: Throwable) {
                Intent(Settings.ACTION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            }
        }
    }

    fun buildSpeedNotification(
        context: Context,
        snapshot: SpeedSnapshot,
        settings: SpeedSettings,
        isPaused: Boolean = false
    ): Notification {
        val dlFormatted = SpeedFormatter.formatSpeed(snapshot.downloadBytesPerSec, settings.speedUnit)
        val ulFormatted = SpeedFormatter.formatSpeed(snapshot.uploadBytesPerSec, settings.speedUnit)

        val thresholdBytes = settings.idleThresholdKbps * 1024L
        val isBelowThreshold = settings.idleThresholdKbps > 0 && (snapshot.downloadBytesPerSec + snapshot.uploadBytesPerSec) < thresholdBytes

        // ============================================================
        // HIDE-WHEN-IDLE MODE: When the user has enabled "Hide
        // Notification When Idle" AND speed is currently below the
        // configured threshold, we render a MINIMAL notification that
        // still satisfies Android's foreground service requirement
        // (smallIcon + ongoing) but visually hides the speed content.
        //
        // The minimal notification:
        //   - Uses the static vector smallIcon (no dynamic speed text)
        //   - Has empty title/content/subText
        //   - No BigTextStyle, no actions, no promoted chip
        //   - Ongoing=true (required to keep foreground service alive)
        // This effectively "hides" the notification: the user only sees
        // a tiny icon in the status bar; the shade entry is blank.
        // ============================================================
        if (!isPaused && isBelowThreshold && settings.hideWhenIdle) {
            return buildHiddenIdleNotification(context, settings)
        }

        // ALWAYS-MINIMAL MODE: the user chose to keep only the status-bar icon
        // (see Settings > Notification). Same foreground-service-compliant
        // minimal rendering as hide-when-idle, but unconditional — zero text
        // relayouts and zero promoted-chip churn on every tick.
        if (!isPaused && settings.isMinimalNotificationEnabled) {
            return buildHiddenIdleNotification(context, settings)
        }

        // Build the status bar chip ("stuck indicator") text.
        // The user-selectable StatusBarChipSize controls how wide/compact the promoted pill appears in the status bar.
        val chipText = when {
            isPaused -> "Paused"
            isBelowThreshold && settings.hideWhenIdle -> ""
            else -> buildChipText(snapshot, settings)
        }

        // Open App Intent
        val appIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingAppIntent = PendingIntent.getActivity(
            context,
            0,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Pause / Resume
        val toggleActionIntent = Intent(context, NetSpeedForegroundService::class.java).apply {
            action = if (isPaused) NetSpeedForegroundService.ACTION_RESUME else NetSpeedForegroundService.ACTION_PAUSE
        }
        val pendingToggleIntent = PendingIntent.getService(
            context,
            1,
            toggleActionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val toggleActionTitle = if (isPaused) "Resume" else "Pause"

        // Action: Reset Session
        val resetActionIntent = Intent(context, NetSpeedForegroundService::class.java).apply {
            action = NetSpeedForegroundService.ACTION_RESET_SESSION
        }
        val pendingResetIntent = PendingIntent.getService(
            context,
            2,
            resetActionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Note: thresholdDisplay intentionally removed — the idle title now shows
        // the plain zero-speed reading (short, readable in the collapsed shade)
        // instead of the long "Network Idle (< ... KB/s)" string that risked
        // ellipsizing on narrow screens.

        val title = when {
            isPaused -> "Network Monitor Paused"
            // Below the threshold / fully idle the speeds read as 0 anyway — a
            // long "Network Idle (< 200 KB/s)" line just gets ellipsized in the
            // collapsed shade (single-line title, ~40 visible chars). Show the
            // plain zero-speed form; the status bar chip already shows the 0.
            isBelowThreshold && (snapshot.downloadBytesPerSec > 0L || snapshot.uploadBytesPerSec > 0L) ->
                when (settings.displayMode) {
                    DisplayMode.BOTH -> "↓ $dlFormatted   •   ↑ $ulFormatted"
                    DisplayMode.DOWNLOAD_ONLY -> "↓ $dlFormatted"
                    DisplayMode.UPLOAD_ONLY -> "↑ $ulFormatted"
                    DisplayMode.AUTO_HIGHEST ->
                        if (snapshot.uploadBytesPerSec > snapshot.downloadBytesPerSec) "↑ $ulFormatted" else "↓ $dlFormatted"
                }
            isBelowThreshold -> when (settings.displayMode) {
                DisplayMode.DOWNLOAD_ONLY -> "↓ 0.0 KB/s"
                DisplayMode.UPLOAD_ONLY -> "↑ 0.0 KB/s"
                else -> "↓ 0.0 KB/s   •   ↑ 0.0 KB/s"
            }
            else -> when (settings.displayMode) {
                DisplayMode.BOTH -> "↓ $dlFormatted   •   ↑ $ulFormatted"
                DisplayMode.DOWNLOAD_ONLY -> "↓ Download: $dlFormatted"
                DisplayMode.UPLOAD_ONLY -> "↑ Upload: $ulFormatted"
                DisplayMode.AUTO_HIGHEST -> {
                    if (snapshot.uploadBytesPerSec > snapshot.downloadBytesPerSec) {
                        "↑ Upload: $ulFormatted"
                    } else {
                        "↓ Download: $dlFormatted"
                    }
                }
            }
        }

        val sessionFormatted = SpeedFormatter.formatDataSize(snapshot.sessionTotalBytes)
        val todayFormatted = SpeedFormatter.formatDataSize(snapshot.todayTotalBytes)
        val subtitle = "Session: $sessionFormatted  |  Today: $todayFormatted  •  ${snapshot.networkName}"

        val builder = Notification.Builder(context, CHANNEL_ID)

        try {
            // When Promoted Notification (showStatusBarChip) is enabled, Android 16 places smallIcon directly inside
            // the status bar chip next to shortCriticalText. If smallIcon is DYNAMIC_SPEED, it renders duplicate speed text
            // in the chip (e.g. "[1.2M] ↓1.2M"). To prevent double indicator in promoted notifications, we use a clean vector
            // icon for smallIcon when showStatusBarChip is true, and dynamic speed icon when promoted chip is disabled.
            val useDynamicIcon = settings.notificationIconStyle == com.onlasdan.netnet.model.NotificationIconStyle.DYNAMIC_SPEED && !settings.showStatusBarChip
            if (useDynamicIcon) {
                val speedIcon = DynamicSpeedIconRenderer.createSpeedIcon(
                    snapshot = snapshot,
                    displayMode = settings.displayMode,
                    speedUnit = settings.speedUnit,
                    colorTheme = settings.notificationColorTheme,
                    isPaused = isPaused,
                    isIdle = isBelowThreshold,
                    iconScale = settings.notificationIconScale
                )
                builder.setSmallIcon(speedIcon)
            } else {
                val iconRes = when (settings.notificationIconStyle) {
                    com.onlasdan.netnet.model.NotificationIconStyle.SPEEDOMETER -> R.drawable.ic_speed_indicator
                    com.onlasdan.netnet.model.NotificationIconStyle.ARROWS -> R.drawable.ic_notif_arrows
                    com.onlasdan.netnet.model.NotificationIconStyle.SIGNAL -> R.drawable.ic_notif_signal
                    com.onlasdan.netnet.model.NotificationIconStyle.MINIMAL_DOT -> R.drawable.ic_notif_dot
                    com.onlasdan.netnet.model.NotificationIconStyle.DYNAMIC_SPEED -> R.drawable.ic_speed_indicator
                }
                // For non-dynamic icons, apply the user-selected scale by rendering the
                // vector drawable into a scaled bitmap. The system would otherwise render
                // the smallIcon at its intrinsic size, ignoring NotificationIconScale.
                val scaledIcon = createScaledIcon(context, iconRes, settings.notificationIconScale)
                if (scaledIcon != null) {
                    builder.setSmallIcon(scaledIcon)
                } else {
                    builder.setSmallIcon(iconRes)
                }
            }
        } catch (_: Throwable) {
            builder.setSmallIcon(R.drawable.ic_speed_indicator)
        }

        builder
            .setContentTitle(title)
            .setContentText(subtitle)
            .setSubText(snapshot.networkType.title)
            .setColor(settings.notificationColorTheme.colorInt)
            // CRITICAL (Android 16 Official Requirement): Promoted Ongoing notifications / Live Updates
            // MUST NOT be colorized (setColorized(true) strictly disqualifies the notification from being promoted).
            .setColorized(false)
            .setContentIntent(pendingAppIntent)
            .setOngoing(!isPaused)
            .setGroupSummary(false)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_STATUS)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }

        val actionIcon = android.graphics.drawable.Icon.createWithResource(context, R.drawable.ic_speed_indicator)
        builder
            .addAction(
                Notification.Action.Builder(actionIcon, toggleActionTitle, pendingToggleIntent).build()
            )
            .addAction(
                Notification.Action.Builder(actionIcon, "Reset Session", pendingResetIntent).build()
            )

        if (settings.notificationDetailedLayout) {
            builder.setStyle(
                Notification.BigTextStyle()
                    .setBigContentTitle(title)
                    .bigText(
                        "Download: $dlFormatted\n" +
                        "Upload: $ulFormatted\n" +
                        "Session Usage: $sessionFormatted\n" +
                        "Today's Usage: $todayFormatted\n" +
                        "Network: ${snapshot.networkName} (${snapshot.networkType.title}) • IP: ${snapshot.ipAddress}"
                    )
            )
        }

        // Android 16 (API 36 / 36.1) Live Updates / Promoted Ongoing Notification APIs
        if (Build.VERSION.SDK_INT >= 36) {
            try {
                // Set Promoted Ongoing: builder.setRequestPromotedOngoing(boolean)
                setRequestPromotedOngoingMethod?.invoke(builder, settings.showStatusBarChip)

                // Set Status Bar Glanceable Chip: builder.setShortCriticalText(CharSequence?)
                if (settings.showStatusBarChip && chipText.isNotEmpty()) {
                    setShortCriticalTextMethod?.invoke(builder, chipText)
                } else {
                    setShortCriticalTextMethod?.invoke(builder, null as CharSequence?)
                }
            } catch (e: Throwable) {
                Log.d(TAG, "Promoted notification API invocation fallback: ${e.message}")
            }
        }

        val notification = try {
            builder.build()
        } catch (_: Throwable) {
            Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_speed_indicator)
                .setContentTitle(title)
                .setContentText(subtitle)
                .setOngoing(!isPaused)
                .build()
        }

        if (Build.VERSION.SDK_INT >= 36) {
            try {
                val isEligible = hasPromotableCharacteristics(notification)
                Log.d(TAG, "Notification promotable characteristics check: $isEligible")
            } catch (_: Throwable) {}
        }

        return notification
    }

    /**
     * Builds the Daily Data Usage Summary alert notification from analytics data.
     */
    fun buildDailyUsageSummaryNotification(
        context: Context,
        analytics: UsageAnalyticsSummary,
        settings: SpeedSettings
    ): Notification {
        val todayRecord = analytics.records.firstOrNull()
        val wifiBytes = todayRecord?.wifiBytes ?: 0L
        val cellBytes = todayRecord?.cellBytes ?: 0L
        val otherBytes = todayRecord?.otherBytes ?: 0L
        val todayTotalBytes = wifiBytes + cellBytes + otherBytes

        val todayFormatted = SpeedFormatter.formatDataSize(todayTotalBytes)
        val wifiFormatted = SpeedFormatter.formatDataSize(wifiBytes)
        val cellFormatted = SpeedFormatter.formatDataSize(cellBytes)
        val avgFormatted = SpeedFormatter.formatDataSize(analytics.dailyAverageBytes)

        val title = "Daily Data Summary: $todayFormatted"
        val subtitle = "📶 Wi-Fi: $wifiFormatted  •  📱 Mobile: $cellFormatted"

        // Open App Intent pointing to Data Usage screen
        val appIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NAVIGATE_TARGET, "data_usage")
        }
        val pendingAppIntent = PendingIntent.getActivity(
            context,
            200,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val wifiPercentStr = if (todayTotalBytes > 0) {
            String.format(Locale.getDefault(), "%.1f%%", (wifiBytes.toFloat() / todayTotalBytes) * 100f)
        } else "0%"

        val cellPercentStr = if (todayTotalBytes > 0) {
            String.format(Locale.getDefault(), "%.1f%%", (cellBytes.toFloat() / todayTotalBytes) * 100f)
        } else "0%"

        val expandedBody = buildString {
            appendLine("Today's Total Consumed: $todayFormatted")
            appendLine("• 📶 Wi-Fi: $wifiFormatted ($wifiPercentStr)")
            appendLine("• 📱 Mobile Data: $cellFormatted ($cellPercentStr)")
            if (analytics.dailyAverageBytes > 0L) {
                appendLine("• 📅 7-Day Daily Avg: $avgFormatted")
            }
            if (analytics.peakDayBytes > 0L) {
                appendLine("• 📈 7-Day Peak: ${SpeedFormatter.formatDataSize(analytics.peakDayBytes)} (${analytics.peakDayLabel})")
            }
        }.trimEnd()

        val builder = Notification.Builder(context, DAILY_SUMMARY_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_speed_indicator)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setSubText("Daily Recap")
            .setColor(settings.notificationColorTheme.colorInt)
            .setColorized(false)
            .setContentIntent(pendingAppIntent)
            .setAutoCancel(true)
            .setOngoing(false)
            .setShowWhen(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .setStyle(
                Notification.BigTextStyle()
                    .setBigContentTitle("📊 $title")
                    .setSummaryText("Network Analytics")
                    .bigText(expandedBody)
            )

        val viewActionIcon = android.graphics.drawable.Icon.createWithResource(context, R.drawable.ic_speed_indicator)
        builder.addAction(
            Notification.Action.Builder(viewActionIcon, "View Analytics", pendingAppIntent).build()
        )

        return try {
            builder.build()
        } catch (_: Throwable) {
            Notification.Builder(context, DAILY_SUMMARY_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_speed_indicator)
                .setContentTitle(title)
                .setContentText(subtitle)
                .setContentIntent(pendingAppIntent)
                .setAutoCancel(true)
                .build()
        }
    }

    /**
     * Immediately generates and posts the Daily Usage Summary Notification.
     * Returns true if posted successfully.
     */
    fun postDailyUsageSummaryNotification(context: Context): Boolean {
        return try {
            val repo = SpeedSettingsRepository.getInstance(context)
            val settings = repo.settings.value
            val analytics = repo.getHistoricalUsage(7)
            val todayRecord = analytics.records.firstOrNull()
            val totalTodayBytes = (todayRecord?.wifiBytes ?: 0L) + (todayRecord?.cellBytes ?: 0L) + (todayRecord?.otherBytes ?: 0L)

            val minThresholdBytes = settings.dailySummaryMinThresholdMb * 1024L * 1024L
            if (minThresholdBytes > 0L && totalTodayBytes < minThresholdBytes) {
                Log.d(TAG, "Daily summary skipped: today usage ($totalTodayBytes B) below threshold ($minThresholdBytes B)")
                return false
            }

            createNotificationChannel(context)
            val notification = buildDailyUsageSummaryNotification(context, analytics, settings)
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.notify(DAILY_SUMMARY_NOTIFICATION_ID, notification)
            Log.d(TAG, "Daily usage summary notification successfully posted ($totalTodayBytes bytes consumed)")
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to post daily usage summary notification", e)
            false
        }
    }

    /**
     * Renders a vector drawable resource into a bitmap scaled by [NotificationIconScale].
     *
     * Returns an [android.graphics.drawable.Icon] backed by the scaled bitmap, or `null`
     * if rendering fails. The caller is expected to fall back to the resource icon when
     * this returns null.
     *
     * The base canvas size is 96x96 (matches DynamicSpeedIconRenderer.ICON_SIZE) so the
     * SMALL / NORMAL / LARGE visual size is consistent between dynamic and static icons.
     */
    private fun createScaledIcon(
        context: Context,
        drawableRes: Int,
        scale: NotificationIconScale
    ): android.graphics.drawable.Icon? {
        if (scale == NotificationIconScale.NORMAL) return null // Use the resource directly for normal size
        return try {
            val drawable = androidx.core.content.ContextCompat.getDrawable(context, drawableRes) ?: return null
            val baseSize = 96
            val scaledSize = (baseSize * scale.scale).toInt().coerceAtLeast(24)
            val bitmap = android.graphics.Bitmap.createBitmap(scaledSize, scaledSize, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            drawable.setBounds(0, 0, scaledSize, scaledSize)
            drawable.draw(canvas)
            android.graphics.drawable.Icon.createWithBitmap(bitmap)
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Builds the Android 16 status bar chip text based on the user-selected [StatusBarChipSize].
     *
     * - COMPACT  → only the dominant speed value (e.g. "2.4M") → narrowest pill
     * - STANDARD → arrow + dominant speed (e.g. "↓2.4M")
     * - DETAILED → both DL & UL (e.g. "↓2.4M ↑0.5K") → widest pill (legacy default)
     *
     * The `displayMode` setting is honoured: DOWNLOAD_ONLY / UPLOAD_ONLY / AUTO_HIGHEST pick the
     * dominant direction accordingly; BOTH falls back to download as the dominant direction.
     */
    private fun buildChipText(snapshot: SpeedSnapshot, settings: SpeedSettings): String {
        val dlCompact = SpeedFormatter.formatCompactSpeed(snapshot.downloadBytesPerSec, settings.speedUnit)
        val ulCompact = SpeedFormatter.formatCompactSpeed(snapshot.uploadBytesPerSec, settings.speedUnit)

        // Determine the dominant direction (used for COMPACT / STANDARD sizes)
        val dominantIsUpload = when (settings.displayMode) {
            DisplayMode.UPLOAD_ONLY -> true
            DisplayMode.DOWNLOAD_ONLY -> false
            DisplayMode.AUTO_HIGHEST -> snapshot.uploadBytesPerSec > snapshot.downloadBytesPerSec
            DisplayMode.BOTH -> false
        }
        val dominantValue = if (dominantIsUpload) ulCompact else dlCompact
        val dominantArrow = if (dominantIsUpload) "↑" else "↓"

        // Official Android 16 status-chip contract (developer.android.com live
        // updates): the chip is capped at 96dp — text under 7 characters is
        // fully shown, and if less than half the text fits the chip shows ONLY
        // the icon. A two-speed "↓2.4M ↑0.5K" string is 9-11 chars and gets
        // clipped unreadably on narrow chips, so every chip size now stays in
        // the guaranteed-readable single-direction form.
        return when (settings.statusBarChipSize) {
            StatusBarChipSize.COMPACT -> dominantValue
            else -> "$dominantArrow$dominantValue"
        }
    }

    /**
     * Builds a MINIMAL notification used when "Hide Notification When Idle" is active
     * AND the current speed is below the user's threshold.
     *
     * The notification is technically still present (Android requires this for the
     * foreground service to stay alive) but visually hidden:
     *   - smallIcon: the selected static icon (or default speed indicator)
     *   - content title / text / subText: empty
     *   - no BigTextStyle, no actions, no promoted chip
     *   - ongoing=true (mandatory for foreground service)
     *   - priority set to LOW so it sorts to the bottom of the shade
     *
     * The user only sees a tiny icon in the status bar; the shade entry is blank.
     */
    private fun buildHiddenIdleNotification(
        context: Context,
        settings: SpeedSettings
    ): Notification {
        val iconRes = when (settings.notificationIconStyle) {
            com.onlasdan.netnet.model.NotificationIconStyle.SPEEDOMETER -> R.drawable.ic_speed_indicator
            com.onlasdan.netnet.model.NotificationIconStyle.ARROWS -> R.drawable.ic_notif_arrows
            com.onlasdan.netnet.model.NotificationIconStyle.SIGNAL -> R.drawable.ic_notif_signal
            com.onlasdan.netnet.model.NotificationIconStyle.MINIMAL_DOT -> R.drawable.ic_notif_dot
            com.onlasdan.netnet.model.NotificationIconStyle.DYNAMIC_SPEED -> R.drawable.ic_speed_indicator
        }

        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(iconRes)
            .setContentTitle("")
            .setContentText("")
            .setSubText(null as CharSequence?)
            .setColor(settings.notificationColorTheme.colorInt)
            .setColorized(false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_STATUS)
            .setPriority(Notification.PRIORITY_LOW)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }

        // Explicitly DISABLE promoted ongoing + chip so the speed pill does not
        // appear in the Android 16 status bar while we are in hide-when-idle mode.
        if (Build.VERSION.SDK_INT >= 36) {
            try {
                setRequestPromotedOngoingMethod?.invoke(builder, false)
                setShortCriticalTextMethod?.invoke(builder, null as CharSequence?)
            } catch (_: Throwable) {}
        }

        return try {
            builder.build()
        } catch (_: Throwable) {
            Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_speed_indicator)
                .setOngoing(true)
                .build()
        }
    }
}
