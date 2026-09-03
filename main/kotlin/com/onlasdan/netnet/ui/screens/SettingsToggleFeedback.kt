package com.onlasdan.netnet.ui.screens

import android.app.NotificationManager
import android.content.Context
import android.widget.Toast
import com.onlasdan.netnet.data.SpeedSettingsRepository
import com.onlasdan.netnet.notification.NotificationHelper
import com.onlasdan.netnet.service.NetSpeedForegroundService

/**
 * Verification-aware toast feedback for Settings toggles.
 *
 * Every toggle that depends on system state (notification permission, the
 * Android 16 promoted-notification capability, the running foreground service,
 * or other dependent settings) announces the status that is ACTUALLY applied
 * in the system right after the switch flips — not merely the new position of
 * the UI toggle. A toggle that cannot take effect reports
 * "Failed to enable — <reason>" instead of a false "Enabled".
 *
 * Pure local preferences that always apply instantly (e.g. the detailed-layout
 * display toggle) intentionally stay silent to avoid toast noise.
 *
 * All checks are fast, synchronous system queries:
 *  - NotificationManager.areNotificationsEnabled()  (app-level POST_NOTIFICATIONS)
 *  - NotificationHelper.canPostPromotedNotifications() (Android 16 promoted setting)
 *  - NetSpeedForegroundService.isRunning (foreground service alive right now)
 *  - SpeedSettingsRepository (persisted settings — read AFTER the update applied)
 */
internal object SettingsToggleFeedback {

    private fun toast(context: Context, message: String) {
        try {
            Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
        } catch (_: Throwable) {
        }
    }

    /** Whether the foreground speed-monitoring service is alive right now. */
    private fun isMonitoringRunning(): Boolean = try {
        NetSpeedForegroundService.isRunning.value
    } catch (_: Throwable) {
        false
    }

    /** Whether the app may post notifications at all (POST_NOTIFICATIONS granted). */
    private fun areNotificationsEnabled(context: Context): Boolean = try {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.areNotificationsEnabled() ?: false
    } catch (_: Throwable) {
        false
    }

    /** Whether Android 16 allows this app to post promoted ongoing notifications. */
    private fun canPostPromoted(context: Context): Boolean = try {
        NotificationHelper.canPostPromotedNotifications(context)
    } catch (_: Throwable) {
        false
    }

    /** Persisted settings — call AFTER onUpdateSettings so the new value is visible. */
    private fun currentSettings(context: Context) = try {
        SpeedSettingsRepository.getInstance(context).settings.value
    } catch (_: Throwable) {
        null
    }

    /**
     * Status Bar Live Speed Chip (Android 16 promoted notification).
     * Depends on: notification permission, promoted-notification capability,
     * and a running monitoring service to actually post the chip.
     */
    fun onStatusBarChipToggled(context: Context, enabled: Boolean) {
        if (!enabled) {
            toast(context, "Status Bar Live Speed Chip: Disabled")
            return
        }
        when {
            !areNotificationsEnabled(context) ->
                toast(context, "Failed to enable — notifications are blocked for this app")
            !canPostPromoted(context) ->
                toast(context, "Failed to enable — Promoted notifications are off in system notification settings")
            !isMonitoringRunning() ->
                toast(context, "Status Bar Live Speed Chip: Enabled — appears once monitoring is running")
            else ->
                toast(context, "Status Bar Live Speed Chip: Enabled")
        }
    }

    /**
     * Stuck Detector Mode — applied live by the running foreground service's
     * notification update loop; takes effect on next service start otherwise.
     */
    fun onStuckDetectorToggled(context: Context, enabled: Boolean) {
        if (!enabled) {
            toast(context, "Stuck Detector Mode: Disabled")
            return
        }
        if (isMonitoringRunning()) {
            toast(context, "Stuck Detector Mode: Enabled")
        } else {
            toast(context, "Stuck Detector Mode: Enabled — takes effect when monitoring starts")
        }
    }

