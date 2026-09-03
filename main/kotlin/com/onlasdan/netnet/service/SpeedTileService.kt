package com.onlasdan.netnet.service

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.onlasdan.netnet.R
import com.onlasdan.netnet.data.SpeedSettingsRepository
import com.onlasdan.netnet.model.SpeedFormatter
import com.onlasdan.netnet.monitor.TrafficMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class SpeedTileService : TileService() {
    private val scope = CoroutineScope(Dispatchers.Main)
    private var updateJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        try {
            updateTileState()

            updateJob?.cancel()
            val trafficMonitor = TrafficMonitor.getInstance(this)
            val settingsRepo = SpeedSettingsRepository.getInstance(this)

            updateJob = scope.launch {
                try {
                    combine(
                        NetSpeedForegroundService.isRunning,
                        NetSpeedForegroundService.isPausedState,
                        trafficMonitor.snapshot,
                        settingsRepo.settings
                    ) { isRunning, isPaused, snapshot, settings ->
                        Quadruple(isRunning, isPaused, snapshot, settings)
                    }.collect { (isRunning, isPaused, snapshot, settings) ->
                        try {
                            val tile = qsTile ?: return@collect
                            val iconRes = when (settings.notificationIconStyle) {
                                com.onlasdan.netnet.model.NotificationIconStyle.DYNAMIC_SPEED -> R.drawable.ic_speed_indicator
                                com.onlasdan.netnet.model.NotificationIconStyle.SPEEDOMETER -> R.drawable.ic_speed_indicator
                                com.onlasdan.netnet.model.NotificationIconStyle.ARROWS -> R.drawable.ic_notif_arrows
                                com.onlasdan.netnet.model.NotificationIconStyle.SIGNAL -> R.drawable.ic_notif_signal
                                com.onlasdan.netnet.model.NotificationIconStyle.MINIMAL_DOT -> R.drawable.ic_notif_dot
                            }
                            tile.icon = Icon.createWithResource(this@SpeedTileService, iconRes)

                            when {
                                !isRunning -> {
                                    tile.state = Tile.STATE_INACTIVE
                                    tile.label = getString(R.string.tile_name)
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                        tile.subtitle = "Stopped • Tap to Start"
                                    }
                                }
                                isPaused -> {
                                    tile.state = Tile.STATE_INACTIVE
                                    tile.label = getString(R.string.tile_name)
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                        tile.subtitle = "Paused • Tap to Resume"
                                    }
                                }
                                else -> {
                                    tile.state = Tile.STATE_ACTIVE
                                    tile.label = getString(R.string.tile_name)
                                    val speedStr = SpeedFormatter.formatShortChip(snapshot, settings.displayMode, settings.speedUnit)
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                        tile.subtitle = speedStr
                                    }
                                }
                            }
                            tile.updateTile()
                        } catch (_: Throwable) {}
                    }
                } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {}
    }

    override fun onStopListening() {
        updateJob?.cancel()
        updateJob = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val isRunning = NetSpeedForegroundService.isRunning.value
        val isPaused = NetSpeedForegroundService.isPausedState.value

        when {
            !isRunning -> {
                NetSpeedForegroundService.startService(this)
            }
            isPaused -> {
                NetSpeedForegroundService.togglePause(this, true)
            }
            else -> {
                NetSpeedForegroundService.togglePause(this, false)
            }
        }
        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val isRunning = NetSpeedForegroundService.isRunning.value
        val isPaused = NetSpeedForegroundService.isPausedState.value

        tile.state = if (isRunning && !isPaused) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_name)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when {
                !isRunning -> "Stopped"
                isPaused -> "Paused"
                else -> "Active"
            }
        }
        tile.updateTile()
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
