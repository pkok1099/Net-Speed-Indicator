package com.onlasdan.netnet.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.util.Log
import android.widget.RemoteViews
import com.onlasdan.netnet.MainActivity
import com.onlasdan.netnet.R
import com.onlasdan.netnet.data.SpeedSettings
import com.onlasdan.netnet.data.SpeedSettingsRepository
import com.onlasdan.netnet.model.NetworkType
import com.onlasdan.netnet.model.SpeedFormatter
import com.onlasdan.netnet.model.SpeedSnapshot
import com.onlasdan.netnet.monitor.TrafficMonitor
import com.onlasdan.netnet.service.NetSpeedForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NetSpeedWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        if (appWidgetIds.isEmpty()) return
        try {
            val monitor = TrafficMonitor.getInstance(context)
            val repo = SpeedSettingsRepository.getInstance(context)
            val snapshot = monitor.snapshot.value
            val settings = repo.settings.value
            val isRunning = NetSpeedForegroundService.isRunning.value
            val isPaused = NetSpeedForegroundService.isPausedState.value

            for (widgetId in appWidgetIds) {
                try {
                    val views = buildRemoteViews(context, snapshot, settings, isRunning, isPaused)
                    appWidgetManager.updateAppWidget(widgetId, views)
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating single widget $widgetId", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onUpdate", e)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        try {
            super.onReceive(context, intent)
            when (intent.action) {
                ACTION_WIDGET_REFRESH -> {
                    // goAsync keeps the process alive for the duration of the
                    // background refresh, replacing the old ad-hoc
                    // CoroutineScope(Dispatchers.Default) launch (which had
                    // no lifecycle and could be killed mid-flight).
                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.Default).launch {
                        try {
                            val monitor = TrafficMonitor.getInstance(context)
                            val repo = SpeedSettingsRepository.getInstance(context)
                            updateAllWidgets(context, monitor.snapshot.value, repo.settings.value)
                        } catch (e: Throwable) {
                            Log.e(TAG, "Error handling refresh action", e)
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
                ACTION_WIDGET_TOGGLE_SERVICE -> {
                    try {
                        val isRunning = NetSpeedForegroundService.isRunning.value
                        val isPaused = NetSpeedForegroundService.isPausedState.value

                        if (!isRunning) {
                            NetSpeedForegroundService.startService(context)
                        } else {
                            NetSpeedForegroundService.togglePause(context, currentlyPaused = isPaused)
                        }
                        // Refresh widget views
                        val monitor = TrafficMonitor.getInstance(context)
                        val repo = SpeedSettingsRepository.getInstance(context)
                        updateAllWidgets(context, monitor.snapshot.value, repo.settings.value)
                    } catch (e: Throwable) {
                        Log.e(TAG, "Error handling toggle action", e)
                    }
                }
            }
        } catch (_: Throwable) {}
    }

    companion object {
        private const val TAG = "NetSpeedWidgetProvider"
        const val ACTION_WIDGET_REFRESH = "com.onlasdan.netnet.action.WIDGET_REFRESH"
        const val ACTION_WIDGET_TOGGLE_SERVICE = "com.onlasdan.netnet.action.WIDGET_TOGGLE_SERVICE"

        fun hasActiveWidgets(context: Context): Boolean {
            return try {
                val appWidgetManager = AppWidgetManager.getInstance(context) ?: return false
                val provider = ComponentName(context, NetSpeedWidgetProvider::class.java)
                val widgetIds = appWidgetManager.getAppWidgetIds(provider)
                widgetIds != null && widgetIds.isNotEmpty()
            } catch (e: Throwable) {
                false
            }
        }

        fun updateAllWidgets(
            context: Context,
            snapshot: SpeedSnapshot? = null,
            settings: SpeedSettings? = null
        ) {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
                val provider = ComponentName(context, NetSpeedWidgetProvider::class.java)
                val widgetIds = appWidgetManager.getAppWidgetIds(provider) ?: return
                if (widgetIds.isEmpty()) return

                val currentSnapshot = snapshot ?: TrafficMonitor.getInstance(context).snapshot.value
                val currentSettings = settings ?: SpeedSettingsRepository.getInstance(context).settings.value
                val isRunning = NetSpeedForegroundService.isRunning.value
                val isPaused = NetSpeedForegroundService.isPausedState.value

                for (widgetId in widgetIds) {
                    try {
                        val views = buildRemoteViews(context, currentSnapshot, currentSettings, isRunning, isPaused)
                        appWidgetManager.updateAppWidget(widgetId, views)
                    } catch (e: Throwable) {
                        Log.e(TAG, "Failed updating widget $widgetId", e)
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to updateAllWidgets", e)
            }
        }

        fun buildRemoteViews(
            context: Context,
            snapshot: SpeedSnapshot,
            settings: SpeedSettings,
            isServiceRunning: Boolean,
            isPaused: Boolean
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_net_speed)

            // Speed values and units
            val (dlVal, dlUnit) = SpeedFormatter.formatSpeedValue(snapshot.downloadBytesPerSec, settings.speedUnit)
            val (ulVal, ulUnit) = SpeedFormatter.formatSpeedValue(snapshot.uploadBytesPerSec, settings.speedUnit)

            views.setTextViewText(R.id.widget_tv_download_val, dlVal)
            views.setTextViewText(R.id.widget_tv_download_unit, dlUnit)
            views.setTextViewText(
                R.id.widget_tv_download_peak,
                "Peak: " + SpeedFormatter.formatSpeed(snapshot.peakDownloadBytesPerSec, settings.speedUnit)
            )

            views.setTextViewText(R.id.widget_tv_upload_val, ulVal)
            views.setTextViewText(R.id.widget_tv_upload_unit, ulUnit)
            views.setTextViewText(
                R.id.widget_tv_upload_peak,
                "Peak: " + SpeedFormatter.formatSpeed(snapshot.peakUploadBytesPerSec, settings.speedUnit)
            )

            // Network Info
            val netName = if (snapshot.networkName.isNotEmpty() && snapshot.networkName != "Unknown") {
                snapshot.networkName
            } else {
                snapshot.networkType.title
            }
            views.setTextViewText(R.id.widget_tv_network_name, netName)
            views.setTextViewText(R.id.widget_tv_network_type_badge, snapshot.networkType.name)

            val netIconRes = when (snapshot.networkType) {
                NetworkType.WIFI -> R.drawable.ic_widget_wifi
                NetworkType.CELLULAR -> R.drawable.ic_widget_cell
                else -> R.drawable.ic_speed_indicator
            }
            views.setImageViewResource(R.id.widget_iv_net_icon, netIconRes)

            // Today's Usage
            val todayUsageFormatted = SpeedFormatter.formatDataSize(snapshot.todayTotalBytes)
            views.setTextViewText(R.id.widget_tv_today_usage, "Today: $todayUsageFormatted")

            // Status / Ping indicator
            when {
                isPaused -> {
                    views.setTextViewText(R.id.widget_tv_ping_or_status, "PAUSED")
                    views.setTextColor(R.id.widget_tv_ping_or_status, Color.parseColor("#F59E0B"))
                }
                !isServiceRunning -> {
                    views.setTextViewText(R.id.widget_tv_ping_or_status, "STANDBY")
                    views.setTextColor(R.id.widget_tv_ping_or_status, Color.parseColor("#94A3B8"))
                }
                snapshot.pingMs >= 0 -> {
                    views.setTextViewText(R.id.widget_tv_ping_or_status, "${snapshot.pingMs} ms")
                    views.setTextColor(R.id.widget_tv_ping_or_status, Color.parseColor("#10B981"))
                }
                else -> {
                    views.setTextViewText(R.id.widget_tv_ping_or_status, "LIVE")
                    views.setTextColor(R.id.widget_tv_ping_or_status, Color.parseColor("#10B981"))
                }
            }

            // Click Intent: Launch MainActivity on widget body tap
            val appIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val appPendingIntent = PendingIntent.getActivity(
                context,
                1001,
                appIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, appPendingIntent)

            // Click Intent: Refresh Button
            val refreshIntent = Intent(context, NetSpeedWidgetProvider::class.java).apply {
                action = ACTION_WIDGET_REFRESH
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                1002,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_refresh, refreshPendingIntent)

            // Click Intent: Toggle Service Button
            val toggleIntent = Intent(context, NetSpeedWidgetProvider::class.java).apply {
                action = ACTION_WIDGET_TOGGLE_SERVICE
            }
            val togglePendingIntent = PendingIntent.getBroadcast(
                context,
                1003,
                toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_toggle, togglePendingIntent)

            return views
        }
    }
}
