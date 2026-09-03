package com.onlasdan.netnet.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.onlasdan.netnet.MainActivity
import com.onlasdan.netnet.data.SpeedSettings
import com.onlasdan.netnet.data.SpeedSettingsRepository
import com.onlasdan.netnet.model.NetworkType
import com.onlasdan.netnet.model.SpeedFormatter
import com.onlasdan.netnet.model.SpeedSnapshot
import com.onlasdan.netnet.monitor.TrafficMonitor
import com.onlasdan.netnet.ui.theme.CyanGlow
import com.onlasdan.netnet.ui.theme.CyanPrimary
import com.onlasdan.netnet.ui.theme.EmeraldGlow
import com.onlasdan.netnet.ui.theme.EmeraldSuccess
import com.onlasdan.netnet.ui.theme.PurpleAccent
import com.onlasdan.netnet.ui.theme.RoseError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FloatingBubbleService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val customViewModelStore = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = customViewModelStore

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private var initialX = 100
    private var initialY = 250

    override fun onCreate() {
        super.onCreate()
        try {
            savedStateRegistryController.performRestore(Bundle())
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

            _isFloatingActive.value = true

            if (!Settings.canDrawOverlays(this)) {
                stopSelf()
                return
            }

            windowManager = getSystemService(WINDOW_SERVICE) as? WindowManager
            setupFloatingWindow()
        } catch (_: Throwable) {
            stopSelf()
        }
    }

    private fun setupFloatingWindow() {
        try {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = initialX
                y = initialY
            }
            layoutParams = params

            val trafficMonitor = TrafficMonitor.getInstance(this)
            val settingsRepo = SpeedSettingsRepository.getInstance(this)

            val composeView = ComposeView(this).apply {
                setViewTreeLifecycleOwner(this@FloatingBubbleService)
                setViewTreeViewModelStoreOwner(this@FloatingBubbleService)
                setViewTreeSavedStateRegistryOwner(this@FloatingBubbleService)

                setContent {
                    val snapshot by trafficMonitor.snapshot.collectAsState()
                    val settings by settingsRepo.settings.collectAsState()

                    FloatingBubbleUi(
                        snapshot = snapshot,
                        settings = settings,
                        onDragDelta = { dx, dy ->
                            try {
                                params.x += dx.toInt()
                                params.y += dy.toInt()
                                windowManager?.updateViewLayout(this, params)
                            } catch (_: Throwable) {}
                        },
                        onOpenApp = {
                            try {
                                val intent = Intent(this@FloatingBubbleService, MainActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                }
                                startActivity(intent)
                            } catch (_: Throwable) {}
                        },
                        onClose = {
                            stopSelf()
                        }
                    )
                }
            }

            floatingView = composeView
            windowManager?.addView(composeView, params)
        } catch (_: Throwable) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        _isFloatingActive.value = false
        try {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            customViewModelStore.clear()
        } catch (_: Throwable) {}

        try {
            floatingView?.let {
                windowManager?.removeView(it)
                floatingView = null
            }
        } catch (_: Throwable) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private val _isFloatingActive = MutableStateFlow(false)
        val isFloatingActive: StateFlow<Boolean> = _isFloatingActive.asStateFlow()

        fun start(context: Context) {
            if (Settings.canDrawOverlays(context)) {
                val intent = Intent(context, FloatingBubbleService::class.java)
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatingBubbleService::class.java)
            context.stopService(intent)
        }

        fun toggle(context: Context) {
            if (_isFloatingActive.value) {
                stop(context)
            } else {
                start(context)
            }
        }
    }
}

@Composable
private fun FloatingBubbleUi(
    snapshot: SpeedSnapshot,
    settings: SpeedSettings,
    onDragDelta: (Float, Float) -> Unit,
    onOpenApp: () -> Unit,
    onClose: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    val dlFormatted = SpeedFormatter.formatSpeed(snapshot.downloadBytesPerSec, settings.speedUnit)
    val ulFormatted = SpeedFormatter.formatSpeed(snapshot.uploadBytesPerSec, settings.speedUnit)

    val pingColor = when {
        snapshot.pingMs in 0..60 -> EmeraldSuccess
        snapshot.pingMs in 61..130 -> Color(0xFFF59E0B)
        snapshot.pingMs > 130 -> RoseError
        else -> Color(0xFF94A3B8)
    }

    val pingText = if (snapshot.pingMs >= 0) "${snapshot.pingMs}ms" else "—"

    Box(
        modifier = Modifier
            .padding(8.dp)
            .shadow(12.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xE6080D1A))
            .border(1.2.dp, CyanPrimary.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDragDelta(dragAmount.x, dragAmount.y)
                }
            }
            .animateContentSize()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            // Main Top Pill Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.clickable { isExpanded = !isExpanded }
            ) {
                // Pulse Indicator
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(if (snapshot.downloadBytesPerSec > 0 || snapshot.uploadBytesPerSec > 0) CyanGlow else EmeraldGlow, CircleShape)
                )

                // Speed Display
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "↓$dlFormatted",
                        color = CyanGlow,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "↑$ulFormatted",
                        color = PurpleAccent,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Expand / Collapse Chevron
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Expand Bubble",
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(14.dp)
                )
            }

            // Expanded HUD Details
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .width(180.dp)
                ) {
                    // Ping & Network Info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (snapshot.networkType == NetworkType.WIFI) Icons.Default.Wifi else Icons.Default.CellTower,
                                contentDescription = null,
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = snapshot.networkName.take(12),
                                fontSize = 10.sp,
                                color = Color(0xFFCBD5E1),
                                maxLines = 1
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(5.dp).background(pingColor, CircleShape))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = pingText,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = pingColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Today usage
                    Text(
                        text = "Today: ${SpeedFormatter.formatDataSize(snapshot.todayTotalBytes)}",
                        fontSize = 10.sp,
                        color = Color(0xFF94A3B8)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Action buttons (Open App & Close)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(CyanPrimary.copy(alpha = 0.2f))
                                .clickable { onOpenApp() }
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text("Open App", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = CyanGlow)
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(RoseError.copy(alpha = 0.2f))
                                .clickable { onClose() }
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text("Close", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = RoseError)
                        }
                    }
                }
            }
        }
    }
}
