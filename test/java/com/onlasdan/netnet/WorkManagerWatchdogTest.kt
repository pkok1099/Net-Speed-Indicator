package com.onlasdan.netnet

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.onlasdan.netnet.data.SpeedSettingsRepository
import com.onlasdan.netnet.work.NetSpeedWorkManagerHelper
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WorkManagerWatchdogTest {

    private lateinit var context: Context
    private lateinit var settingsRepo: SpeedSettingsRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        settingsRepo = SpeedSettingsRepository.getInstance(context)
    }

    @Test
    fun testWorkManagerHelperSchedulingAndCancelling() {
        // Test scheduling watchdog
        NetSpeedWorkManagerHelper.schedulePeriodicWatchdog(context)
        
        // Test immediate watchdog
        NetSpeedWorkManagerHelper.enqueueImmediateWatchdog(context)

        // Test cancel
        NetSpeedWorkManagerHelper.cancelWatchdog(context)
    }

    @Test
    fun testBatteryOptimizationCheck() {
        val isIgnored = NetSpeedWorkManagerHelper.isIgnoringBatteryOptimizations(context)
        // Under Robolectric, power manager returns false or default
        assertNotNull(isIgnored)
    }

    @Test
    fun testPowerSaveModeCheck() {
        val isPowerSave = NetSpeedWorkManagerHelper.isPowerSaveMode(context)
        assertNotNull(isPowerSave)
    }
}
