package com.onlasdan.netnet.notification

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Icon
import com.onlasdan.netnet.model.DisplayMode
import com.onlasdan.netnet.model.NotificationColorTheme
import com.onlasdan.netnet.model.NotificationIconScale
import com.onlasdan.netnet.model.SpeedSnapshot
import com.onlasdan.netnet.model.SpeedUnit
import java.util.Locale

object DynamicSpeedIconRenderer {
    private const val ICON_SIZE = 96

    // High contrast stroke paint for readability against light or variable status bars
    private val strokePaint by lazy {
        Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 4.5f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = Color.argb(220, 0, 0, 0)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
    }

    private val numberPaint by lazy {
        Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            color = Color.WHITE
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
    }

    private val unitPaint by lazy {
        Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            color = Color.WHITE
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
    }

    @Volatile private var cachedKey: String? = null
    @Volatile private var cachedIcon: Icon? = null

    @Synchronized
    fun createSpeedIcon(
        snapshot: SpeedSnapshot,
        displayMode: DisplayMode,
        speedUnit: SpeedUnit,
        colorTheme: NotificationColorTheme = NotificationColorTheme.CYAN,
        isPaused: Boolean = false,
        isIdle: Boolean = false,
        iconScale: NotificationIconScale = NotificationIconScale.NORMAL
    ): Icon {
        val targetBytes = when (displayMode) {
            DisplayMode.BOTH -> snapshot.downloadBytesPerSec + snapshot.uploadBytesPerSec
            DisplayMode.DOWNLOAD_ONLY -> snapshot.downloadBytesPerSec
            DisplayMode.UPLOAD_ONLY -> snapshot.uploadBytesPerSec
            DisplayMode.AUTO_HIGHEST -> maxOf(snapshot.downloadBytesPerSec, snapshot.uploadBytesPerSec)
        }

        val (valueStr, unitStr) = if (isPaused) {
            Pair("PAUSE", "OFF")
        } else if (isIdle) {
            Pair("0", if (speedUnit == SpeedUnit.BYTES) "K/s" else "Kbps")
        } else {
            getSpeedComponents(targetBytes, speedUnit)
        }

        val cacheKey = "$valueStr|$unitStr|${colorTheme.name}|$isPaused|$isIdle|${iconScale.name}"
        val existing = cachedIcon
        if (existing != null && cacheKey == cachedKey) {
            return existing
        }

        return try {
            val bitmap = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val textColor = when (colorTheme) {
                NotificationColorTheme.MONOCHROME -> Color.WHITE
                NotificationColorTheme.SYSTEM -> Color.WHITE
                else -> colorTheme.colorInt
            }

            numberPaint.color = textColor
            unitPaint.color = textColor

            // Save the canvas, apply the user-selected scale, then draw the text so
            // SMALL / NORMAL / LARGE all share the same code path with different sizes.
            canvas.save()
            canvas.scale(iconScale.scale, iconScale.scale, ICON_SIZE / 2f, ICON_SIZE / 2f)

            if (isPaused) {
                drawCenteredText(canvas, "PAUSE", "OFF")
            } else if (isIdle) {
                drawCenteredText(canvas, "0", if (speedUnit == SpeedUnit.BYTES) "K/s" else "Kbps")
            } else {
                drawSpeed(canvas, valueStr, unitStr)
            }

            canvas.restore()

            val createdIcon = Icon.createWithBitmap(bitmap)
            cachedKey = cacheKey
            cachedIcon = createdIcon
            createdIcon
        } catch (_: Throwable) {
            existing ?: Icon.createWithBitmap(Bitmap.createBitmap(1, 1, Bitmap.Config.ALPHA_8))
        }
    }

    fun getSpeedComponents(bytesPerSec: Long, unit: SpeedUnit): Pair<String, String> {
        if (bytesPerSec <= 0) {
            return Pair("0", if (unit == SpeedUnit.BYTES) "K/s" else "Kbps")
        }

        return if (unit == SpeedUnit.BYTES) {
            val kb = bytesPerSec / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0

            when {
                gb >= 10.0 -> Pair(String.format(Locale.US, "%.0f", gb), "G/s")
                gb >= 1.0 -> Pair(String.format(Locale.US, "%.1f", gb), "G/s")
                mb >= 100.0 -> Pair(String.format(Locale.US, "%.0f", mb), "M/s")
                mb >= 10.0 -> Pair(String.format(Locale.US, "%.0f", mb), "M/s")
                mb >= 1.0 -> Pair(String.format(Locale.US, "%.1f", mb), "M/s")
                kb >= 100.0 -> Pair(String.format(Locale.US, "%.0f", kb), "K/s")
                kb >= 10.0 -> Pair(String.format(Locale.US, "%.0f", kb), "K/s")
                kb >= 1.0 -> Pair(String.format(Locale.US, "%.1f", kb), "K/s")
                else -> Pair(String.format(Locale.US, "%d", bytesPerSec), "B/s")
            }
        } else {
            val bits = bytesPerSec * 8
            val kb = bits / 1000.0
            val mb = kb / 1000.0
            val gb = mb / 1000.0

            when {
                gb >= 10.0 -> Pair(String.format(Locale.US, "%.0f", gb), "Gbps")
                gb >= 1.0 -> Pair(String.format(Locale.US, "%.1f", gb), "Gbps")
                mb >= 100.0 -> Pair(String.format(Locale.US, "%.0f", mb), "Mbps")
                mb >= 10.0 -> Pair(String.format(Locale.US, "%.0f", mb), "Mbps")
                mb >= 1.0 -> Pair(String.format(Locale.US, "%.1f", mb), "Mbps")
                kb >= 100.0 -> Pair(String.format(Locale.US, "%.0f", kb), "Kbps")
                kb >= 10.0 -> Pair(String.format(Locale.US, "%.0f", kb), "Kbps")
                kb >= 1.0 -> Pair(String.format(Locale.US, "%.1f", kb), "Kbps")
                else -> Pair(String.format(Locale.US, "%d", bits), "bps")
            }
        }
    }

    private fun drawSpeed(canvas: Canvas, value: String, unit: String) {
        val cx = ICON_SIZE / 2f

        val numSize = when {
            value.length >= 4 -> 42f
            value.length == 3 -> 48f
            else -> 54f
        }
        val unitSize = if (unit.length > 3) 22f else 26f

        numberPaint.textSize = numSize
        unitPaint.textSize = unitSize
        strokePaint.textSize = numSize

        val numY = 46f
        val unitY = 82f

        // 1. Draw outline stroke for high contrast readability
        strokePaint.textSize = numSize
        canvas.drawText(value, cx, numY, strokePaint)
        strokePaint.textSize = unitSize
        canvas.drawText(unit, cx, unitY, strokePaint)

        // 2. Draw crisp fill text
        canvas.drawText(value, cx, numY, numberPaint)
        canvas.drawText(unit, cx, unitY, unitPaint)
    }

    private fun drawCenteredText(canvas: Canvas, line1: String, line2: String) {
        val cx = ICON_SIZE / 2f
        numberPaint.textSize = 44f
        unitPaint.textSize = 26f
        val numY = 46f
        val unitY = 82f

        // Draw outline stroke
        strokePaint.textSize = 44f
        canvas.drawText(line1, cx, numY, strokePaint)
        strokePaint.textSize = 26f
        canvas.drawText(line2, cx, unitY, strokePaint)

        // Draw fill
        canvas.drawText(line1, cx, numY, numberPaint)
        canvas.drawText(line2, cx, unitY, unitPaint)
    }
}
