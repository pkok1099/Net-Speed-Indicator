# Settings reference

All user settings live in one `SpeedSettings` data class
(`data/SpeedSettingsRepository.kt`), persisted in the
`net_speed_indicator_prefs` SharedPreferences file and exposed as a
`StateFlow`. Every write goes through the `@Synchronized`
`updateSettings { it.copy(...) }` read-transform-write — never write the
prefs directly.

The Settings screen (`ui/screens/SettingsScreen.kt`) groups these into
sections with toggle rows and info tooltips.

## Service & display

| Setting | Default | Effect |
|---|---|---|
| `isServiceEnabled` | `true` | master switch; when off, boot receiver and watchdog don't run the service |
| `speedUnit` | `BYTES` | Bytes (B/s, KB/s, MB/s) vs Bits (bps, Kbps, Mbps) — applies everywhere via `SpeedFormatter` |
| `displayMode` | `BOTH` | down+up, download only, upload only, or auto (highest) |
| `updateIntervalMs` | `1000` | sampling cadence; change wakes the monitor loop immediately |
| `autoStartOnBoot` | `true` | BootReceiver starts the service after boot/update |
| `appThemeMode` | `SYSTEM` | System / Light / Dark / OLED pure black / Sakura Pink |
| `isOledTheme` | derived | legacy key kept in sync with `appThemeMode == OLED` |

## Notification & status bar

| Setting | Default | Effect |
|---|---|---|
| `showStatusBarChip` | `true` | request the Android 16 promoted status-bar pill |
| `statusBarChipSize` | `STANDARD` | chip text width: `↓2.4M` (STANDARD) or `2.4M` (COMPACT); legacy `DETAILED` migrated to STANDARD |
| `notificationIconStyle` | `DYNAMIC_SPEED` | dynamic speed-number icon, gauge, arrows, signal or dot |
| `notificationIconScale` | `NORMAL` | 0.80 / 1.00 / 1.25 × status-bar icon size |
| `notificationColorTheme` | `CYAN` | accent color for icon + notification |
| `notificationDetailedLayout` | `true` | full multi-field body vs compact text |
| `isMinimalNotificationEnabled` | `false` | textless shade notification, icon only |
| `hideWhenIdle` | `false` | swap in a fully hidden notification variant while idle |
| `idleThresholdKbps` | `0` | traffic below this counts as idle (for hide/throttle) |
| `isThresholdFreezeEnabled` | `true` | with a non-zero threshold, freeze below-threshold updates entirely |

## Battery

| Setting | Default | Effect |
|---|---|---|
| `autoPauseOnScreenOff` | `true` | fully pause sampling when screen is off (vs 10s/30s low-frequency) |
| `isBatterySaverMode` | `false` | Smart Battery Saver: disables non-core loops (ping probes, watchdog, daily summary), keeps core cadence |

## Diagnostics

| Setting | Default | Effect |
|---|---|---|
| `diagnosticPollingIntervalMs` | `4000` | ping-probe cadence; `0` = diagnostics off |
| `isStuckDetectorEnabled` | `true` | see below |
| `stuckDetectorIntervalSec` | `5` (2–60) | how often an idle (frozen) notification still refreshes |

## Daily summary

| Setting | Default | Effect |
|---|---|---|
| `isDailySummaryEnabled` | `true` | post a daily usage notification |
| `dailySummaryHour` / `dailySummaryMinute` | `21:00` | when it fires (re-arms daily) |
| `dailySummaryMinThresholdMb` | `0` | skip the notification if today's total is below this |

## Persistence details

- Keys are `key_*` constants in the repository companion object.
- Enum values are stored by `name`; unknown/corrupt values fall back to the
  default via the defensive `try/catch` load style — settings can never
  crash the app.
- Legacy migrations: `key_oled_theme` boolean → `AppThemeMode.OLED`;
  chip-size `DETAILED` → `STANDARD`.

## Speed test of settings plumbing

Any change to the settings flow is picked up live: TrafficMonitor collects
`settings` and wakes its loop (so interval changes apply immediately), and the
service's notification rebuild reacts to a settings-signature change (which
also interrupts idle throttling).
