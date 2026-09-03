# Building

## Layout

The build is a **flat single-module** project: the Android application plugin
is applied to the root project (`build.gradle.kts` at the repo root, no `:app`
subproject). Source sets are pointed at root-level directories:

| Source set | Directories |
|---|---|
| main | `main/AndroidManifest.xml`, `main/kotlin`, `main/res`, `main/assets` |
| test | `test/java`, `test/resources` |

Package declarations remain `com.onlasdan.netnet` — only the physical layout
is flat. `applicationId` matches the code namespace
(`com.onlasdan.netnet`).

## Commands

```bash
./gradlew assembleDebug      # → build/outputs/apk/debug/
./gradlew assembleRelease    # → build/outputs/apk/release/
./gradlew test               # JVM unit tests
./gradlew lint               # Android lint
```

## Toolchain

- Gradle wrapper 9.3.1, AGP 9.1.1, Kotlin 2.2.10, Compose BOM 2024.09.00
  (version catalog: `gradle/libs.versions.toml`)
- `compileSdk` 36 with minor level 1; `minSdk = targetSdk = 36`
- Java 11 source/target compatibility
- Debug and release builds sign with explicit keystores (see Signing below);
  no cloud build system is involved.

## Constrained environments

`gradle.properties` intentionally serializes the build:

- `org.gradle.workers.max=1` and in-process Kotlin compilation
  (`kotlin.compiler.execution.strategy=in-process`) avoid daemon/worker
  failures in constrained environments.
- `org.gradle.parallel=false`, configuration cache + build cache on.

Don't "optimize" these back to parallel without testing in the target CI
environment.

## Signing

| Build | Keystore | Credentials |
|---|---|---|
| debug | `${rootDir}/debug.keystore` (gitignored — create if missing) | `android` / `androiddebugkey` |
| release | `KEYSTORE_PATH` env var, default `${rootDir}/my-upload-key.jks` | `STORE_PASSWORD`, `KEY_PASSWORD` env vars, alias `upload` |

Generate a debug keystore if absent:

```bash
keytool -genkeypair -v -keystore debug.keystore \
  -storepass android -alias androiddebugkey -keypass android \
  -keyalg RSA -keysize 2048 -validity 10000
```

## CI/CD (GitHub Actions)

Two workflows live in `.github/workflows/`:

| Workflow | Trigger | Steps |
|---|---|---|
| `ci.yml` | push to `main`, PRs | unit tests → lint → `assembleDebug` → APK artifact |
| `release.yml` | tag `v*` or manual | tests → signed `assembleRelease` → signature verify → GitHub Release with APK |

Release signing reads from repository **secrets** (Settings → Secrets and
variables → Actions):

| Secret | Content |
|---|---|
| `KEYSTORE_BASE64` | base64 of the upload keystore (`base64 -w0 my-upload-key.jks`) |
| `STORE_PASSWORD` | keystore store password |
| `KEY_PASSWORD` | key password (alias `upload`) |

The keystore never lives in the repo — all `*.jks` / `*.keystore` files are
gitignored. CI uses JDK 17 (Temurin) via `setup-java` + the official Gradle
action; the Android SDK comes preinstalled on `ubuntu-latest` runners.

## Why minSdk 36 (do not lower)

Android 16-only lets R8 strip 1,700+ `Api<N>Impl` forward-compat classes that
exist only to provide backported behavior on older Android versions, which
significantly shrinks the APK. Never add version guards for older APIs — they
are dead code in this app. Robolectric tests run `@Config(sdk=[36])`;
that must match the app manifest, else the framework jar cannot parse it.

## APK size optimizations (all deliberate)

In `build.gradle.kts` / `gradle.properties`:

- **R8 full mode** (`android.enableR8.fullMode=true`) + `isMinifyEnabled` +
  `isShrinkResources` on release; PNG crunching on.
- **`resourceConfigurations += listOf("en")`** — app strings are English-only;
  drops ~80 AndroidX locales the app never translates. Do not add locale
  resources.
- **`abiFilters`: `armeabi-v7a`, `arm64-v8a`** — phones only; drops
  emulator-only x86 slices from `androidx.graphics.path`.
- **`dependenciesInfo.includeInApk = false`** — no Play asset metadata in the
  APK.
- **`proguard-rules.pro` is intentionally minimal** — no reflection on app
  classes exists, so nothing needs extra keeps. Read its header comment
  before adding rules. Don't re-add the four legacy ProGuard options;
  they're unrecognized by the bundled R8.

## Dependency minimalism is a design principle

Large libraries were deliberately removed and replaced with hand-rolled
equivalents (see the big "REMOVED" comment block in `build.gradle.kts`).
Do not re-add without strong justification:

| Removed | Replaced by |
|---|---|
| `androidx.work` (WorkManager, ~480 classes) | `AlarmManager` + `work/NetSpeedAlarmReceiver.kt` |
| `androidx.navigation.compose` (~300 classes) | manual state-based nav in `ui/MainScreen.kt` |
| Room, Retrofit, OkHttp, Moshi, Firebase | not used at all — no persistence/HTTP/AI features exist |

Kept dependencies: Compose BOM (ui, material3, material-icons,
tooling-preview), activity-compose, lifecycle (runtime/viewmodel/runtime-compose),
core-ktx, kotlinx-coroutines. Test-only: JUnit4, Robolectric, coroutines-test,
Roborazzi (configured but no screenshot tests yet), compose-ui-test-junit4.

## Versioning

SemVer `versionName` + Conventional Commits in `build.gradle.kts`
(`versionCode` +1 on every versionName change):

- fix → PATCH, `fix:`
- feature → MINOR, `feat:`
- breaking → MAJOR, `feat!:` / `BREAKING CHANGE:`
- internal refactors/docs/chore → no bump

## Static analysis

`config/detekt/detekt.yml` + `baseline.xml` exist but detekt is **not wired
into Gradle**. Run the detekt CLI manually against the config if enforcing;
`baseline.xml` suppresses existing findings in legacy composables. Notable
thresholds: LongMethod 60, CyclomaticComplexMethod 15, TooManyFunctions 11,
NestedBlockDepth 4.
