package com.onlasdan.netnet.data

import android.content.Context
import android.content.SharedPreferences
import com.onlasdan.netnet.model.AppThemeMode
import com.onlasdan.netnet.model.DailyUsageRecord
import com.onlasdan.netnet.model.DisplayMode
import com.onlasdan.netnet.model.NetworkType
import com.onlasdan.netnet.model.NotificationColorTheme
import com.onlasdan.netnet.model.NotificationIconStyle
import com.onlasdan.netnet.model.NotificationIconScale
import com.onlasdan.netnet.model.SpeedUnit
import com.onlasdan.netnet.model.StatusBarChipSize
import com.onlasdan.netnet.model.UsageAnalyticsSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Locale

data class SpeedSettings(
    val isServiceEnabled: Boolean = true,
    val speedUnit: SpeedUnit = SpeedUnit.BYTES,
    val displayMode: DisplayMode = DisplayMode.BOTH,
    val updateIntervalMs: Long = 1000L,
    val autoStartOnBoot: Boolean = true,
    val hideWhenIdle: Boolean = false,
    /** Always render the minimal (textless) shade notification — only the small
     *  status-bar icon survives, zero body text. Saves every per-tick text
     *  relayout/post for users who only want the icon indicator. */
    val isMinimalNotificationEnabled: Boolean = false,
    val showStatusBarChip: Boolean = true,
    val idleThresholdKbPerSec: Long = 0L,
    /** 0 = never update while below [idleThresholdKbPerSec] (notification frozen until
     * traffic crosses the threshold). Only valid with a non-zero threshold. */
    val isThresholdFreezeEnabled: Boolean = true,
    val appThemeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val isOledTheme: Boolean = false,
    val isBatterySaverMode: Boolean = false,
    val autoPauseOnScreenOff: Boolean = true,
    val notificationColorTheme: NotificationColorTheme = NotificationColorTheme.CYAN,
    val notificationIconStyle: NotificationIconStyle = NotificationIconStyle.DYNAMIC_SPEED,
    val notificationIconScale: NotificationIconScale = NotificationIconScale.NORMAL,
    val notificationDetailedLayout: Boolean = true,
    val statusBarChipSize: StatusBarChipSize = StatusBarChipSize.STANDARD,
    val diagnosticPollingIntervalMs: Long = 4000L, // 0 = diagnostics OFF
    val isStuckDetectorEnabled: Boolean = true,
    val stuckDetectorIntervalSec: Int = 5,
    val isDailySummaryEnabled: Boolean = true,
    val dailySummaryHour: Int = 21,
    val dailySummaryMinute: Int = 0,
    val dailySummaryMinThresholdMb: Long = 0L
)

class SpeedSettingsRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<SpeedSettings> = _settings.asStateFlow()

    /**
     * Atomic read-transform-write. Invoked concurrently from the UI thread
     * (settings screen), the foreground service (toggle paths) and background
     * coroutines (diagnostic interval) — without the lock a stale reader could
     * resurrect old values for unrelated fields or drop a concurrent change.
     */
    @Synchronized
    fun updateSettings(transform: (SpeedSettings) -> SpeedSettings) {
        val newSettings = transform(_settings.value)
        _settings.value = newSettings
        saveSettings(newSettings)
    }

    private fun loadSettings(): SpeedSettings {
        return try {
            val oledLegacy = try { prefs.getBoolean(KEY_OLED_THEME, false) } catch (_: Throwable) { false }
            val defaultMode = if (oledLegacy) AppThemeMode.OLED.name else AppThemeMode.SYSTEM.name
            val themeModeStr = try { prefs.getString(KEY_APP_THEME_MODE, defaultMode) ?: defaultMode } catch (_: Throwable) { defaultMode }
            val themeMode = try {
                AppThemeMode.valueOf(themeModeStr)
            } catch (_: Throwable) {
                if (oledLegacy) AppThemeMode.OLED else AppThemeMode.SYSTEM
            }

            val unit = try {
                SpeedUnit.valueOf(prefs.getString(KEY_SPEED_UNIT, SpeedUnit.BYTES.name) ?: SpeedUnit.BYTES.name)
            } catch (_: Throwable) { SpeedUnit.BYTES }

            val dispMode = try {
                DisplayMode.valueOf(prefs.getString(KEY_DISPLAY_MODE, DisplayMode.BOTH.name) ?: DisplayMode.BOTH.name)
            } catch (_: Throwable) { DisplayMode.BOTH }

            val notifColor = try {
                NotificationColorTheme.valueOf(prefs.getString(KEY_NOTIF_COLOR_THEME, NotificationColorTheme.CYAN.name) ?: NotificationColorTheme.CYAN.name)
            } catch (_: Throwable) { NotificationColorTheme.CYAN }

            val notifIcon = try {
                NotificationIconStyle.valueOf(prefs.getString(KEY_NOTIF_ICON_STYLE, NotificationIconStyle.DYNAMIC_SPEED.name) ?: NotificationIconStyle.DYNAMIC_SPEED.name)
            } catch (_: Throwable) { NotificationIconStyle.DYNAMIC_SPEED }

            val iconScale = try {
                NotificationIconScale.valueOf(prefs.getString(KEY_NOTIF_ICON_SCALE, NotificationIconScale.NORMAL.name) ?: NotificationIconScale.NORMAL.name)
            } catch (_: Throwable) { NotificationIconScale.NORMAL }

            // Legacy migration: DETAILED chips (two speeds, "↓2.4M ↑0.5K") exceeded the
            // official 96dp chip limit and rendered clipped/unreadable — old prefs
            // still storing DETAILED are migrated to STANDARD (arrow + value).
            val chipSize = try {
                val stored = prefs.getString(KEY_STATUS_BAR_CHIP_SIZE, StatusBarChipSize.STANDARD.name)
                    ?: StatusBarChipSize.STANDARD.name
                if (stored == "DETAILED") StatusBarChipSize.STANDARD
                else StatusBarChipSize.valueOf(stored)
            } catch (_: Throwable) { StatusBarChipSize.STANDARD }

            SpeedSettings(
                isServiceEnabled = try { prefs.getBoolean(KEY_SERVICE_ENABLED, true) } catch (_: Throwable) { true },
                speedUnit = unit,
                displayMode = dispMode,
                updateIntervalMs = try { prefs.getLong(KEY_UPDATE_INTERVAL, 1000L) } catch (_: Throwable) { 1000L },
                autoStartOnBoot = try { prefs.getBoolean(KEY_AUTO_BOOT, true) } catch (_: Throwable) { true },
                hideWhenIdle = try { prefs.getBoolean(KEY_HIDE_IDLE, false) } catch (_: Throwable) { false },
                isMinimalNotificationEnabled = try { prefs.getBoolean(KEY_MINIMAL_NOTIFICATION, false) } catch (_: Throwable) { false },
                showStatusBarChip = try { prefs.getBoolean(KEY_SHOW_CHIP, true) } catch (_: Throwable) { true },
                idleThresholdKbPerSec = try { prefs.getLong(KEY_IDLE_THRESHOLD_KB_PER_SEC, 0L) } catch (_: Throwable) { 0L },
                isThresholdFreezeEnabled = try { prefs.getBoolean(KEY_THRESHOLD_FREEZE, true) } catch (_: Throwable) { true },
                appThemeMode = themeMode,
                isOledTheme = themeMode == AppThemeMode.OLED,
                isBatterySaverMode = try { prefs.getBoolean(KEY_BATTERY_SAVER_MODE, false) } catch (_: Throwable) { false },
                autoPauseOnScreenOff = try { prefs.getBoolean(KEY_AUTO_PAUSE_SCREEN_OFF, true) } catch (_: Throwable) { true },
                notificationColorTheme = notifColor,
                notificationIconStyle = notifIcon,
                notificationIconScale = iconScale,
                notificationDetailedLayout = try { prefs.getBoolean(KEY_NOTIF_DETAILED_LAYOUT, true) } catch (_: Throwable) { true },
                statusBarChipSize = chipSize,
                diagnosticPollingIntervalMs = try { prefs.getLong(KEY_DIAGNOSTIC_POLLING_INTERVAL, 4000L) } catch (_: Throwable) { 4000L },
                isStuckDetectorEnabled = try { prefs.getBoolean(KEY_STUCK_DETECTOR_ENABLED, true) } catch (_: Throwable) { true },
                stuckDetectorIntervalSec = try { prefs.getInt(KEY_STUCK_DETECTOR_INTERVAL_SEC, 5) } catch (_: Throwable) { 5 },
                isDailySummaryEnabled = try { prefs.getBoolean(KEY_DAILY_SUMMARY_ENABLED, true) } catch (_: Throwable) { true },
                dailySummaryHour = try { prefs.getInt(KEY_DAILY_SUMMARY_HOUR, 21) } catch (_: Throwable) { 21 },
                dailySummaryMinute = try { prefs.getInt(KEY_DAILY_SUMMARY_MINUTE, 0) } catch (_: Throwable) { 0 },
                dailySummaryMinThresholdMb = try { prefs.getLong(KEY_DAILY_SUMMARY_MIN_THRESHOLD_MB, 0L) } catch (_: Throwable) { 0L }
            )
        } catch (_: Throwable) {
            SpeedSettings()
        }
    }

    private fun saveSettings(s: SpeedSettings) {
        val isOled = s.appThemeMode == AppThemeMode.OLED || s.isOledTheme
        prefs.edit()
            .putBoolean(KEY_SERVICE_ENABLED, s.isServiceEnabled)
            .putString(KEY_SPEED_UNIT, s.speedUnit.name)
            .putString(KEY_DISPLAY_MODE, s.displayMode.name)
            .putLong(KEY_UPDATE_INTERVAL, s.updateIntervalMs)
            .putBoolean(KEY_AUTO_BOOT, s.autoStartOnBoot)
            .putBoolean(KEY_HIDE_IDLE, s.hideWhenIdle)
            .putBoolean(KEY_MINIMAL_NOTIFICATION, s.isMinimalNotificationEnabled)
            .putBoolean(KEY_SHOW_CHIP, s.showStatusBarChip)
            .putLong(KEY_IDLE_THRESHOLD_KB_PER_SEC, s.idleThresholdKbPerSec)
            .putBoolean(KEY_THRESHOLD_FREEZE, s.isThresholdFreezeEnabled)
            .putString(KEY_APP_THEME_MODE, s.appThemeMode.name)
            .putBoolean(KEY_OLED_THEME, isOled)
            .putBoolean(KEY_BATTERY_SAVER_MODE, s.isBatterySaverMode)
            .putBoolean(KEY_AUTO_PAUSE_SCREEN_OFF, s.autoPauseOnScreenOff)
            .putString(KEY_NOTIF_COLOR_THEME, s.notificationColorTheme.name)
            .putString(KEY_NOTIF_ICON_STYLE, s.notificationIconStyle.name)
            .putString(KEY_NOTIF_ICON_SCALE, s.notificationIconScale.name)
            .putBoolean(KEY_NOTIF_DETAILED_LAYOUT, s.notificationDetailedLayout)
            .putString(KEY_STATUS_BAR_CHIP_SIZE, s.statusBarChipSize.name)
            .putLong(KEY_DIAGNOSTIC_POLLING_INTERVAL, s.diagnosticPollingIntervalMs)
            .putBoolean(KEY_STUCK_DETECTOR_ENABLED, s.isStuckDetectorEnabled)
            .putInt(KEY_STUCK_DETECTOR_INTERVAL_SEC, s.stuckDetectorIntervalSec)
            .putBoolean(KEY_DAILY_SUMMARY_ENABLED, s.isDailySummaryEnabled)
            .putInt(KEY_DAILY_SUMMARY_HOUR, s.dailySummaryHour)
            .putInt(KEY_DAILY_SUMMARY_MINUTE, s.dailySummaryMinute)
            .putLong(KEY_DAILY_SUMMARY_MIN_THRESHOLD_MB, s.dailySummaryMinThresholdMb)
            .apply()
    }

    // Daily Traffic Persistence with Wi-Fi vs Mobile categorization & In-Memory Delta Batching
    @Volatile private var cachedRx: Long = 0L
    @Volatile private var cachedTx: Long = 0L
    @Volatile private var cachedWifi: Long = 0L
    @Volatile private var cachedCell: Long = 0L
    @Volatile private var cachedOther: Long = 0L
    @Volatile private var hasPendingDiskWrites: Boolean = false
    @Volatile private var lastDiskFlushTime: Long = 0L

    init {
        initMemoryCacheLocked()
    }

    /** Caller must hold the repository monitor (constructor holds it implicitly). */
    private fun initMemoryCacheLocked() {
        val todayKey = getTodayKey()
        cachedTodayKey = todayKey
        cachedRx = prefs.getLong("today_rx_$todayKey", 0L)
        cachedTx = prefs.getLong("today_tx_$todayKey", 0L)
        cachedWifi = prefs.getLong("today_wifi_$todayKey", 0L)
        cachedCell = prefs.getLong("today_cell_$todayKey", 0L)
        cachedOther = prefs.getLong("today_other_$todayKey", 0L)
        hasPendingDiskWrites = false
        lastDiskFlushTime = System.currentTimeMillis()
    }

    // @Synchronized: called both from @Synchronized paths (recordUsageDelta /
    // flushUsageToDisk / getTodayUsage) and internally — the day rollover must
    // not swap the memory cache while another thread is mid delta-booking.
    @Synchronized
    private fun ensureTodayKeyAligned(): String {
        val currentKey = getTodayKey()
        if (currentKey != cachedTodayKey) {
            // Persist the OLD day's counters under the OLD key before the
            // cache re-bases on the new day (only when something is pending).
            if (hasPendingDiskWrites) {
                doFlushLocked(key = cachedTodayKey.ifEmpty { currentKey })
            }
            initMemoryCacheLocked()
        }
        return currentKey
    }

    fun getTodayUsage(): Pair<Long, Long> {
        ensureTodayKeyAligned()
        return Pair(cachedRx, cachedTx)
    }

    /**
     * Records ONE validated window delta. rx and tx must be passed together
     * in a single call — the method is @Synchronized because it is called
     * from the monitor coroutine while the watchdog/notification thread can
     * flush to disk concurrently.
     */
    @Synchronized
    fun recordUsageDelta(networkType: NetworkType, rxDelta: Long, txDelta: Long): Pair<Long, Long> {
        val todayKey = ensureTodayKeyAligned()
        cachedRx += rxDelta
        cachedTx += txDelta

        val totalDelta = rxDelta + txDelta
        when (networkType) {
            NetworkType.WIFI -> cachedWifi += totalDelta
            NetworkType.CELLULAR -> cachedCell += totalDelta
            else -> cachedOther += totalDelta
        }

        hasPendingDiskWrites = true

        // Batch flush every 15 seconds to eliminate continuous flash storage I/O
        val now = System.currentTimeMillis()
        if (now - lastDiskFlushTime >= 15_000L) {
            doFlushLocked(key = todayKey.ifEmpty { getTodayKey() })
        }

        return Pair(cachedRx, cachedTx)
    }

    @Synchronized
    fun flushUsageToDisk() {
        if (!hasPendingDiskWrites && cachedTodayKey.isNotEmpty()) return
        doFlushLocked(key = cachedTodayKey.ifEmpty { getTodayKey() })
    }

    /** Caller must hold the repository monitor. */
    private fun doFlushLocked(key: String) {
        prefs.edit()
            .putLong("today_rx_$key", cachedRx)
            .putLong("today_tx_$key", cachedTx)
            .putLong("today_wifi_$key", cachedWifi)
            .putLong("today_cell_$key", cachedCell)
            .putLong("today_other_$key", cachedOther)
            .apply()
        hasPendingDiskWrites = false
        lastDiskFlushTime = System.currentTimeMillis()
    }

    /** @Synchronized: called from the UI thread while the monitor coroutine can
     *  be mid recordUsageDelta — without the lock the zeroing races a pending
     *  delta and the freshly-zeroed cache resurrects old bytes. */
    @Synchronized
    fun resetTodayUsage() {
        val todayKey = getTodayKey()
        cachedTodayKey = todayKey
        cachedRx = 0L
        cachedTx = 0L
        cachedWifi = 0L
        cachedCell = 0L
        cachedOther = 0L
        hasPendingDiskWrites = false
        lastDiskFlushTime = System.currentTimeMillis()
        prefs.edit()
            .putLong("today_rx_$todayKey", 0L)
            .putLong("today_tx_$todayKey", 0L)
            .putLong("today_wifi_$todayKey", 0L)
            .putLong("today_cell_$todayKey", 0L)
            .putLong("today_other_$todayKey", 0L)
            .apply()
    }

    /**
     * Retrieves historical Wi-Fi vs Cellular data usage for the past N days (7 or 30 days).
     */
    fun getHistoricalUsage(daysCount: Int): UsageAnalyticsSummary {
        // Ensure in-memory deltas are visible before reading prefs back.
        synchronized(this) {
            if (hasPendingDiskWrites) {
                doFlushLocked(key = cachedTodayKey.ifEmpty { getTodayKey() })
            }
        }
        val records = mutableListOf<DailyUsageRecord>()
        val today = java.time.LocalDate.now()
        val shortDayFormat = SimpleDateFormat("EEE", Locale.getDefault())
        val fullDateFormat = SimpleDateFormat("MMM d", Locale.getDefault())

        var totalWifi = 0L
        var totalCell = 0L
        var maxDayBytes = 0L
        var peakLabel = "-"

        for (i in (daysCount - 1) downTo 0) {
            val date = today.minusDays(i.toLong())
            val key = date.format(TODAY_KEY_FORMAT)
            val dayShort = if (i == 0) "Today" else shortDayFormat.format(java.util.Date.from(date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()))
            val dateFull = fullDateFormat.format(java.util.Date.from(date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()))

            var wifi = prefs.getLong("today_wifi_$key", 0L)
            var cell = prefs.getLong("today_cell_$key", 0L)
            var other = prefs.getLong("today_other_$key", 0L)

            // Legacy migration: totals exist but no wifi/cell breakdown was
            // ever recorded. Report the whole legacy amount as "other" instead
            // of fabricating a 75/25 split — a made-up Wi-Fi/Mobile ratio
            // misleads the daily summary and the analytics chart.
            if (wifi == 0L && cell == 0L) {
                other += prefs.getLong("today_rx_$key", 0L) + prefs.getLong("today_tx_$key", 0L)
            }

            val dayTotal = wifi + cell + other
            if (dayTotal > maxDayBytes) {
                maxDayBytes = dayTotal
                peakLabel = dateFull
            }

            totalWifi += wifi
            totalCell += cell

            records.add(
                DailyUsageRecord(
                    dateKey = key,
                    dateFormatted = dateFull,
                    dayShortLabel = dayShort,
                    wifiBytes = wifi,
                    cellBytes = cell,
                    otherBytes = other
                )
            )
        }

        val overallTotal = totalWifi + totalCell
        val wifiPercent = if (overallTotal > 0) (totalWifi.toFloat() / overallTotal) * 100f else 0f
        val cellPercent = if (overallTotal > 0) (totalCell.toFloat() / overallTotal) * 100f else 0f
        val dailyAverage = if (daysCount > 0) overallTotal / daysCount else 0L

        return UsageAnalyticsSummary(
            totalWifiBytes = totalWifi,
            totalCellBytes = totalCell,
            totalBytes = overallTotal,
            dailyAverageBytes = dailyAverage,
            peakDayBytes = maxDayBytes,
            peakDayLabel = peakLabel,
            wifiPercentage = wifiPercent,
            cellPercentage = cellPercent,
            records = records
        )
    }

    /**
     * Today's persistence key, with a per-tick fast path: LocalDate.now()
     * plus DateTimeFormatter allocate several objects and the monitor calls
     * this every tick (1/s). Cache the formatted string per epoch-day so the
     * common case is one volatile long read.
     */
    @Volatile private var cachedEpochDay: Long = -1L
    @Volatile private var cachedTodayKey: String = ""

    private fun getTodayKey(): String {
        val today = java.time.LocalDate.now()
        val epochDay = today.toEpochDay()
        if (epochDay == cachedEpochDay && cachedTodayKey.isNotEmpty()) {
            return cachedTodayKey
        }
        val formatted = today.format(TODAY_KEY_FORMAT)
        cachedEpochDay = epochDay
        cachedTodayKey = formatted
        return formatted
    }

    companion object {
        private const val PREFS_NAME = "net_speed_indicator_prefs"
        private val TODAY_KEY_FORMAT = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd", java.util.Locale.US)
        private const val KEY_SERVICE_ENABLED = "key_service_enabled"
        private const val KEY_SPEED_UNIT = "key_speed_unit"
        private const val KEY_DISPLAY_MODE = "key_display_mode"
        private const val KEY_UPDATE_INTERVAL = "key_update_interval"
        private const val KEY_AUTO_BOOT = "key_auto_boot"
        private const val KEY_HIDE_IDLE = "key_hide_idle"
        private const val KEY_MINIMAL_NOTIFICATION = "key_minimal_notification"
        private const val KEY_SHOW_CHIP = "key_show_chip"
        private const val KEY_IDLE_THRESHOLD_KB_PER_SEC = "key_idle_threshold_kbps" // legacy key name, kept for persistence compat
        private const val KEY_THRESHOLD_FREEZE = "key_threshold_freeze"
        private const val KEY_APP_THEME_MODE = "key_app_theme_mode"
        private const val KEY_OLED_THEME = "key_oled_theme"
        private const val KEY_BATTERY_SAVER_MODE = "key_battery_saver_mode"
        private const val KEY_AUTO_PAUSE_SCREEN_OFF = "key_auto_pause_screen_off"
        private const val KEY_NOTIF_COLOR_THEME = "key_notif_color_theme"
        private const val KEY_NOTIF_ICON_STYLE = "key_notif_icon_style"
        private const val KEY_NOTIF_ICON_SCALE = "key_notif_icon_scale"
        private const val KEY_NOTIF_DETAILED_LAYOUT = "key_notif_detailed_layout"
        private const val KEY_STATUS_BAR_CHIP_SIZE = "key_status_bar_chip_size"
        private const val KEY_DIAGNOSTIC_POLLING_INTERVAL = "key_diagnostic_polling_interval"
        private const val KEY_STUCK_DETECTOR_ENABLED = "key_stuck_detector_enabled"
        private const val KEY_STUCK_DETECTOR_INTERVAL_SEC = "key_stuck_detector_interval_sec"
        private const val KEY_DAILY_SUMMARY_ENABLED = "key_daily_summary_enabled"
        private const val KEY_DAILY_SUMMARY_HOUR = "key_daily_summary_hour"
        private const val KEY_DAILY_SUMMARY_MINUTE = "key_daily_summary_minute"
        private const val KEY_DAILY_SUMMARY_MIN_THRESHOLD_MB = "key_daily_summary_min_threshold_mb"

        @Volatile
        private var INSTANCE: SpeedSettingsRepository? = null

        fun getInstance(context: Context): SpeedSettingsRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SpeedSettingsRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
