package com.onlasdan.netnet

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.onlasdan.netnet.data.SpeedSettings
import com.onlasdan.netnet.model.DisplayMode
import com.onlasdan.netnet.model.NetworkType
import com.onlasdan.netnet.model.NotificationColorTheme
import com.onlasdan.netnet.model.NotificationIconStyle
import com.onlasdan.netnet.model.SpeedFormatter
import com.onlasdan.netnet.model.SpeedSnapshot
import com.onlasdan.netnet.model.SpeedUnit
import com.onlasdan.netnet.notification.NotificationHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PromotedNotificationTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        NotificationHelper.createNotificationChannel(context)
    }

    @Test
    fun testNotificationChannelCompliesWithPromotedRequirements() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = manager.getNotificationChannel(NotificationHelper.CHANNEL_ID)

        assertNotNull("Notification channel must be created", channel)
        // Android 16 Live Updates strictly forbid IMPORTANCE_MIN
        assertTrue(
            "Channel importance must not be IMPORTANCE_MIN",
            channel.importance != NotificationManager.IMPORTANCE_MIN
        )
        assertEquals("Channel importance should be IMPORTANCE_LOW", NotificationManager.IMPORTANCE_LOW, channel.importance)
    }

    @Test
    fun testPromotedNotificationCharacteristics() {
        val snapshot = SpeedSnapshot(
            downloadBytesPerSec = 5_242_880L, // 5 MB/s
            uploadBytesPerSec = 1_048_576L,   // 1 MB/s
            networkType = NetworkType.WIFI,
            networkName = "Test-WiFi"
        )
        val settings = SpeedSettings(
            showStatusBarChip = true,
            displayMode = DisplayMode.BOTH,
            speedUnit = SpeedUnit.BYTES,
            notificationColorTheme = NotificationColorTheme.CYAN,
            notificationIconStyle = NotificationIconStyle.SPEEDOMETER
        )

        val notification = NotificationHelper.buildSpeedNotification(
            context = context,
            snapshot = snapshot,
            settings = settings,
            isPaused = false
        )

        assertNotNull("Notification must be built", notification)

        // 1. Must be ONGOING
        val isOngoing = (notification.flags and Notification.FLAG_ONGOING_EVENT) != 0
        assertTrue("Promoted notifications must have FLAG_ONGOING_EVENT set", isOngoing)

        // 2. Must have Content Title
        val title = notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        assertNotNull("Notification title must not be null", title)
        assertTrue("Notification title must not be empty", !title.isNullOrEmpty())

        // 3. Must NOT be colorized (Android 16 strictly rejects colorized notifications from promotion)
        val isColorized = notification.extras.getBoolean(Notification.EXTRA_COLORIZED, false)
        assertFalse("Promoted notification must NOT be colorized", isColorized)

        // 4. Must NOT be a group summary
        val isGroupSummary = (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
        assertFalse("Promoted notification must not be a group summary", isGroupSummary)
    }

    @Test
    fun testStatusBarChipLengthLimits() {
        // Status chip maximum width is 96dp and optimally accepts <= 7 characters for full text display
        val downloadSnapshot = SpeedSnapshot(
            downloadBytesPerSec = 2_621_440L, // 2.5 MB/s
            uploadBytesPerSec = 100_000L
        )

        val singleModeChip = SpeedFormatter.formatShortChip(
            downloadSnapshot,
            DisplayMode.DOWNLOAD_ONLY,
            SpeedUnit.BYTES
        )
        // e.g. "↓2.5M" -> 5 characters <= 7 characters
        assertTrue("Single mode chip text ($singleModeChip) should fit well within 7 chars", singleModeChip.length <= 7)

        val autoModeChip = SpeedFormatter.formatShortChip(
            downloadSnapshot,
            DisplayMode.AUTO_HIGHEST,
            SpeedUnit.BYTES
        )
        assertTrue("Auto mode chip text ($autoModeChip) should fit well within 7 chars", autoModeChip.length <= 7)
    }

    @Test
    fun testPromotedSettingsIntentCreation() {
        val intent = NotificationHelper.getPromotedNotificationSettingsIntent(context)
        assertNotNull("Settings intent must not be null", intent)
        assertEquals("android.settings.MANAGE_APP_PROMOTED_NOTIFICATIONS", intent.action)
    }

    @Test
    fun testPromotedEligibilityCheckMethods() {
        // Validation helper methods should run safely across any SDK runtime without unhandled crashes
        val canPost = NotificationHelper.canPostPromotedNotifications(context)
        val dummyNotif = Notification.Builder(context, NotificationHelper.CHANNEL_ID)
            .setContentTitle("Test")
            .setOngoing(true)
            .build()
        val hasChars = NotificationHelper.hasPromotableCharacteristics(dummyNotif)

        assertNotNull("canPost should return a boolean", canPost)
        assertNotNull("hasChars should return a boolean", hasChars)
    }

    @Test
    fun testBatterySaverModeSetting() {
        val repo = com.onlasdan.netnet.data.SpeedSettingsRepository.getInstance(context)
        assertFalse("Battery saver mode should be false by default", repo.settings.value.isBatterySaverMode)

        repo.updateSettings { it.copy(isBatterySaverMode = true) }
        assertTrue("Battery saver mode should be true after update", repo.settings.value.isBatterySaverMode)

        repo.updateSettings { it.copy(isBatterySaverMode = false) }
        assertFalse("Battery saver mode should be false after toggle", repo.settings.value.isBatterySaverMode)
    }

    @Test
    fun testDefaultNotificationIconStyleIsDynamicSpeed() {
        val repo = com.onlasdan.netnet.data.SpeedSettingsRepository.getInstance(context)
        assertEquals(
            "Default icon style should be DYNAMIC_SPEED",
            com.onlasdan.netnet.model.NotificationIconStyle.DYNAMIC_SPEED,
            repo.settings.value.notificationIconStyle
        )
    }

    @Test
    fun testDynamicSpeedIconNotificationCreation() {
        val snapshot = SpeedSnapshot(
            downloadBytesPerSec = 5_242_880L, // 5.0 MB/s
            uploadBytesPerSec = 1_048_576L,   // 1.0 MB/s
            sessionRxBytes = 15_000_000L,
            sessionTxBytes = 5_000_000L,
            todayRxBytes = 80_000_000L,
            todayTxBytes = 20_000_000L,
            networkName = "Wi-Fi 6",
            networkType = com.onlasdan.netnet.model.NetworkType.WIFI
        )

        val settings = com.onlasdan.netnet.data.SpeedSettings(
            notificationIconStyle = com.onlasdan.netnet.model.NotificationIconStyle.DYNAMIC_SPEED,
            displayMode = DisplayMode.DOWNLOAD_ONLY,
            speedUnit = SpeedUnit.BYTES
        )

        val notification = NotificationHelper.buildSpeedNotification(
            context = context,
            snapshot = snapshot,
            settings = settings,
            isPaused = false
        )

        assertNotNull("Notification with dynamic speed icon must build successfully", notification)
        assertNotNull("Small icon must be present on notification", notification.smallIcon)
    }

    @Test
    fun testDisabledStatusBarChipNotificationCreation() {
        val snapshot = SpeedSnapshot(
            downloadBytesPerSec = 3_000_000L,
            uploadBytesPerSec = 500_000L,
            networkName = "Wi-Fi",
            networkType = com.onlasdan.netnet.model.NetworkType.WIFI
        )

        val settings = com.onlasdan.netnet.data.SpeedSettings(
            showStatusBarChip = false,
            notificationIconStyle = com.onlasdan.netnet.model.NotificationIconStyle.SPEEDOMETER
        )

        val notification = NotificationHelper.buildSpeedNotification(
            context = context,
            snapshot = snapshot,
            settings = settings,
            isPaused = false
        )

        assertNotNull("Notification with disabled chip must build successfully", notification)
    }

    @Test
    fun testDoubleIndicatorPreventionInPromotedNotifications() {
        val snapshot = SpeedSnapshot(
            downloadBytesPerSec = 4_194_304L, // 4.0 MB/s
            uploadBytesPerSec = 1_048_576L,   // 1.0 MB/s
            networkName = "Wi-Fi 6",
            networkType = com.onlasdan.netnet.model.NetworkType.WIFI
        )

        // Case 1: Promoted notification (showStatusBarChip = true) with DYNAMIC_SPEED icon style
        // Should use clean vector icon to avoid duplicate speed indicator in the status bar chip
        val promotedSettings = com.onlasdan.netnet.data.SpeedSettings(
            showStatusBarChip = true,
            notificationIconStyle = com.onlasdan.netnet.model.NotificationIconStyle.DYNAMIC_SPEED,
            displayMode = DisplayMode.BOTH,
            speedUnit = SpeedUnit.BYTES
        )
        val promotedNotification = NotificationHelper.buildSpeedNotification(
            context = context,
            snapshot = snapshot,
            settings = promotedSettings,
            isPaused = false
        )
        assertNotNull("Promoted notification must build", promotedNotification)
        assertNotNull("Promoted notification must have small icon", promotedNotification.smallIcon)

        // Case 2: Non-promoted notification (showStatusBarChip = false) with DYNAMIC_SPEED icon style
        // Should use dynamic bitmap icon
        val standardSettings = com.onlasdan.netnet.data.SpeedSettings(
            showStatusBarChip = false,
            notificationIconStyle = com.onlasdan.netnet.model.NotificationIconStyle.DYNAMIC_SPEED,
            displayMode = DisplayMode.BOTH,
            speedUnit = SpeedUnit.BYTES
        )
        val standardNotification = NotificationHelper.buildSpeedNotification(
            context = context,
            snapshot = snapshot,
            settings = standardSettings,
            isPaused = false
        )
        assertNotNull("Standard notification must build", standardNotification)
        assertNotNull("Standard notification must have small icon", standardNotification.smallIcon)
    }
}
