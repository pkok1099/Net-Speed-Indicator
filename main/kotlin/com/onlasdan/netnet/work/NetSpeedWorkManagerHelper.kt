package com.onlasdan.netnet.work

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

/**
 * AlarmManager-based replacement for the WorkManager helper.
 *
 * Why: androidx.work pulls in ~480 classes (Worker, CoroutineWorker, WorkManager,
 * WorkSpec, Constraints, ConstraintsImpl, WorkDatabase, ...). For our use case
 * (periodic keep-alive + scheduled daily summary), the platform AlarmManager +
 * a tiny BroadcastReceiver is plenty — and the savings are ~200 KB compressed.
 *
 * Behavior parity with the old WorkManager version:
 *   - schedulePeriodicWatchdog: AlarmManager.setExactAndAllowWhileIdle every 15 min
 *     (was WorkManager PeriodicWorkRequest at 15 min)
 *   - enqueueImmediateWatchdog: AlarmManager.set — fires once, immediately
 *     (was WorkManager OneTimeWorkRequest)
 *   - scheduleDailySummaryWorker: AlarmManager.setWindow at the user's hour:minute
 *   - cancelWatchdog / cancelDailySummaryWorker: AlarmManager.cancel for the
 *     corresponding PendingIntent
 *   - sendImmediateDailySummaryAlert: directly call NotificationHelper (no WorkManager)
 *
 * Power management: AlarmManager.setExactAndAllowWhileIdle still respects Doze —
 * alarms fire during the Doze maintenance window, exactly like WorkManager.
 */
object NetSpeedWorkManagerHelper {
    private const val TAG = "NetSpeedAlarm"

    // PendingIntent request codes — must be distinct so we can cancel them independently.
    private const val WATCHDOG_PERIODIC_REQUEST_CODE = 1001
    private const val WATCHDOG_IMMEDIATE_REQUEST_CODE = 1002
    private const val DAILY_SUMMARY_REQUEST_CODE = 1003

    // 15 minutes in ms (matches the old WorkManager PeriodicWorkRequest interval).
    private const val WATCHDOG_INTERVAL_MS = 15L * 60L * 1000L

    // ---------------------------------------------------------------
    // Watchdog: keep the foreground speed service alive
    // ---------------------------------------------------------------

