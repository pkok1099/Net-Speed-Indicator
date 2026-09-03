package com.onlasdan.netnet.service

import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import com.onlasdan.netnet.data.SpeedSettingsRepository
import com.onlasdan.netnet.monitor.TrafficMonitor
import com.onlasdan.netnet.notification.NotificationHelper
import com.onlasdan.netnet.widget.NetSpeedWidgetProvider
import com.onlasdan.netnet.work.NetSpeedWorkManagerHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch

class NetSpeedForegroundService : Service() {
    private val scope = CoroutineScope(Dispatchers.Main)
    private var collectorJob: Job? = null
    private var batterySaverObserverJob: Job? = null

    private lateinit var trafficMonitor: TrafficMonitor
    private lateinit var settingsRepo: SpeedSettingsRepository
    private lateinit var notificationManager: NotificationManager

    private var isPaused = false
    @Volatile private var isScreenOn = true
    private var lastBatterySaverState: Boolean = false

    // Track previous notification signature parts and idle-throttle state.
    // Split: settings changes interrupt the idle throttle; speed-only changes
    // below the threshold do not (they are already throttled).
    private var wasIdle: Boolean = false
    private var lastNotificationPostTime: Long = 0L
    private var lastSettingsSignature: String = ""
    private var lastSpeedSignature: String = ""

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val autoPause = settingsRepo.settings.value.autoPauseOnScreenOff
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    // Flush any pending disk writes when screen turns off
                    settingsRepo.flushUsageToDisk()
                    if (autoPause) {
                        // Smart Battery Saver: Full pause when screen is off
                        trafficMonitor.stop()
                    } else {
                        // Standard low-frequency background sampling
                        trafficMonitor.setScreenState(false)
                    }
                    // Keep the shade in sync with the now-stale speeds instead
                    // of leaving the last pre-pause numbers on screen.
                    updateNotification("screenOff")
                }
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    isScreenOn = true
                    if (autoPause && !isPaused) {
                        trafficMonitor.start(settingsRepo.settings.value.updateIntervalMs)
                    }
                    trafficMonitor.setScreenState(true)
                    // Immediately update notification & widget on screen wake
                    updateNotification("screenOn")
                }
                PowerManager.ACTION_POWER_SAVE_MODE_CHANGED,
                PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED -> {
                    val pm = context?.getSystemService(Context.POWER_SERVICE) as? PowerManager
                    val isPowerSave = pm?.isPowerSaveMode == true
                    if (isPowerSave) {
                        settingsRepo.flushUsageToDisk()
                    }
                    // Screen state is the source of truth here; a power-save
                    // broadcast arriving while the screen is OFF must not
                    // resurrect the auto-paused monitor (it only re-derives the
                    // low-frequency screen-off cadence for the non-paused path).
                    if (!autoPause || isScreenOn) {
                        trafficMonitor.setScreenState(isScreenOn)
                    }
                }
                // Android 16 (API 36) battery capacity level: when the system
                // reports a LOW battery state it expects background jobs to be
                // limited. Extend the screen-off sampling cadence and stop the
                // in-app ping probe entirely until the level recovers.
                Intent.ACTION_BATTERY_CHANGED -> {
                    val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                    val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                    val capacityLevel = if (Build.VERSION.SDK_INT >= 36) {
                        intent?.getIntExtra(BatteryManager.EXTRA_CAPACITY_LEVEL, -1) ?: -1
                    } else {
                        -1
                    }
                    val isBatteryLow = if (Build.VERSION.SDK_INT >= 36 && capacityLevel >= 0) {
                        capacityLevel <= BatteryManager.BATTERY_CAPACITY_LEVEL_LOW
                    } else {
                        level >= 0 && scale > 0 && (level.toFloat() / scale) <= 0.15f
                    }
                    trafficMonitor.setBatteryLow(isBatteryLow)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        try {
            trafficMonitor = TrafficMonitor.getInstance(this)
            settingsRepo = SpeedSettingsRepository.getInstance(this)
            notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            _isRunning.value = true
            _isPausedState.value = false

            // Ensure notification channel exists before starting foreground
            NotificationHelper.createNotificationChannel(this)

            // Immediately promote to foreground in onCreate to strictly satisfy Android foreground service lifecycle contracts
            val initialSnapshot = trafficMonitor.snapshot.value
            val initialSettings = settingsRepo.settings.value
            val initialNotification = NotificationHelper.buildSpeedNotification(
                this,
                initialSnapshot,
                initialSettings,
                isPaused = false
            )

            if (Build.VERSION.SDK_INT >= 34) {
                try {
                    // specialUse is the correct type for a persistent speed
                    // indicator: it has NO runtime timeout. dataSync (fallback)
                    // is subject to the Android 15/16 FGS timeout policy —
                    // onTimeout() below handles that gracefully instead of a
                    // hard system kill.
                    startForeground(
                        NotificationHelper.NOTIFICATION_ID,
                        initialNotification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    )
                } catch (_: Throwable) {
                    try {
                        startForeground(
                            NotificationHelper.NOTIFICATION_ID,
                            initialNotification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                        )
                    } catch (_: Throwable) {
                        try {
                            startForeground(NotificationHelper.NOTIFICATION_ID, initialNotification)
                        } catch (_: Throwable) {}
                    }
                }
            } else {
                try {
                    startForeground(NotificationHelper.NOTIFICATION_ID, initialNotification)
                } catch (_: Throwable) {}
            }
        } catch (e: Throwable) {
            android.util.Log.e("NetSpeedService", "Error in onCreate startForeground", e)
        }

        // Register screen state & power management listener for battery conservation
        try {
            val screenFilter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
                // Sticky broadcast: registered receivers get the current battery
                // state immediately, so isBatteryLow starts accurate without an
                // extra query.
                addAction(Intent.ACTION_BATTERY_CHANGED)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(screenReceiver, screenFilter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(screenReceiver, screenFilter)
            }
        } catch (_: Throwable) {}

        // Smart Battery Saver observer — when battery saver is toggled ON/OFF, we cancel or
        // re-schedule WorkManager watchdog & daily summary workers so that ONLY the core
        // traffic counter + notification remains active while in battery saver mode.
        lastBatterySaverState = settingsRepo.settings.value.isBatterySaverMode
        applyBatterySaverWorkManagerState(lastBatterySaverState)
        batterySaverObserverJob = scope.launch {
            try {
                settingsRepo.settings.collect { settings ->
                    if (settings.isBatterySaverMode != lastBatterySaverState) {
                        lastBatterySaverState = settings.isBatterySaverMode
                        applyBatterySaverWorkManagerState(settings.isBatterySaverMode)
                    }
                }
            } catch (_: Throwable) {}
        }
    }

    /**
     * When Smart Battery Saver Mode is ON, cancel WorkManager watchdog and daily summary
     * workers so the only active component is the foreground traffic counter + notification.
     * When turned back OFF (and the user-enabled daily summary feature is on), re-schedule them.
     */
    private fun applyBatterySaverWorkManagerState(batterySaverOn: Boolean) {
        try {
            if (batterySaverOn) {
                // Cancel ALL background WorkManager tasks to guarantee that ONLY the core
                // foreground traffic counter + speed notification remains active.
                NetSpeedWorkManagerHelper.cancelWatchdog(this)
                NetSpeedWorkManagerHelper.cancelDailySummaryWorker(this)
                android.util.Log.i("NetSpeedService", "Smart Battery Saver ON: WorkManager watchdog & daily summary cancelled.")
            } else {
                // Reschedule watchdog to keep the foreground service resilient across Doze kills.
                NetSpeedWorkManagerHelper.schedulePeriodicWatchdog(this)
                // Reschedule daily summary worker (only if the user has enabled it).
                val settings = settingsRepo.settings.value
                if (settings.isDailySummaryEnabled) {
                    NetSpeedWorkManagerHelper.scheduleDailySummaryWorker(this)
                }
                android.util.Log.i("NetSpeedService", "Smart Battery Saver OFF: WorkManager watchdog rescheduled.")
            }
        } catch (e: Throwable) {
            android.util.Log.w("NetSpeedService", "applyBatterySaverWorkManagerState failed", e)
        }
    }

    /**
     * Android 15+ FGS timeout policy: if this service ever runs under the
     * dataSync type (the specialUse startForeground above can fail on some
     * OEM builds and fall back to dataSync), the system gives it a limited
     * execution window and then calls onTimeout. Without this override the
     * system would hard-kill the process; instead we flush counters, mark the
     * service stopped cleanly, and re-arm the watchdog so the service comes
     * back via the legitimate alarm path when the OS allows it.
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        android.util.Log.w("NetSpeedService", "FGS timeout (type=$fgsType) — graceful shutdown.")
        try {
            settingsRepo.flushUsageToDisk()
        } catch (_: Throwable) {}
        try {
            _isRunning.value = false
            _isPausedState.value = false
            trafficMonitor.stop()
            NetSpeedWorkManagerHelper.schedulePeriodicWatchdog(this)
            stopSelf()
        } catch (_: Throwable) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopService()
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> {
                isPaused = true
                _isPausedState.value = true
                trafficMonitor.stop()
                updateNotification("pause")
            }
            ACTION_RESUME -> {
                isPaused = false
                _isPausedState.value = false
                trafficMonitor.start(settingsRepo.settings.value.updateIntervalMs)
                updateNotification("resume")
            }
            ACTION_RESET_SESSION -> {
                trafficMonitor.resetSession()
                updateNotification("resetSession")
            }
            else -> {
                startMonitoring()
            }
        }
        return START_STICKY
    }

    private fun startMonitoring() {
        try {
            isPaused = false
            _isPausedState.value = false

            val initialSettings = settingsRepo.settings.value
            trafficMonitor.start(initialSettings.updateIntervalMs)

            collectorJob?.cancel()
            collectorJob = scope.launch {
                combine(trafficMonitor.snapshot, settingsRepo.settings) { snapshot, settings ->
                    Pair(snapshot, settings)
                }
                    // Never let emissions queue up on the Main thread: if the collector
                    // falls behind, skip straight to the LATEST snapshot instead of
                    // processing stale ones one by one (removes queue-induced delay).
                    .conflate()
                    .collect { (snapshot, settings) ->
                        try {
                            // Battery Optimization: Only post notification updates when screen is ON and not paused
                            if (!isPaused && isScreenOn) {
                                val dl = snapshot.downloadBytesPerSec
                                val ul = snapshot.uploadBytesPerSec
                                val now = System.currentTimeMillis()

                                // Split signature: settings changes must ALWAYS post
                                // immediately (user just toggled something); only the
                                // speed-only portion participates in idle throttling.
                                val settingsSignature = "${snapshot.networkName}_${settings.displayMode}_${settings.speedUnit}_${settings.notificationIconStyle}_${settings.notificationColorTheme}_${settings.showStatusBarChip}_${settings.notificationDetailedLayout}_${settings.hideWhenIdle}_${settings.isMinimalNotificationEnabled}_${settings.idleThresholdKbps}_${settings.isThresholdFreezeEnabled}_${settings.isStuckDetectorEnabled}_${settings.stuckDetectorIntervalSec}"

                                // ALWAYS-MINIMAL notification: the rendered body does
                                // not depend on speed at all (icon-only) — rebuilding it
                                // per tick would be pure waste. Post only when the
                                // settings signature (which fully determines it) changes.
                                if (settings.isMinimalNotificationEnabled &&
                                    settingsSignature == lastSettingsSignature &&
                                    lastNotificationPostTime > 0L
                                ) {
                                    return@collect
                                }
                                val speedSignature = "${snapshot.downloadBytesPerSec}_${snapshot.uploadBytesPerSec}"

                                // Idle for CADENCE purposes honors the user's threshold:
                                // threshold > 0 => below it counts as idle (the user
                                // declared they don't care about per-second updates
                                // there); threshold 0 => only true 0 B/s throttles.
                                val thresholdBytes = settings.idleThresholdKbps * 1024L
                                val isBelowThreshold = settings.idleThresholdKbps > 0L &&
                                        (dl + ul) < thresholdBytes
                                val isNotificationIdle = if (isBelowThreshold) {
                                    true
                                } else {
                                    dl == 0L && ul == 0L
                                }

                                if (isNotificationIdle) {
                                    // Threshold FREEZE: below the threshold the
                                    // notification is frozen COMPLETELY — zero posts —
                                    // until traffic crosses the threshold again. The
                                    // last shown value stays; when traffic resumes the
                                    // crossing tick posts instantly.
                                    if (isBelowThreshold && settings.isThresholdFreezeEnabled) {
                                        return@collect
                                    }
                                    // Low-cadence refresh while idle/below-threshold:
                                    // Stuck Detector interval when enabled, else 10s.
                                    val idleIntervalMs = (if (settings.isStuckDetectorEnabled) {
                                        settings.stuckDetectorIntervalSec.coerceIn(2, 60)
                                    } else {
                                        10
                                    }) * 1000L
                                    val justWentIdle = !wasIdle
                                    wasIdle = true
                                    // Settings change always interrupts the throttle;
                                    // within the throttle window, speed-only updates are
                                    // skipped (the notification literally reads
                                    // "Network Idle (< threshold)" below the threshold).
                                    val settingsChanged = settingsSignature != lastSettingsSignature
                                    if (!justWentIdle && !settingsChanged &&
                                        now - lastNotificationPostTime < idleIntervalMs
                                    ) {
                                        return@collect
                                    }
                                    lastSpeedSignature = speedSignature
                                } else {
                                    // Active traffic (above threshold): update immediately —
                                    // this is also the instant interrupt out of idle throttling.
                                    wasIdle = false
                                }

                                lastNotificationPostTime = now
                                lastSettingsSignature = settingsSignature
                                lastSpeedSignature = speedSignature

                                val notif = NotificationHelper.buildSpeedNotification(
                                    this@NetSpeedForegroundService,
                                    snapshot,
                                    settings,
                                    isPaused = false
                                )
                                // setOnlyAlertOnce is applied inside the builder; posting
                                // the same ID with fresh content is the platform-blessed
                                // way to refresh a Live Update (promoted ongoing)
                                // notification without sound/vibration relights.
                                notificationManager.notify(NotificationHelper.NOTIFICATION_ID, notif)

                                // Real-time widget update — guarded so a widget-less device
                                // pays zero IPC cost.
                                if (NetSpeedWidgetProvider.hasActiveWidgets(this@NetSpeedForegroundService)) {
                                    NetSpeedWidgetProvider.updateAllWidgets(
                                        this@NetSpeedForegroundService,
                                        snapshot,
                                        settings
                                    )
                                }
                            }
                        } catch (_: Throwable) {}
                    }
            }
        } catch (_: Throwable) {}
    }

    private fun updateNotification(reason: String = "manual") {
        try {
            val snapshot = trafficMonitor.snapshot.value
            val settings = settingsRepo.settings.value
            lastSettingsSignature = "" // Force update on manual refreshes
            val notif = NotificationHelper.buildSpeedNotification(
                this,
                snapshot,
                settings,
                isPaused = isPaused
            )
            notificationManager.notify(NotificationHelper.NOTIFICATION_ID, notif)
            NetSpeedWidgetProvider.updateAllWidgets(this, snapshot, settings)
        } catch (_: Throwable) {}
    }

    private fun stopService() {
        try {
            _isRunning.value = false
            _isPausedState.value = false
            collectorJob?.cancel()
            batterySaverObserverJob?.cancel()
            settingsRepo.flushUsageToDisk()
            trafficMonitor.stop()
            stopForeground(STOP_FOREGROUND_REMOVE)
            NetSpeedWidgetProvider.updateAllWidgets(this)
            stopSelf()
        } catch (_: Throwable) {}
    }

    override fun onDestroy() {
        _isRunning.value = false
        _isPausedState.value = false
        collectorJob?.cancel()
        batterySaverObserverJob?.cancel()
        settingsRepo.flushUsageToDisk()
        try {
            unregisterReceiver(screenReceiver)
        } catch (_: Exception) {}
        trafficMonitor.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.onlasdan.netnet.action.START"
        const val ACTION_STOP = "com.onlasdan.netnet.action.STOP"
        const val ACTION_PAUSE = "com.onlasdan.netnet.action.PAUSE"
        const val ACTION_RESUME = "com.onlasdan.netnet.action.RESUME"
        const val ACTION_RESET_SESSION = "com.onlasdan.netnet.action.RESET_SESSION"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private val _isPausedState = MutableStateFlow(false)
        val isPausedState: StateFlow<Boolean> = _isPausedState.asStateFlow()

        fun startService(context: Context) {
            try {
                val intent = Intent(context, NetSpeedForegroundService::class.java).apply {
                    action = ACTION_START
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                // Schedule WorkManager watchdog to ensure foreground service longevity across power saves
                NetSpeedWorkManagerHelper.schedulePeriodicWatchdog(context)
            } catch (_: Throwable) {}
        }

        fun stopService(context: Context) {
            try {
                val intent = Intent(context, NetSpeedForegroundService::class.java).apply {
                    action = ACTION_STOP
                }
                context.startService(intent)
                // Cancel WorkManager watchdog when monitoring is explicitly turned off
                NetSpeedWorkManagerHelper.cancelWatchdog(context)
            } catch (_: Throwable) {}
        }

        fun togglePause(context: Context, currentlyPaused: Boolean) {
            try {
                val intent = Intent(context, NetSpeedForegroundService::class.java).apply {
                    action = if (currentlyPaused) ACTION_RESUME else ACTION_PAUSE
                }
                context.startService(intent)
            } catch (_: Throwable) {}
        }

        fun resetSession(context: Context) {
            try {
                val intent = Intent(context, NetSpeedForegroundService::class.java).apply {
                    action = ACTION_RESET_SESSION
                }
                context.startService(intent)
            } catch (_: Throwable) {}
        }
    }
}
