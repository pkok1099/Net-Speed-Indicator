# =========================================================================
# R8 rules for Net Speed Indicator (com.onlasdan.netnet)
# =========================================================================
# Context (verified against this project, not generic advice):
#   AGP 9.1.1 / Gradle 9.3.1 / Kotlin 2.2.10
#   android.enableR8.fullMode=true (gradle.properties)
#   isMinifyEnabled=true, isShrinkResources=true (app/build.gradle.kts)
#   minSdk=36, targetSdk=36
#   R8 binary: R8_8.9.27 (bundled with this AGP)
#
# Principles applied:
#   * AGP already injects its default rule set (proguard-android-optimize.txt)
#     on every release build: it keeps manifest-referenced components
#     (services, receivers, activities, providers), enum values()/valueOf(),
#     View/Parcelable constructors, and other Android framework contracts.
#     Rules below only ADD what is project-specific; default-mirroring rules
#     were deleted.
#   * No -keep blanket rules: a full grep of app source found reflection
#     ONLY on framework classes (Notification.Builder, NotificationManager —
#     see NotificationHelper.kt), never on app classes. Therefore every app
#     class is safe to shrink, obfuscate, and repackage.
#   * The 4 legacy ProGuard options that the previous file used
#     (-optimizationpasses, -overloadaggressively, -mergeinterfacesaggressively,
#     -dontpreverify) were REMOVED: disassembling the bundled R8 8.9.27 parser
#     shows -optimizationpasses/-optimizations only emit an "Unsupported
#     option" diagnostic, and the other three are not even recognized by the
#     parser (no-op). -allowaccessmodification is already enabled by default
#     in R8 full mode.
#   * Keep rules for Moshi / Retrofit / OkHttp / Room / Firebase were REMOVED
#     entirely (not just -dontwarn): none of those libraries are declared in
#     app/build.gradle.kts (see the "REMOVED" dependency block there).
# =========================================================================

# -------------------------------------------------------------------------
# 1. Manifest-instantiated components.
#    AGP keeps these automatically, but explicit keeps make the contract
#    obvious and survive rule-file reshuffling. Every entry below matches an
#    android:name in AndroidManifest.xml:
#      .NetSpeedApp, .MainActivity,
#      .service.NetSpeedForegroundService, .service.FloatingBubbleService,
#      .service.SpeedTileService (Quick Settings tile, instantiated by SystemUI),
#      .receiver.BootReceiver, .work.NetSpeedAlarmReceiver,
#      .widget.NetSpeedWidgetProvider (AppWidget host instantiates by class
#      name from the APPWIDGET_UPDATE broadcast).
# -------------------------------------------------------------------------
-keep class com.onlasdan.netnet.NetSpeedApp { *; }
-keep class com.onlasdan.netnet.MainActivity { *; }
-keep class com.onlasdan.netnet.service.NetSpeedForegroundService { *; }
-keep class com.onlasdan.netnet.service.FloatingBubbleService { *; }
-keep class com.onlasdan.netnet.service.SpeedTileService { *; }
-keep class com.onlasdan.netnet.receiver.BootReceiver { *; }
-keep class com.onlasdan.netnet.work.NetSpeedAlarmReceiver { *; }
-keep class com.onlasdan.netnet.widget.NetSpeedWidgetProvider { *; }

# The widget renders RemoteViews across process boundaries; its nested
# companion object is referenced from the host process keep-alive path.
# (Covered by the class keep above — noted for clarity only.)

