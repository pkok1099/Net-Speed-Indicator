package com.onlasdan.netnet.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.onlasdan.netnet.data.SpeedSettings
import com.onlasdan.netnet.data.SpeedSettingsRepository
import com.onlasdan.netnet.model.SpeedPoint
import com.onlasdan.netnet.model.SpeedSnapshot
import com.onlasdan.netnet.monitor.TrafficMonitor
import com.onlasdan.netnet.notification.NotificationHelper
import com.onlasdan.netnet.service.FloatingBubbleService
import com.onlasdan.netnet.service.SpeedTileService
import com.onlasdan.netnet.service.NetSpeedForegroundService
import com.onlasdan.netnet.work.NetSpeedWorkManagerHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import com.onlasdan.netnet.model.ProcessResourceUsage
import com.onlasdan.netnet.model.DetailedNetworkDiagnostics
import com.onlasdan.netnet.model.CompleteNetworkState
import com.onlasdan.netnet.monitor.NetworkStateManager
import com.onlasdan.netnet.monitor.ProcessDiagnosticsHelper
import java.net.HttpURLConnection
import java.net.URL

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val context: Context = application.applicationContext
    private val trafficMonitor = TrafficMonitor.getInstance(context)
    private val settingsRepo = SpeedSettingsRepository.getInstance(context)
    private val networkStateManager = NetworkStateManager.getInstance(context)

    val snapshot: StateFlow<SpeedSnapshot> = trafficMonitor.snapshot
    val history: StateFlow<List<SpeedPoint>> = trafficMonitor.history
    val settings: StateFlow<SpeedSettings> = settingsRepo.settings

    val completeNetworkState: StateFlow<CompleteNetworkState> = networkStateManager.networkState

    val isServiceRunning: StateFlow<Boolean> = NetSpeedForegroundService.isRunning
    val isPaused: StateFlow<Boolean> = NetSpeedForegroundService.isPausedState
    val isFloatingBubbleActive: StateFlow<Boolean> = FloatingBubbleService.isFloatingActive

    private val _processUsage = MutableStateFlow(ProcessResourceUsage())
    val processUsage: StateFlow<ProcessResourceUsage> = _processUsage.asStateFlow()

    private val _networkDiagnostics = MutableStateFlow(networkStateManager.getDiagnostics())
    val networkDiagnostics: StateFlow<DetailedNetworkDiagnostics> = _networkDiagnostics.asStateFlow()

    private val _pingDiagnosticState = MutableStateFlow(com.onlasdan.netnet.model.PingDiagnosticState())
    val pingDiagnosticState: StateFlow<com.onlasdan.netnet.model.PingDiagnosticState> = _pingDiagnosticState.asStateFlow()

    private val _oneTimeDiagnosticState = MutableStateFlow(com.onlasdan.netnet.model.OneTimeDiagnosticState())
    val oneTimeDiagnosticState: StateFlow<com.onlasdan.netnet.model.OneTimeDiagnosticState> = _oneTimeDiagnosticState.asStateFlow()

    private val _isTestingSpeed = MutableStateFlow(false)
    val isTestingSpeed: StateFlow<Boolean> = _isTestingSpeed.asStateFlow()

    private val _isIgnoringBatteryOptimizations = MutableStateFlow(NetSpeedWorkManagerHelper.isIgnoringBatteryOptimizations(context))
    val isIgnoringBatteryOptimizations: StateFlow<Boolean> = _isIgnoringBatteryOptimizations.asStateFlow()

    private val _isSystemPowerSaveMode = MutableStateFlow(NetSpeedWorkManagerHelper.isPowerSaveMode(context))
    val isSystemPowerSaveMode: StateFlow<Boolean> = _isSystemPowerSaveMode.asStateFlow()

    // replay=1: MainActivity.handleIntent() can emit the deep-link target in
    // onCreate() BEFORE the Compose LaunchedEffect starts collecting — without
    // replay that emission is silently dropped on a cold start (tapping the
    // daily-summary notification while the app is dead did nothing).
    // The collector clears the buffer after handling so stale routes from a
    // previous session are not re-applied on recomposition.
    private val _navigationEvent = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 1)
    val navigationEvent: SharedFlow<String> = _navigationEvent.asSharedFlow()

    fun navigateToScreen(route: String) {
        _navigationEvent.tryEmit(route)
    }

    /** Drops the replay cache after the UI handled the route. */
    fun consumeNavigationEvent() {
        _navigationEvent.resetReplayCache()
    }

    private var speedTestJob: Job? = null
    private var pingDiagnosticJob: Job? = null
    private var oneTimeDiagnosticJob: Job? = null

    init {
        // Automatically sync network diagnostics directly from the single source of truth (NetworkStateManager)
        viewModelScope.launch(Dispatchers.Default) {
            networkStateManager.networkState.collect { completeState ->
                _networkDiagnostics.value = completeState.toDetailedDiagnostics()
            }
        }

        // Periodic process telemetry sampler. Pausable: while the UI is
        // backgrounded nobody sees the telemetry, but each tick costs a
        // getProcessMemoryInfo binder IPC plus two power queries — so the
        // loop sleeps in long intervals until the app returns to the
        // foreground (driven by TrafficMonitor's app-foreground flag).
        viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                val inForeground = trafficMonitor.isAppInForeground
                try {
                    if (inForeground) {
                        val usage = ProcessDiagnosticsHelper.sampleProcessUsage(context, isServiceRunning.value)
                        _processUsage.value = usage
                        _isIgnoringBatteryOptimizations.value = NetSpeedWorkManagerHelper.isIgnoringBatteryOptimizations(context)
                        _isSystemPowerSaveMode.value = NetSpeedWorkManagerHelper.isPowerSaveMode(context)
                    }
                } catch (_: Throwable) {}
                delay(if (inForeground) 2000L else 15_000L)
            }
        }
    }

    fun refreshPowerManagementState() {
        _isIgnoringBatteryOptimizations.value = NetSpeedWorkManagerHelper.isIgnoringBatteryOptimizations(context)
        _isSystemPowerSaveMode.value = NetSpeedWorkManagerHelper.isPowerSaveMode(context)
    }

    fun requestIgnoreBatteryOptimizations() {
        NetSpeedWorkManagerHelper.requestIgnoreBatteryOptimizations(context)
        refreshPowerManagementState()
    }

    fun openBatteryOptimizationSettings() {
        NetSpeedWorkManagerHelper.openBatteryOptimizationSettings(context)
        refreshPowerManagementState()
    }

    fun triggerWorkManagerWatchdogSync() {
        NetSpeedWorkManagerHelper.enqueueImmediateWatchdog(context)
        refreshPowerManagementState()
    }

    fun ensureServiceStarted() {
        try {
            if (settings.value.isServiceEnabled && !isServiceRunning.value) {
                NetSpeedForegroundService.startService(context)
            }
        } catch (_: Throwable) {}
    }

    fun toggleService() {
        if (isServiceRunning.value) {
            NetSpeedForegroundService.stopService(context)
            settingsRepo.updateSettings { it.copy(isServiceEnabled = false) }
        } else {
            NetSpeedForegroundService.startService(context)
            settingsRepo.updateSettings { it.copy(isServiceEnabled = true) }
        }
    }

    fun togglePause() {
        NetSpeedForegroundService.togglePause(context, isPaused.value)
    }

    fun toggleFloatingBubble() {
        FloatingBubbleService.toggle(context)
    }

    fun resetSession() {
        trafficMonitor.resetSession()
        NetSpeedForegroundService.resetSession(context)
    }

    fun resetTodayUsage() {
        settingsRepo.resetTodayUsage()
    }

    fun updateSettings(transform: (SpeedSettings) -> SpeedSettings) {
        settingsRepo.updateSettings(transform)
        val currentSettings = settingsRepo.settings.value
        // Smart Battery Saver must keep the core loop alone: never reschedule
        // the watchdog / daily-summary alarms while it is active.
        if (currentSettings.isDailySummaryEnabled && !currentSettings.isBatterySaverMode) {
            NetSpeedWorkManagerHelper.scheduleDailySummaryWorker(context)
        } else {
            NetSpeedWorkManagerHelper.cancelDailySummaryWorker(context)
        }
    }

    fun toggleDailySummary(enabled: Boolean) {
        updateSettings { it.copy(isDailySummaryEnabled = enabled) }
    }

    fun setDailySummaryTime(hour: Int, minute: Int) {
        updateSettings { it.copy(dailySummaryHour = hour, dailySummaryMinute = minute) }
    }

    fun setDailySummaryMinThresholdMb(thresholdMb: Long) {
        updateSettings { it.copy(dailySummaryMinThresholdMb = thresholdMb) }
    }

    fun previewDailySummaryNotification(): Boolean {
        return NetSpeedWorkManagerHelper.sendImmediateDailySummaryAlert(context)
    }

    fun setDiagnosticPollingInterval(intervalMs: Long) {
        networkStateManager.setDiagnosticPollingInterval(intervalMs)
    }

    fun refreshNetworkStateNow() {
        viewModelScope.launch(Dispatchers.IO) {
            networkStateManager.fetchCompleteNetworkState(forceRefreshPing = true)
        }
    }

    fun canPostPromoted(): Boolean {
        return NotificationHelper.canPostPromotedNotifications(context)
    }

    fun runPingDiagnostic() {
        if (_pingDiagnosticState.value.status == com.onlasdan.netnet.model.PingDiagnosticStatus.RUNNING) {
            cancelPingDiagnostic()
            return
        }

        pingDiagnosticJob?.cancel()
        _pingDiagnosticState.value = com.onlasdan.netnet.model.PingDiagnosticState(
            status = com.onlasdan.netnet.model.PingDiagnosticStatus.RUNNING,
            progress = 0.05f,
            currentStep = "Initializing Ping Probes..."
        )

        pingDiagnosticJob = viewModelScope.launch {
            try {
                val gateway = _networkDiagnostics.value.gatewayAddress
                val result = com.onlasdan.netnet.monitor.PingDiagnosticRunner.runDiagnostic(
                    context = context,
                    gatewayIp = gateway,
                    onProgress = { progress, step ->
                        _pingDiagnosticState.value = _pingDiagnosticState.value.copy(
                            progress = progress,
                            currentStep = step
                        )
                    }
                )
                _pingDiagnosticState.value = com.onlasdan.netnet.model.PingDiagnosticState(
                    status = com.onlasdan.netnet.model.PingDiagnosticStatus.COMPLETED,
                    progress = 1.0f,
                    currentStep = "Diagnostic Completed",
                    result = result
                )
            } catch (e: Exception) {
                _pingDiagnosticState.value = com.onlasdan.netnet.model.PingDiagnosticState(
                    status = com.onlasdan.netnet.model.PingDiagnosticStatus.FAILED,
                    progress = 0f,
                    currentStep = "Test Failed",
                    errorMessage = e.message ?: "Network probe error"
                )
            }
        }
    }

    fun cancelPingDiagnostic() {
        pingDiagnosticJob?.cancel()
        _pingDiagnosticState.value = _pingDiagnosticState.value.copy(
            status = com.onlasdan.netnet.model.PingDiagnosticStatus.IDLE,
            progress = 0f,
            currentStep = "Cancelled"
        )
    }

    fun runOneTimeDiagnostic() {
        if (_oneTimeDiagnosticState.value.isRunning) {
            cancelOneTimeDiagnostic()
            return
        }

        oneTimeDiagnosticJob?.cancel()
        _oneTimeDiagnosticState.value = com.onlasdan.netnet.model.OneTimeDiagnosticState(
            stage = com.onlasdan.netnet.model.OneTimeDiagnosticStage.PING_PHASE,
            progress = 0.05f,
            statusMessage = "Starting diagnostic audit..."
        )

        oneTimeDiagnosticJob = viewModelScope.launch {
            try {
                val net = _networkDiagnostics.value
                val result = com.onlasdan.netnet.monitor.OneTimeDiagnosticRunner.runFullDiagnostic(
                    context = context,
                    gatewayIp = net.gatewayAddress,
                    networkType = net.networkType,
                    networkName = net.networkName,
                    ipAddress = net.ipv4Address,
                    onUpdate = { state ->
                        _oneTimeDiagnosticState.value = state
                    }
                )
            } catch (e: Exception) {
                _oneTimeDiagnosticState.value = com.onlasdan.netnet.model.OneTimeDiagnosticState(
                    stage = com.onlasdan.netnet.model.OneTimeDiagnosticStage.FAILED,
                    progress = 0f,
                    statusMessage = "Diagnostic failed",
                    errorMessage = e.message ?: "Failed to run test"
                )
            }
        }
    }

    fun cancelOneTimeDiagnostic() {
        oneTimeDiagnosticJob?.cancel()
        _oneTimeDiagnosticState.value = com.onlasdan.netnet.model.OneTimeDiagnosticState(
            stage = com.onlasdan.netnet.model.OneTimeDiagnosticStage.CANCELLED,
            progress = 0f,
            statusMessage = "Diagnostic cancelled"
        )
    }

    fun runSpeedBurstTest() {
        if (_isTestingSpeed.value) {
            speedTestJob?.cancel()
            _isTestingSpeed.value = false
            return
        }

        _isTestingSpeed.value = true
        speedTestJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                // Download a small chunk of test file (5MB or 10MB test data) from Cloudflare CDN to verify traffic meter
                val url = URL("https://speed.cloudflare.com/__down?bytes=15000000")
                val connection = url.openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 4000
                    connection.readTimeout = 6000
                    connection.connect()

                    connection.inputStream?.use { inputStream ->
                        val buffer = ByteArray(16384)
                        while (inputStream.read(buffer) != -1 && _isTestingSpeed.value) {
                            // reading traffic generates rx stats
                        }
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (_: Exception) {
                // network test completed or timed out
            } finally {
                withContext(Dispatchers.Main) {
                    _isTestingSpeed.value = false
                }
            }
        }
    }
}
