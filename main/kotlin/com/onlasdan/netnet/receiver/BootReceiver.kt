package com.onlasdan.netnet.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.onlasdan.netnet.data.SpeedSettingsRepository
import com.onlasdan.netnet.service.NetSpeedForegroundService
import com.onlasdan.netnet.work.NetSpeedWorkManagerHelper

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            val action = intent.action ?: return
            if (action == Intent.ACTION_BOOT_COMPLETED ||
                action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
                action == Intent.ACTION_MY_PACKAGE_REPLACED ||
                action == "android.intent.action.QUICKBOOT_POWERON" ||
                action == "com.htc.intent.action.QUICKBOOT_POWERON"
            ) {
                val settings = SpeedSettingsRepository.getInstance(context).settings.value
                if (settings.isDailySummaryEnabled) {
                    NetSpeedWorkManagerHelper.scheduleDailySummaryWorker(context)
                }
                if (settings.isServiceEnabled) {
                    if (settings.autoStartOnBoot) {
                        NetSpeedForegroundService.startService(context)
                        NetSpeedWorkManagerHelper.enqueueImmediateWatchdog(context)
                    }
                    // Only arm the periodic keep-alive when the foreground
                    // service is actually being started; scheduling it for a
                    // disabled service just wakes the device every 15 minutes
                    // for a no-op check.
                    NetSpeedWorkManagerHelper.schedulePeriodicWatchdog(context)
                }
            }
        } catch (_: Throwable) {}
    }
}