# -------------------------------------------------------------------------
# 2. Enums loaded by NAME from SharedPreferences.
#    SpeedSettingsRepository.loadSettings() parses persisted strings back
#    into enums via AppThemeMode.valueOf(...), SpeedUnit.valueOf(...),
#    DisplayMode, NotificationColorTheme, NotificationIconStyle,
#    NotificationIconScale, StatusBarChipSize (7 enums — verified in
#    SpeedSettingsRepository.kt).
#
#    Enum.valueOf resolves constants by FIELD NAME via Enum.valueOf's
#    reflective lookup. The stock AGP enum rule keeps only the methods —
#    with constant FIELDS renamed (e.g. AppThemeMode.SYSTEM -> "f"),
#    valueOf("SYSTEM") from old persisted prefs throws and every call site
#    falls into its catch-default: existing users would silently lose all
#    saved preferences on upgrade. <fields> pins the constant names.
#    (Verified against the first release build's dex: without <fields>,
#    SpeedUnit.BYTES was emitted as "e" — regression caught in audit.)
# -------------------------------------------------------------------------
-keepclassmembers enum com.onlasdan.netnet.** {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# -------------------------------------------------------------------------
# 3. Crash-log readability.
#    NetSpeedApp.saveCrashLog() writes the full stack trace to a local file
#    and logs it; without SourceFile/LineNumberTable every released crash
#    trace is unusable until retrace-mapped. Keep these two attributes
#    (only these — Signature/InnerClasses/EnclosingMethod are needed by
#    R8 itself and are retained automatically where required).
# -------------------------------------------------------------------------
-keepattributes SourceFile, LineNumberTable

# -------------------------------------------------------------------------
# 4. Coroutines debug-agent class is referenced only when the debug agent
#    is installed; it is absent from release classpath and produces a
#    warning otherwise. (Consumer rules shipped inside kotlinx-coroutines
#    core JARs handle everything else — no -keep kotlinx.coroutines.** here;
#    that blanket keep would have prevented shrinking the state machines.)
# -------------------------------------------------------------------------
-dontwarn kotlinx.coroutines.debug.**

# -------------------------------------------------------------------------
# 5. Repackaging.
#    No code anywhere reflects on app class names (grep: no Class.forName,
#    no loadClass, no serialization frameworks), so every remaining app
#    class after shrinking can move to the root (unnamed) package. This
#    maximizes class-name sharing in the dex string pool — the single
#    biggest APK size win available for this codebase.
#    Keep mapping.txt (default) for symbolication of crash reports.
# -------------------------------------------------------------------------
-repackageclasses ''

# =========================================================================
# DELETED (with reasons — do not re-add without re-auditing):
#  * -keep class com.onlasdan.netnet.** { *; }
#      -> Kept ~60 classes + all members from shrinking/obfuscation for no
#         benefit: no app class is accessed reflectively.
#  * -keep class kotlin.Metadata { *; } + @kotlin.Metadata keepclassmembers
#      -> No Kotlin-reflection library in the dependency tree. Keeping all
#         Kotlin metadata inflates the dex significantly.
#  * -keep kotlinx.coroutines.** / kotlin.coroutines.**
#      -> Consumer rules ship in the coroutines JARs; blanket keep blocked
#         removal of unused coroutine machinery.
#  * -keep androidx.compose.runtime/ui/foundation/material3/animation/**
#      -> Compose artifacts bundle their own R8 consumer rules. Blanket
#         keeps prevented stripping unused Compose code AND blocked the
#         removal of ~thousands of unreferenced material-icons-extended
#         classes (the explicit goal of this build config).
#  * Moshi / OkHttp / Retrofit / Room / Firebase sections
#      -> Libraries are not in the dependency list at all (build.gradle.kts
#         documents their removal). Rules were pure dead weight.
#  * -keepclassmembers android.app.Notification$Builder/Notification
#      -> Framework classes, not processed by R8 — keep rules on them are
#         no-ops. The reflection in NotificationHelper targets the platform
#         at runtime, unaffected by shrinking.
#  * View/Parcelable/main() safe-keeping blocks
#      -> Duplicates AGP's injected defaults.
#  * -optimizationpasses / -overloadaggressively /
#    -mergeinterfacesaggressively / -dontpreverify / -allowaccessmodification
#      -> No-ops or defaults (see header). Verified against the R8 8.9.27
#         binary shipped with this AGP version.
#  * -dontwarn blocks for androidx.datastore / android.support /
#    androidx.work / androidx.navigation / firebase / moshi / okhttp / etc.
#      -> Referenced classes are not on the classpath; no warnings occur.
# =========================================================================
