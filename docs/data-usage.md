# Data usage accounting

All usage bookkeeping lives in `data/SpeedSettingsRepository.kt` — the same
singleton that owns settings. There is no Room database: daily records are
SharedPreferences entries keyed by `yyyyMMdd` date.

## How traffic is recorded

Every validated sampling window, `TrafficMonitor` calls
`SpeedSettingsRepository.recordUsageDelta(networkType, rxDelta, txDelta)`
with the **raw** (unsmoothed) byte deltas:

- rx and tx are passed in one `@Synchronized` call — the monitor coroutine
  writes while the watchdog/notification thread can flush to disk
  concurrently.
- Totals are split into Wi-Fi / Cellular / Other buckets based on the
  current `NetworkType`.
- Cached counters (`cachedRx`, `cachedWifi`, ...) live in memory.

## Flush policy

Writes to disk are batched to eliminate continuous flash I/O:

- a flush fires at most every **15 seconds** (piggybacked on the next
  `recordUsageDelta`),
- `flushUsageToDisk()` forces a flush on screen-off and before the daily
  summary notification,
- each flush writes `today_rx_<key>`, `today_tx_<key>`, `today_wifi_<key>`,
  `today_cell_<key>`, `today_other_<key>` for the day's key.

A day rollover (`ensureTodayKeyAligned`) switches the key at midnight without
losing pending bytes.

## What the UI shows

- **Data Usage screen**: today's download/upload/total, split summary cards,
  active-session card (session bytes + peaks, reset via `resetSession()`),
  and actions (reset today's usage, send a test daily-summary notification).
- **Usage analytics**: `getHistoricalUsage(daysCount)` builds a
  `UsageAnalyticsSummary` (7 or 30 days) with total Wi-Fi/cellular bytes,
  daily average, peak day and Wi-Fi/cellular percentages from the
  `DailyUsageRecord` list.

## Daily summary notification

- Scheduled by `NetSpeedWorkManagerHelper.scheduleDailySummaryWorker` via
  `AlarmManager.setWindow` (60s batching window) at the user's
  `dailySummaryHour:dailySummaryMinute` (default 21:00).
- On fire (`NetSpeedAlarmReceiver` → `DAILY_SUMMARY_ALARM`): flush usage,
  post the summary notification (if enabled), re-arm for tomorrow.
- `dailySummaryMinThresholdMb` suppresses the notification on light-usage
  days (default 0 MB = always notify).
- Deep-links to the Data Usage screen via `EXTRA_NAVIGATE_TARGET`.

## Resets

- `resetTodayUsage()` zeroes today's counters (memory + prefs).
- `resetSession()` (TrafficMonitor) zeroes session accumulators, peaks and
  history — see [traffic-monitoring.md](traffic-monitoring.md).
