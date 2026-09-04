# Net Speed Indicator

Real-time network speed monitor for **Android 16+** (API 36). Measures live
download/upload speed straight from the kernel's traffic counters and surfaces
it everywhere Android lets you: a persistent status-bar notification with the
Android 16 *promoted* status-bar chip, a home-screen widget, a draggable
floating bubble overlay, and a Quick Settings tile.

```
NetSpeedApp (Application: crash log handler, channel, alarm scheduling on boot)
   └─ MainActivity → MainViewModel (AndroidViewModel) → Compose UI
                         │
   Singletons (all via thread-safe getInstance(context)):
   ├─ TrafficMonitor          — sampling engine; emits snapshot/history StateFlows
   ├─ SpeedSettingsRepository — SharedPreferences-backed SpeedSettings StateFlow
   │                            + daily usage records + today's byte counters
   └─ NetSpeedForegroundService — owns the persistent speed notification
                                 (static isRunning/isPausedState StateFlows)
```

## Features

- **Live speed measurement** — per-second sampling of `TrafficStats` counters
  with sawtooth-cancellation math (see [docs/traffic-monitoring.md]), so the
  displayed curve is flat during a steady transfer instead of oscillating.
- **Promoted status-bar chip (Android 16)** — the notification requests
  `setRequestPromotedOngoing`, so a compact "↓2.4M" pill lives directly in the
  status bar.
- **Dynamic speed icon** — the status-bar icon is a rendered bitmap of the
  current speed ("2.4M"); selectable static icons (gauge, arrows, signal, dot)
  and three icon sizes are also available.
- **Home-screen widget** — shows live speeds; tap-to-refresh and a
  service-start/stop toggle button.
- **Floating bubble overlay** — a draggable, expandable speed bubble rendered
  via `WindowManager` + Compose (`SYSTEM_ALERT_WINDOW`).
- **Quick Settings tile** — starts/stops/pauses the monitor; shows live speed
  as the tile subtitle.
- **Data usage analytics** — per-day Wi-Fi vs cellular totals with 7/30-day
  history, session peaks, and an optional daily summary notification.
- **Network diagnostics** — ping ladder (gateway → DNS → internet), latency,
  jitter, live speed-burst test, and a one-time full audit with
  download/upload benchmarks.
- **Battery-aware** — screen-off auto-pause or low-frequency sampling,
  adaptive idle sampling (1s → 5s after ~30 silent ticks), low-battery
  cadence stretch, Doze-friendly self-rescheduling alarms.
- **Tiny APK** — R8 full mode + resource shrinking + ARM-only ABIs +
  English-only resources. WorkManager, Navigation-Compose, Room, Retrofit,
  OkHttp, Moshi and Firebase were all deliberately removed (see
  [docs/building.md]).

## Requirements

| Tool | Version |
|---|---|
| Android SDK | Platform 36 (Android 16) |
| JDK | 21+ (Robolectric SDK 36 sandbox requires Java 21) |
| Gradle wrapper | 9.3.1 (bundled) |
| AGP / Kotlin | 9.1.1 / 2.2.10 |
| Device | Android 16+ only (`minSdk = 36`) — deliberate, see [docs/building.md] |

## Build & test

```bash
./gradlew assembleDebug      # debug APK → build/outputs/apk/debug/
./gradlew assembleRelease    # release APK — needs signing env vars (below)
./gradlew test               # JVM unit tests (JUnit4 + Robolectric)
./gradlew connectedAndroidTest  # instrumented tests (needs a device)
./gradlew lint               # Android lint
```

Debug builds sign with `debug.keystore` in the repo root (gitignored — create
it if missing; password/alias `android`/`androiddebugkey`).

Release builds read signing from the environment:

```bash
export KEYSTORE_PATH=/path/to/upload-key.jks   # default: ./my-upload-key.jks
export STORE_PASSWORD=...
export KEY_PASSWORD=...
```

More detail in [docs/building.md] and [docs/testing.md].

## Project structure

Single Gradle module in a **flat layout** — the app plugin is applied to the
root project and sources live in top-level directories:

```
├── main/
│   ├── AndroidManifest.xml
│   ├── kotlin/com/onlasdan/netnet/
│   │   ├── NetSpeedApp.kt          # Application: crash log, channel, alarms
│   │   ├── MainActivity.kt
│   │   ├── data/                   # SpeedSettingsRepository (prefs + usage)
│   │   ├── model/                  # NetworkModels, SpeedFormatter, enums
│   │   ├── monitor/                # TrafficMonitor + diagnostics runners
│   │   ├── notification/           # NotificationHelper, dynamic icon renderer
│   │   ├── receiver/               # BootReceiver
│   │   ├── service/                # FGS, floating bubble, QS tile
│   │   ├── ui/                     # Compose UI (screens, components, nav)
│   │   ├── widget/                  # NetSpeedWidgetProvider
│   │   └── work/                   # AlarmManager helper + receiver
│   ├── res/                        # drawables, widget layouts, strings (EN)
│   └── assets/                     # contributors.json, icon credits
├── test/java/                      # JVM/Robolectric unit tests
├── build.gradle.kts                # single module build config
├── proguard-rules.pro              # minimal, heavily commented
├── config/detekt/                  # detekt.yml + baseline (not wired into Gradle)
└── gradle/libs.versions.toml       # version catalog
```

## Documentation

| Doc | Contents |
|---|---|
| [docs/architecture.md](docs/architecture.md) | Component map, StateFlow wiring, navigation, background keep-alive |
| [docs/traffic-monitoring.md](docs/traffic-monitoring.md) | Sampling engine, counter bases, VPN handling, sawtooth cancellation |
| [docs/notifications.md](docs/notifications.md) | Persistent notification, Android 16 promoted chip, idle throttling |
| [docs/widgets-and-overlays.md](docs/widgets-and-overlays.md) | Home-screen widget, floating bubble, Quick Settings tile |
| [docs/data-usage.md](docs/data-usage.md) | Daily counters, flush policy, history analytics, daily summary |
| [docs/diagnostics.md](docs/diagnostics.md) | Ping ladder, jitter, speed test, one-time full diagnostic |
| [docs/settings.md](docs/settings.md) | Every setting, its default and what it actually does |
| [docs/building.md](docs/building.md) | Build types, signing, size-optimization rationale |
| [docs/testing.md](docs/testing.md) | Test layout, conventions, running tests |

## Versioning

SemVer (`versionName`) + Conventional Commits. `versionCode` increments by
exactly +1 whenever `versionName` changes. Bump in `build.gradle.kts` on any
commit that will be released; internal refactors don't bump.

## License notes

Icon credits are in `main/assets/icon_credits.html`.