    /**
     * Hide Notification When Idle — only actually hides when an idle threshold
     * (> 0 KB/s, configured under Stuck Detector) exists, notifications are
     * allowed, and the monitoring service is running.
     */
    fun onHideWhenIdleToggled(context: Context, enabled: Boolean) {
        if (!enabled) {
            toast(context, "Hide Notification When Idle: Disabled")
            return
        }
        val thresholdKbps = currentSettings(context)?.idleThresholdKbps ?: 0L
        when {
            thresholdKbps <= 0L ->
                toast(context, "Failed to enable — Speed Threshold is 0 KB/s, so nothing counts as idle")
            !areNotificationsEnabled(context) ->
                toast(context, "Failed to enable — notifications are blocked for this app")
            !isMonitoringRunning() ->
                toast(context, "Hide Notification When Idle: Enabled — takes effect when monitoring starts")
            else ->
                toast(context, "Hide Notification When Idle: Enabled — hides below $thresholdKbps KB/s")
        }
    }

    /**
     * Smart Battery Saver Mode — the running service cancels (or re-arms) the
     * watchdog alarm and daily summary in direct response to this toggle.
     */
    fun onBatterySaverToggled(context: Context, enabled: Boolean) {
        if (!enabled) {
            if (isMonitoringRunning()) {
                toast(context, "Smart Battery Saver Mode: Disabled — watchdog & daily summary rescheduled")
            } else {
                toast(context, "Smart Battery Saver Mode: Disabled")
            }
            return
        }
        if (isMonitoringRunning()) {
            toast(context, "Smart Battery Saver Mode: Enabled — watchdog & daily summary cancelled")
        } else {
            toast(context, "Smart Battery Saver Mode: Enabled — background tasks stop when monitoring starts")
        }
    }

    /** Screen-Off Deep Sleep — enforced by the service's screen-off receiver. */
    fun onScreenOffPauseToggled(context: Context, enabled: Boolean) {
        if (!enabled) {
            toast(context, "Screen-Off Deep Sleep: Disabled")
            return
        }
        if (isMonitoringRunning()) {
            toast(context, "Screen-Off Deep Sleep: Enabled")
        } else {
            toast(context, "Screen-Off Deep Sleep: Enabled — takes effect when monitoring starts")
        }
    }

    /**
     * Background Diagnostics (ping/jitter probe loop) — interval 0 = OFF.
     * Core speed counting and the live notification are never affected.
     */
    fun onDiagnosticsToggled(context: Context, enabled: Boolean) {
        if (enabled) {
            toast(context, "Network Diagnostics: Enabled — ping probes resume")
        } else {
            toast(context, "Network Diagnostics: OFF — live speed & notification unaffected")
        }
    }

    /** Freeze Below Threshold — zero notification posts below the threshold. */
    fun onThresholdFreezeToggled(context: Context, enabled: Boolean) {
        if (enabled) {
            toast(context, "Freeze Below Threshold: ON — no updates until speed crosses the threshold")
        } else {
            toast(context, "Freeze Below Threshold: OFF — idle updates at the tick interval")
        }
    }

    /** Minimal (icon-only) notification — the most battery-efficient rendering. */
    fun onMinimalNotificationToggled(context: Context, enabled: Boolean) {
        if (enabled) {
            toast(context, "Minimal Notification: ON — status-bar icon only, no shade text")
        } else {
            toast(context, "Minimal Notification: OFF — full notification restored")
        }
    }

    /**
     * Start on Device Boot — BootReceiver is always registered (permission is
     * granted at install); boot auto-start additionally requires the
     * monitoring-service preference to be ON.
     */
    fun onBootStartToggled(context: Context, enabled: Boolean) {
        if (!enabled) {
            toast(context, "Start on Device Boot: Disabled")
            return
        }
        val serviceEnabled = currentSettings(context)?.isServiceEnabled ?: true
        if (serviceEnabled) {
            toast(context, "Start on Device Boot: Enabled")
        } else {
            toast(context, "Start on Device Boot: Enabled — monitoring service is currently stopped")
        }
    }

    /** Daily data-usage recap — alarm-driven summary notification. */
    fun onDailySummaryToggled(context: Context, enabled: Boolean) {
        if (enabled) {
            toast(context, "Daily Recap: Enabled — scheduled at your chosen time")
        } else {
            toast(context, "Daily Recap: Disabled — no more summary notifications")
        }
    }

    /** Send-now preview of the daily recap notification. */
    fun onDailySummaryPreview(context: Context, posted: Boolean) {
        if (posted) {
            toast(context, "Preview recap posted — check your notifications")
        } else {
            toast(context, "Preview skipped — today's usage is below the minimum threshold")
        }
    }
}
