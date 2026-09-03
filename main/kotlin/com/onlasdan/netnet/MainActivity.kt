package com.onlasdan.netnet

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onlasdan.netnet.monitor.TrafficMonitor
import com.onlasdan.netnet.notification.NotificationHelper
import com.onlasdan.netnet.ui.MainScreen
import com.onlasdan.netnet.ui.MainViewModel
import com.onlasdan.netnet.ui.navigation.Screen
import com.onlasdan.netnet.ui.theme.NetSpeedTheme
import androidx.lifecycle.ViewModelProvider

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        ViewModelProvider.AndroidViewModelFactory.getInstance(application)
    }
    private val trafficMonitor by lazy { 
        try {
            TrafficMonitor.getInstance(applicationContext)
        } catch (e: Throwable) {
            Log.e("MainActivity", "TrafficMonitor init error", e)
            null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        try {
            enableEdgeToEdge()
        } catch (_: Throwable) {}

        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            NetSpeedTheme(
                themeMode = settings.appThemeMode,
                isOled = settings.isOledTheme
            ) {
                MainScreen(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val target = intent?.getStringExtra(NotificationHelper.EXTRA_NAVIGATE_TARGET)
        if (target == "data_usage") {
            viewModel.navigateToScreen(Screen.DataUsage.route)
        }
    }

    override fun onStart() {
        super.onStart()
        try {
            trafficMonitor?.setAppForeground(true)
        } catch (_: Throwable) {}
    }

    override fun onResume() {
        super.onResume()
        try {
            viewModel.ensureServiceStarted()
            viewModel.refreshPowerManagementState()
        } catch (_: Throwable) {}
    }

    override fun onStop() {
        try {
            trafficMonitor?.setAppForeground(false)
        } catch (_: Throwable) {}
        super.onStop()
    }
}

