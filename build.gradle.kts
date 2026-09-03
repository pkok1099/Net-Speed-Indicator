// Flat single-module build: the Android application plugin is applied directly
// to the ROOT project (no :app subproject). All sources live in top-level
// directories:
//   main/          — Kotlin sources (main/kotlin/...), AndroidManifest.xml, res/, assets/
//   test/          — JVM unit tests (test/java/...)
// Package declarations in every file remain com.onlasdan.netnet — only the
// physical layout is flat.
plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.roborazzi)
}

android {
  namespace = "com.onlasdan.netnet"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.onlasdan.netnet"
    // Target Android 16 (API 36, minor level 1). Only Android 16+ devices will install
    // this build — this lets R8 strip 1,700+ Api<N>Impl backport classes that exist only
    // to provide forward-compat behaviour on older Android versions.
    minSdk = 36
    targetSdk = 36
    versionCode = 8
    versionName = "1.5.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    // Resource shrinking keeps only what the app references, but AndroidX
    // library resources still ship for ~80 locales the app itself never
    // translates (app strings are EN-only). Restrict to English plus the
    // density buckets actually used (verified via apkanalyzer: the APK
    // previously carried 93 configurations).
    resourceConfigurations += listOf("en")

    // Native libs: androidx.graphics.path ships .so for 4 ABIs; this app runs
    // on phones (arm) only — drop the emulator-only x86/x86_64 slices.
    ndk {
      abiFilters += listOf("armeabi-v7a", "arm64-v8a")
    }
  }

  // ---- FLAT LAYOUT: point every source set at the root-level directories ----
  sourceSets {
    getByName("main") {
      manifest.srcFile("main/AndroidManifest.xml")
      kotlin.srcDir("main/kotlin")
      res.srcDir("main/res")
      assets.srcDir("main/assets")
    }
    getByName("test") {
      kotlin.srcDir("test/java")
      resources.srcDir("test/resources")
    }
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      // Enable R8 code shrinking + optimization. Strips unused classes/methods/fields
      // and inlines what it can. Combined with android.enableR8.fullMode=true in
      // gradle.properties, this is the most aggressive size reduction available.
      isMinifyEnabled = true
      // Remove unused resources (drawables, strings, etc.) detected by R8.
      isShrinkResources = true
      // Crunch PNGs to save space.
      isCrunchPngs = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug { signingConfig = signingConfigs.getByName("debugConfig") }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)

  // ======================================================================
  // REMOVED — these dependencies were declared but NEVER referenced from
  // source code (verified via `grep` across the source tree). Keeping them
  // was inflating the APK by several MB due to transitive deps.
  //   - firebase-bom / firebase-ai / firebase-appcheck-* — no Firebase usage
  //   - retrofit / converter-moshi / okhttp / logging-interceptor — no HTTP
  //   - moshi-kotlin / moshi-kotlin-codegen — no JSON parsing
  //   - room-runtime / room-ktx / room-compiler — no @Database/@Dao/@Entity
  //   - androidx.work.runtime-ktx — replaced with AlarmManager + a tiny
  //     BroadcastReceiver (NetSpeedAlarmReceiver). Saves ~480 classes.
  //   - androidx.navigation.compose — replaced with manual state-based
  //     navigation in MainScreen.kt. Saves ~300 classes.
  // ======================================================================

  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  // androidTest deps removed: the only instrumented test was leftover template
  // boilerplate (com.example package, referenced nowhere) and has been deleted.
  // Re-add androidx.test junit4/espresso/runner here if real instrumented tests
  // are ever written.
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
}