    /**
     * Schedule a recurring keep-alive alarm that fires every ~15 minutes to check
     * that the speed foreground service is still running.
     *
     * Battery policy (per official Android guidance): a standard utility app must
     * NOT use setExactAndAllowWhileIdle — that flag requires the SCHEDULE_EXACT_ALARM
     * permission, which this app does not declare (and does not need: a watchdog has
     * no timing-precision requirement). The previous exact call always threw
     * SecurityException and silently fell back to set(), which is NOT Doze-aware.
     * setAndAllowWhileIdle is the correct choice: no permission needed, and it
     * respects Doze by firing within the maintenance window.
     */
    fun schedulePeriodicWatchdog(context: Context) {
        try {
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val pi = buildWatchdogPendingIntent(context, WATCHDOG_PERIODIC_REQUEST_CODE)
            val triggerAt = System.currentTimeMillis() + WATCHDOG_INTERVAL_MS
            am.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pi
            )
            // Self-rescheduling: NetSpeedAlarmReceiver re-arms the next alarm after firing,
            // so this acts like a periodic alarm (AlarmManager.setRepeating does NOT respect
            // Doze, but setAndAllowWhileIdle + self-reschedule does).
            Log.d(TAG, "Periodic watchdog alarm scheduled (next fire in 15 min).")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to schedule periodic watchdog alarm", e)
        }
    }

    /**
     * Trigger an immediate one-shot keep-alive alarm (~1s delay, no precision needed).
     * setAndAllowWhileIdle keeps it Doze-safe without the SCHEDULE_EXACT_ALARM permission.
     */
    fun enqueueImmediateWatchdog(context: Context) {
        try {
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val pi = buildWatchdogPendingIntent(context, WATCHDOG_IMMEDIATE_REQUEST_CODE)
            am.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 1000L,
                pi
            )
            Log.d(TAG, "Immediate watchdog alarm enqueued.")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to enqueue immediate watchdog alarm", e)
        }
    }

    /**
     * Cancel any pending watchdog alarms (periodic + immediate).
     */
    fun cancelWatchdog(context: Context) {
        try {
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            am.cancel(buildWatchdogPendingIntent(context, WATCHDOG_PERIODIC_REQUEST_CODE))
            am.cancel(buildWatchdogPendingIntent(context, WATCHDOG_IMMEDIATE_REQUEST_CODE))
            Log.d(TAG, "Watchdog alarms cancelled.")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to cancel watchdog alarms", e)
        }
    }

    private fun buildWatchdogPendingIntent(context: Context, requestCode: Int): PendingIntent {
        val intent = Intent(context, NetSpeedAlarmReceiver::class.java).apply {
            action = NetSpeedAlarmReceiver.ACTION_WATCHDOG
        }
        return PendingIntent.getBroadcast(
            context.applicationContext,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // ---------------------------------------------------------------
    // Daily summary: scheduled notification at user-configured hour
    // ---------------------------------------------------------------

    /**
     * Schedule the daily data usage summary notification to fire at the configured hour:minute.
     * Re-arms itself after firing (NetSpeedAlarmReceiver reschedules the next day).
     */
    fun scheduleDailySummaryWorker(context: Context) {
        try {
            val settingsRepo = com.onlasdan.netnet.data.SpeedSettingsRepository.getInstance(context)
            val settings = settingsRepo.settings.value
            if (!settings.isDailySummaryEnabled) {
                cancelDailySummaryWorker(context)
                return
            }

            val targetHour = settings.dailySummaryHour
            val targetMinute = settings.dailySummaryMinute

            val now = java.util.Calendar.getInstance()
            val target = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, targetHour)
                set(java.util.Calendar.MINUTE, targetMinute)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            // If the target time for today has already passed, schedule for tomorrow.
            if (target.timeInMillis <= now.timeInMillis) {
                target.add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
            val initialDelayMs = target.timeInMillis - now.timeInMillis

            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val pi = buildDailySummaryPendingIntent(context)
            // setWindow allows Android to batch the alarm within a 1-minute window — saves battery
            // without meaningful delay for a daily notification.
            am.setWindow(
                AlarmManager.RTC_WAKEUP,
                target.timeInMillis,
                60_000L,
                pi
            )
            Log.d(TAG, "Daily summary scheduled (in ${initialDelayMs / 1000}s).")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to schedule daily summary alarm", e)
        }
    }

    /**
     * Cancel any pending daily-summary alarm.
     */
    fun cancelDailySummaryWorker(context: Context) {
        try {
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            am.cancel(buildDailySummaryPendingIntent(context))
            Log.d(TAG, "Daily summary alarm cancelled.")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to cancel daily summary alarm", e)
        }
    }

    /**
     * Immediately post the daily usage summary notification (used by the UI "Send test alert"
     * button). Previously routed via a OneTimeWorkRequest; now we just call NotificationHelper
     * directly because we're already on a background-friendly call site.
     */
    fun sendImmediateDailySummaryAlert(context: Context): Boolean {
        return try {
            com.onlasdan.netnet.notification.NotificationHelper.postDailyUsageSummaryNotification(context)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to send immediate daily summary alert", e)
            false
        }
    }

    private fun buildDailySummaryPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, NetSpeedAlarmReceiver::class.java).apply {
            action = NetSpeedAlarmReceiver.ACTION_DAILY_SUMMARY
        }
        return PendingIntent.getBroadcast(
            context.applicationContext,
            DAILY_SUMMARY_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // ---------------------------------------------------------------
    // Power-management queries (used by MainViewModel) — same behavior
    // as the WorkManager version, just thinner.
    // ---------------------------------------------------------------

    /**
     * Returns true if the app is whitelisted from Android Battery Optimizations (Doze exemptions).
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true
            } else {
                true
            }
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Returns true if the system's power-saving mode (Battery Saver) is currently active.
     */
    fun isPowerSaveMode(context: Context): Boolean {
        return try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            powerManager?.isPowerSaveMode == true
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Returns true if the device is currently in deep Doze mode.
     */
    fun isDeviceIdleMode(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                powerManager?.isDeviceIdleMode == true
            } else {
                false
            }
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Attempts to launch the system dialog requesting ignore battery optimization for this package.
     * Falls back to general battery optimization settings if direct request fails.
     */
    @SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimizations(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                true
            } else {
                false
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Direct REQUEST_IGNORE_BATTERY_OPTIMIZATIONS failed, opening settings page", e)
            openBatteryOptimizationSettings(context)
        }
    }

    /**
     * Opens system battery optimization settings screen.
     */
    fun openBatteryOptimizationSettings(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Throwable) {
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                true
            } catch (_: Throwable) {
                false
            }
        }
    }
}
