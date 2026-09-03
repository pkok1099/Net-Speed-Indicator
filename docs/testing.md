# Testing

## Layout

- Unit tests: `test/java/com/onlasdan/netnet/` — plain **JUnit4** with
  backtick test names.
- **Robolectric** (`@RunWith(RobolectricTestRunner::class)`,
  `@Config(sdk = [36])`) for anything touching Android framework classes
  (notification managers, receivers). The `@Config` level **must be 36**:
  the merged manifest carries `minSdkVersion=36`, and Robolectric's
  PackageParser rejects it against an older framework jar
  ("Requires newer sdk version #36"). Framework jars are test-time
  shadows, not install targets.
- `testOptions.unitTests.isIncludeAndroidResources = true` so Robolectric can
  resolve the app's real resources.
- Instrumented tests: no real ones exist — the leftover template boilerplate
  (`com.example` package) was deleted. `connectedAndroidTest` currently has
  nothing meaningful to run.
- Roborazzi (screenshot testing) dependencies and plugin are configured but
  **no screenshot tests currently exist**.

## Commands

```bash
./gradlew test               # all JVM unit tests
./gradlew test --tests "com.onlasdan.netnet.TrafficMonitorMathTest"   # one class
./gradlew connectedAndroidTest   # instrumented (needs device/emulator; currently empty)
```

## Current test files

| File | Covers |
|---|---|
| `TrafficMonitorMathTest.kt` | the pure math: `trafficRateBytesPerSec` real-window rates, invalid windows, `pairedRateBytesPerSec` sawtooth cancellation, `interfaceSetUnchanged`, `physicalIfaceWindow` flapping/baseline-replacement rules |
| `SpeedFormatterTest.kt` | `SpeedFormatter` byte/bit formatting across magnitudes |
| `PromotedNotificationTest.kt` | Android 16 promoted-notification compliance against real framework managers (Robolectric): channel setup, promotion characteristics, chip behavior |
| `PingDiagnosticTest.kt` | ping-ladder helpers / advice generation |
| `WorkManagerWatchdogTest.kt` | AlarmManager scheduling semantics of `NetSpeedWorkManagerHelper` (misnamed; pure AlarmManager now) |

## Conventions

- **Backtick test names** describing behavior, e.g.
  `` `short or invalid windows never calculate a rate` ``.
- **Extract-then-test**: pure logic is extracted into `internal` top-level
  functions (see TrafficMonitor's math functions) so JVM tests don't need
  Android at all. Prefer extending that pattern over mocking singletons.
- Coroutines tests use `kotlinx-coroutines-test`.
- No mocking framework for framework classes — use Robolectric's real
  shadows.
- Composables that are test targets get `testTag`s (defined in
  `ui/navigation/Screen.kt`, e.g. `nav_dashboard_tab`).

## Verifying on device

The app writes crash logs to
`Android/data/<package>/files/startup_crash.log` (512 KB rotate) via
`NetSpeedApp`'s uncaught-exception handler — useful when debugging failures
that only happen outside adb. Logcat tags of interest: `NetSpeedApp`,
`NetSpeedAlarm`, `NotificationHelper`, `NetSpeedWidget`.
