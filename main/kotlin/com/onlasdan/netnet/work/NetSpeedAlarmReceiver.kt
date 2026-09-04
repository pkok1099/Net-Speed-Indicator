package com.onlasdan.netnet.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.onlasdan.netnet.data.SpeedSettingsRepository
import com.onlasdan.netnet.notification.NotificationHelper
import com.onlasdan.netnet.service.NetSpeedForegroundService

/**
 * BroadcastReceiver that replaces both WorkManager workers:
 *
 *  - ACTION_WATCHDOG: fires every ~30 minutes. Mirrors NetSpeedKeepAliveWorker behavior —
 *    flushes traffic counters to disk, restarts the foreground speed service if the user
 *    has it enabled but the service was killed by the OS/OEM, then re-arms the next alarm.
 *
 *  - ACTION_DAILY_SUMMARY: fires once a day at the user-configured hour. Mirrors
 *    DailyUsageSummaryWorker behavior — flushes traffic counters and posts the daily
 *    usage summary notification, then re-arms the next day's alarm.
 *
 * Using a BroadcastReceiver + AlarmManager saves ~480 classes from androidx.work
 * (Worker, CoroutineWorker, WorkManager, WorkSpec, Constraints, WorkDatabase, ...).
 */
class NetSpeedAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        Log.d(TAG, "Alarm received: $action")

        when (action) {
            ACTION_WATCHDOG -> handleWatchdog(context)
            ACTION_DAILY_SUMMARY -> handleDailySummary(context)
        }
    }

    /**
     * Keep-alive logic, ported verbatim from NetSpeedKeepAliveWorker.doWork().
     * Runs synchronously — these calls are all fast (just SharedPreferences writes + a
     * startService call) so we don't need a coroutine or a Worker.
     */
    private fun handleWatchdog(context: Context) {
        try {
            val settingsRepo = SpeedSettingsRepository.getInstance(context)
            val settings = settingsRepo.settings.value
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager

            val isPowerSave = powerManager?.isPowerSaveMode == true
            val isDeviceIdle = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && powerManager?.isDeviceIdleMode == true

            Log.d(TAG, "Watchdog fired. isServiceEnabled=${settings.isServiceEnabled}, isPowerSave=$isPowerSave, isDeviceIdle=$isDeviceIdle")

            // Flush accumulated traffic stats to disk
            settingsRepo.flushUsageToDisk()

            if (settings.isServiceEnabled) {
                if (!NetSpeedForegroundService.isRunning.value) {
                    Log.i(TAG, "Foreground service was inactive. Restarting via alarm.")
                    NetSpeedForegroundService.startService(context)
                }
            } else {
                if (NetSpeedForegroundService.isRunning.value) {
                    NetSpeedForegroundService.stopService(context)
                }
            }

            // Re-arm the next periodic alarm (AlarmManager doesn't have a Doze-friendly
            // periodic mode, so we self-reschedule from the receiver).
            NetSpeedWorkManagerHelper.schedulePeriodicWatchdog(context)
        } catch (e: Throwable) {
            Log.e(TAG, "Error in watchdog handler", e)
            // Still re-arm so we don't lose the periodic heartbeat on transient errors.
            try { NetSpeedWorkManagerHelper.schedulePeriodicWatchdog(context) } catch (_: Throwable) {}
        }
    }

    /**
     * Daily summary logic, ported verbatim from DailyUsageSummaryWorker.doWork().
     */
    private fun handleDailySummary(context: Context) {
        try {
            val settingsRepo = SpeedSettingsRepository.getInstance(context)
            val settings = settingsRepo.settings.value
            Log.d(TAG, "Daily summary alarm fired. isDailySummaryEnabled=${settings.isDailySummaryEnabled}")

            if (settings.isDailySummaryEnabled) {
                settingsRepo.flushUsageToDisk()
                NotificationHelper.postDailyUsageSummaryNotification(context)
                NetSpeedWorkManagerHelper.scheduleDailySummaryWorker(context)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error in daily summary handler", e)
        }
    }

    companion object {
        private const val TAG = "NetSpeedAlarmReceiver"
        const val ACTION_WATCHDOG = "com.onlasdan.netnet.action.WATCHDOG_ALARM"
        const val ACTION_DAILY_SUMMARY = "com.onlasdan.netnet.action.DAILY_SUMMARY_ALARM"
    }
}
