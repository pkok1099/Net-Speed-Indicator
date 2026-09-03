# Widgets & overlays

Three surfaces outside the app UI show live speed: the home-screen widget,
the floating bubble, and the Quick Settings tile. All read the same
`TrafficMonitor.snapshot` / settings flows — there is one source of truth.

## Home-screen widget (`widget/NetSpeedWidgetProvider.kt`)

- Standard `AppWidgetProvider` (RemoteViews; layout in `res/layout/`,
  metadata `res/xml/net_speed_widget_info.xml`).
- Shows live download/upload speeds via `SpeedFormatter`, the network type,
  and service running/paused state.
- Updates are **pushed** by `NetSpeedForegroundService`
  (`NetSpeedWidgetProvider.updateAllWidgets(...)`) on every notification
  refresh, so the widget cadence rides the notification cadence (including
  idle throttling).
- Handles custom broadcast actions:
  - `com.onlasdan.netnet.action.WIDGET_REFRESH` — tap-to-refresh button
    (reads the current snapshot on a coroutine and repaints)
  - `com.onlasdan.netnet.action.WIDGET_TOGGLE_SERVICE` — starts/stops the
    foreground service
- No periodic `AlarmManager` polling of its own — a widget-only refresh
  loop would waste battery duplicating what the service already pushes.

## Floating bubble (`service/FloatingBubbleService.kt`)

- A `Service` that adds a Compose `ComposeView` via `WindowManager` with
  `TYPE_APPLICATION_OVERLAY` (falling back to `TYPE_PHONE`).
- Requires the `SYSTEM_ALERT_WINDOW` permission — the UI routes the user to
  the overlay settings screen when needed.
- Draggable anywhere (drag gestures update `WindowManager.LayoutParams` x/y),
  expandable to a two-line speed panel, closable via an X button. Position
  is not persisted across sessions (it starts at a default spot).
- Static companion `isFloatingActive` StateFlow so the dashboard card and
  ViewModel can show bubble state without binding.
- Started/stopped from the Dashboard's Floating Bubble card.

## Quick Settings tile (`service/SpeedTileService.kt`)

- A `TileService` (label "Net Speed") combining
  `isRunning` + `isPausedState` + snapshot + settings flows.
- Reflects state in tile label/subtitle/state: Stopped → tap to start,
  Paused → tap to resume, Running → shows live speed subtitle.
- Tile icon follows the user's `NotificationIconStyle` choice.
- Tap action starts/stops/pauses the foreground service, so users get
  one-swipe control over the monitor.
