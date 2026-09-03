package com.onlasdan.netnet

import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import com.onlasdan.netnet.data.SpeedSettingsRepository
import com.onlasdan.netnet.notification.NotificationHelper
import com.onlasdan.netnet.work.NetSpeedWorkManagerHelper
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NetSpeedApp : Application() {

    override fun onCreate() {
        super.onCreate()
        setupCrashLogging()

        try {
            NotificationHelper.createNotificationChannel(this)
        } catch (_: Throwable) {}

        try {
            val settings = SpeedSettingsRepository.getInstance(this).settings.value
            if (settings.isServiceEnabled) {
                NetSpeedWorkManagerHelper.schedulePeriodicWatchdog(this)
            }
            if (settings.isDailySummaryEnabled) {
                NetSpeedWorkManagerHelper.scheduleDailySummaryWorker(this)
            }
        } catch (_: Throwable) {}
    }

    private fun setupCrashLogging() {
        try {
            val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                try {
                    saveCrashLog(this, thread, throwable)
                } catch (e: Throwable) {
                    Log.e(TAG, "Failed to write crash log to disk", e)
                } finally {
                    Log.e(TAG, "Uncaught exception on thread [${thread.name}]: ${throwable.message}", throwable)
                    defaultHandler?.uncaughtException(thread, throwable)
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to register uncaught exception handler", e)
        }
    }

    companion object {
        private const val TAG = "NetSpeedApp"
        private const val CRASH_LOG_FILE = "startup_crash.log"
        private const val MAX_LOG_SIZE_BYTES = 512 * 1024L // 512 KB limit

        private fun getLogDirectory(context: Context): File {
            val externalDir = context.getExternalFilesDir(null)
            val baseDir = externalDir ?: context.filesDir
            if (!baseDir.exists()) {
                baseDir.mkdirs()
            }
            return baseDir
        }

        /**
         * Writes crash details to a local log file in Android/data/<package>/files/
         */
        fun saveCrashLog(context: Context, thread: Thread, throwable: Throwable) {
            val logDir = getLogDirectory(context)
            val logFile = File(logDir, CRASH_LOG_FILE)
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())

            val sw = StringWriter()
            val pw = PrintWriter(sw)
            throwable.printStackTrace(pw)
            val stackTraceString = sw.toString()

            val crashReport = buildString {
                appendLine("==================================================")
                appendLine("CRASH TIMESTAMP: $timestamp")
                appendLine("THREAD: ${thread.name} (ID: ${thread.id})")
                appendLine("EXCEPTION: ${throwable.javaClass.name}")
                appendLine("MESSAGE: ${throwable.message}")
                appendLine("DEVICE: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
                appendLine("ANDROID SDK: ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})")
                appendLine("APP VERSION: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                appendLine("DEBUG BUILD: ${BuildConfig.DEBUG}")
                appendLine("STORAGE PATH: ${logFile.absolutePath}")
                appendLine("STACKTRACE:")
                appendLine(stackTraceString)
                appendLine("==================================================")
                appendLine()
            }

            // Append crash log (rotate if file exceeds max size)
            if (logFile.exists() && logFile.length() > MAX_LOG_SIZE_BYTES) {
                logFile.delete()
            }

            logFile.appendText(crashReport)
            Log.i(TAG, "Crash report recorded to: ${logFile.absolutePath}")
        }

        /**
         * Reads the contents of the crash log file if it exists.
         */
        fun readCrashLog(context: Context): String? {
            return try {
                val logFile = getCrashLogFile(context)
                if (logFile.exists() && logFile.length() > 0) {
                    logFile.readText()
                } else {
                    null
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to read crash log file", e)
                null
            }
        }

        /**
         * Clears the crash log file.
         */
        fun clearCrashLog(context: Context): Boolean {
            return try {
                val logFile = getCrashLogFile(context)
                if (logFile.exists()) logFile.delete() else true
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to clear crash log file", e)
                false
            }
        }

        /**
         * Returns the File handle for the crash log in Android/data/<package>/files/
         */
        fun getCrashLogFile(context: Context): File {
            return File(getLogDirectory(context), CRASH_LOG_FILE)
        }
    }
}
