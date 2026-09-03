# Notifications

`notification/NotificationHelper.kt` builds every notification. Two channels:

| Channel | ID | Purpose |
|---|---|---|
| Speed indicator | `net_speed_indicator_channel` | the persistent FGS notification |
| Daily summary | `net_speed_daily_summary_channel` | the daily data-usage alert |

## The persistent speed notification

Owned by `NetSpeedForegroundService`, rebuilt on every snapshot tick
(conflated). Properties:

- `IMPORTANCE_LOW`, silent, no badge — it's an indicator, not an alert.
- No `BigTextStyle`, no actions, no progress bar: any of those disqualify a
  notification from being **promoted** on Android 16.
- Never `setColorized(true)` — strictly disqualifies promotion.
- Tapping it deep-links into the app (`EXTRA_NAVIGATE_TARGET`).

### Minimal mode

`isMinimalNotificationEnabled` renders a textless shade notification — only
the small status-bar icon survives. This saves every per-tick text
relayout/post for users who only want the icon indicator.

### Hide-when-idle

`hideWhenIdle` swaps in a fully hidden (empty, promotion-disabled) variant
when traffic is below the idle threshold, so the notification disappears from
the shade while idle instead of showing "0 KB/s".

## Android 16 promoted status-bar chip

`Notification.Builder` is `final` on this compileSdk, so
`setRequestPromotedOngoing(true)` and `setShortCriticalText(...)` are invoked
via **cached reflection** (`Method` objects looked up once — the notification
rebuilds up to once per second, and reflective `getMethod` per tick is pure
waste).

The promoted chip ("stuck indicator" pill in the status bar) is capped at
96dp by the platform; the app offers two chip formats:

- `StatusBarChipSize.COMPACT` — just the value, e.g. `2.4M`
- `StatusBarChipSize.STANDARD` — arrow + value, e.g. `↓2.4M`

(A legacy `DETAILED` two-value chip exceeded the 96dp limit and rendered
clipped; stored prefs still holding it are migrated to `STANDARD`.)

When the chip is enabled, the small icon switches to a clean vector icon —
the dynamic-speed icon would render duplicate speed text in the chip
(`[1.2M] ↓1.2M`).

Compliance (does the notification technically qualify for promotion? does the
user allow promoted notifications?) is verified in Robolectric tests — see
`test/.../PromotedNotificationTest.kt`.

## Dynamic speed icon

`notification/DynamicSpeedIconRenderer.kt` renders the current speed ("2.4M")
into a bitmap used as the small icon. Icon style is user-selectable:

| `NotificationIconStyle` | Icon |
|---|---|
| `DYNAMIC_SPEED` | live speed number bitmap (default) |
| `SPEEDOMETER` | gauge |
| `ARROWS` | transfer arrows |
| `SIGNAL` | signal wave |
| `MINIMAL_DOT` | dot |

`NotificationIconScale` (0.80 / 1.00 / 1.25) sizes the status-bar icon; the
static vector icons are rendered as scaled bitmaps so they respect it too.

Color themes (`NotificationColorTheme`) set the icon/notification accent:
System dynamic, Electric Cyan, Sakura Pink, Emerald, Purple, Amber, Rose,
Monochrome.

## Idle throttling

The service does not rebuild the notification on every tick when nothing
interesting changed:

- Below `idleThresholdKbps`, notifications throttle to a **stuck-detector
  cadence** — every `stuckDetectorIntervalSec` (2–60 s, default 5 s; 10 s
  when the detector is off) — so a "frozen" 0 KB/s indicator still gets a
  periodic refresh kick and cannot get *stuck* showing stale numbers.
- `isThresholdFreezeEnabled` freezes below-threshold updates entirely.
- Settings-signature changes interrupt the throttle; speed-only changes
  below the threshold do not (they're already throttled).

## Daily summary notification

Built by `buildDailyUsageSummaryNotification`, posted by the daily-summary
alarm or the "send test alert" button. It summarizes today's usage
(optionally gated by a minimum-MB threshold), deep-links to the Data Usage
screen, and re-schedules itself for the next day. See
[docs/data-usage.md](data-usage.md).
