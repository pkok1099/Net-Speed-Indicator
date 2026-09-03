# Architecture

This app is deliberately small: one Gradle module, no DI framework, no
navigation library, no database, no HTTP client. Shared state lives in
thread-safe singletons exposed as `StateFlow`s; the Compose UI just collects
them.

## Component map

```
NetSpeedApp (Application)
│  crash logging → Android/data/<pkg>/files/startup_crash.log (512 KB rotate)
│  creates notification channels
│  arms watchdog + daily-summary alarms if enabled in settings
│
MainActivity (single Activity, Compose)
│  reads EXTRA_NAVIGATE_TARGET from the launch intent (deep links)
│  hosts MainScreen
│
MainScreen (ui/MainScreen.kt)
│  manual state-based navigation (see below)
│
MainViewModel (AndroidViewModel)
   exposes snapshot / history / settings / service state / diagnostics
   runs the process-telemetry sampler (2s) + speed-burst test
   emits navigationEvent (SharedFlow) → MainScreen collector

Singletons (getInstance(context), @Volatile double-checked locking):

TrafficMonitor (monitor/TrafficMonitor.kt)
   sampling engine. Emits SpeedSnapshot + List<SpeedPoint> history.
   See docs/traffic-monitoring.md.

SpeedSettingsRepository (data/SpeedSettingsRepository.kt)
   SharedPreferences-backed SpeedSettings StateFlow
   + today's byte counters + historical daily usage records.

NetworkStateManager (monitor/NetworkStateManager.kt)
   ConnectivityManager.NetworkCallback → CompleteNetworkState
   (network type/name/IP/link speed + periodic ping probe).
   Single source of truth for connectivity state.

NetSpeedForegroundService (service/)
   owns the persistent speed notification; static isRunning / isPausedState
   StateFlows so anyone can observe without binding.
```

## Everything is a StateFlow

There is no event bus and no service binding. Components observe:

| Flow | Source | Consumers |
|---|---|---|
| `TrafficMonitor.snapshot` | TrafficMonitor | service → notification, widget, tile, UI |
| `TrafficMonitor.history` | TrafficMonitor | UI chart (gated on app foreground) |
| `SpeedSettingsRepository.settings` | repository | everything |
| `NetworkStateManager.networkState` | NetworkStateManager | TrafficMonitor, UI diagnostics |
| `NetSpeedForegroundService.isRunning` / `isPausedState` | service companion | UI, widget, tile |
| `FloatingBubbleService.isFloatingActive` | service companion | UI |

The UI collects with `collectAsStateWithLifecycle()`. The service's collector
is `conflate()`d so back-to-back snapshot emissions don't queue up.

## Navigation is manual

`androidx.navigation.compose` was removed (~300 classes saved). Instead:

- `ui/navigation/Screen.kt` defines a sealed class `Screen` with
  `Dashboard` / `DataUsage` / `Diagnostics`, each with `route`, title, icons
  and a `testTag` (`nav_dashboard_tab`, `nav_data_usage_tab`,
  `nav_diagnostics_tab`).
- `MainScreen` holds `currentScreen: Screen` in
  `remember { mutableStateOf }`; tab clicks write to it;
  `AnimatedContent(currentScreen)` renders the active screen with slide/fade
  transitions.
- **Deep links**: tapping the speed or daily-summary notification opens
  `MainActivity` with `EXTRA_NAVIGATE_TARGET`. `MainViewModel.navigationEvent`
  (a `MutableSharedFlow` with `extraBufferCapacity = 1`) carries the route to
  the `MainScreen` collector, which switches `currentScreen`.
- There is also a Settings destination reachable from the dashboard
  (Settings icon / drawer), rendered on top of the tab scaffold.

## Background keep-alive chain

The service must survive OEM task killers. Three mechanisms cooperate:

1. **BootReceiver** — on `BOOT_COMPLETED` / `LOCKED_BOOT_COMPLETED` /
   `MY_PACKAGE_REPLACED` (plus OEM quickboot actions): restarts the service
   (when `autoStartOnBoot` is on), enqueues an immediate watchdog check, and
   re-arms the periodic watchdog + daily summary alarms.
2. **Watchdog alarm** (`work/NetSpeedAlarmReceiver.kt`, action
   `WATCHDOG_ALARM`) — fires every ~15 minutes and re-checks that the
   foreground service is alive, restarting it if needed. It re-arms itself
   after each fire, because:
   - `AlarmManager.setRepeating` ignores Doze, and
   - `setExactAndAllowWhileIdle` needs the `SCHEDULE_EXACT_ALARM` permission
     this app does not declare.
   `setAndAllowWhileIdle` + self-reschedule respects Doze *and* needs no
   permission.
3. **Foreground service itself** — `specialUse|dataSync` FGS type with the
   special-use subtype property "Real-time Network Speed Indicator and Traffic
   Monitor", so the process is never cached-away while measuring.

> Note: `work/NetSpeedWorkManagerHelper` is **misnamed** — it is pure
> AlarmManager scheduling now. The name is kept because it is referenced
> widely. Do not reintroduce WorkManager.

Daily summary is a second alarm (`DAILY_SUMMARY_ALARM`) scheduled with
`setWindow` (1-minute batching window) at the user's chosen time; on fire it
flushes usage to disk, posts the summary notification and re-arms for the next
day.

## Service ↔ UI contract

The foreground service exposes **static companion StateFlows**
(`isRunning`, `isPausedState`) updated from `onStartCommand` / screen events.
The ViewModel wires them straight into the UI, so toggling the service from
the widget, tile or boot receiver is reflected everywhere without any
binding or broadcast.

Screen-state policy lives in the service:

- `ACTION_SCREEN_OFF` → flush usage to disk; then either full pause
  (`autoPauseOnScreenOff`) or 10s screen-off cadence (30s if battery low).
- `ACTION_SCREEN_ON` / `USER_PRESENT` → wake the monitor for an immediate
  sample and refresh notification + widget.
- Power-save / device-idle broadcasts only re-derive the low-frequency
  cadence; they never resurrect an auto-paused monitor while the screen is
  off.

## Conventions you must follow

- **Settings updates** always go through
  `SpeedSettingsRepository.updateSettings { it.copy(...) }` — a
  `@Synchronized` read-transform-write. Never write SharedPreferences
  directly (concurrent writers would resurrect stale fields).
- **Defensive exception swallowing**
  (`try { ... } catch (_: Throwable) {}`) is the established style for
  non-critical Android lifecycle paths — OEM ROMs throw where AOSP doesn't.
  Don't "fix" these into rethrows.
- **Singletons** use `companion getInstance(context)` with `@Volatile`
  double-checked locking (see `SpeedSettingsRepository`).
- **`SpeedFormatter`** (in `model/NetworkModels.kt`) is the single
  byte/speed formatting utility. Never format bytes inline.
- Comments explain **why**, with measured numbers and rejected alternatives.
  When changing behavior, update the rationale.
