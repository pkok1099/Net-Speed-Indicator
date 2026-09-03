package com.onlasdan.netnet.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlasdan.netnet.model.SpeedFormatter
import com.onlasdan.netnet.model.SpeedPoint
import com.onlasdan.netnet.model.SpeedUnit
import com.onlasdan.netnet.ui.theme.AppTheme
import kotlin.math.max

@Composable
fun LiveSpeedGraph(
    history: List<SpeedPoint>,
    unit: SpeedUnit,
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors
    val maxSpeed = max(
        100 * 1024L,
        history.maxOfOrNull { max(it.downloadBytesPerSec, it.uploadBytesPerSec) } ?: 100 * 1024L
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceElevated)
            .border(1.dp, colors.cardBorder, RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        // Header & Legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "REAL-TIME TRAFFIC",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                letterSpacing = 1.sp
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Download legend
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(colors.accentPrimary, CircleShape)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "DL",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.accentGlow
                )

                Spacer(modifier = Modifier.width(10.dp))

                // Upload legend
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(colors.accentSecondary, CircleShape)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "UL",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.accentSecondaryGlow
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Canvas Graph
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
        ) {
            // Fill gradients depend only on the theme: remembered so the draw
            // lambda (re-executed per animation frame) does not re-allocate brushes.
            val ulFillBrush = remember(colors) {
                Brush.verticalGradient(
                    0.0f to colors.accentSecondary.copy(alpha = 0.25f),
                    1.0f to colors.accentSecondary.copy(alpha = 0.02f)
                )
            }
            val dlFillBrush = remember(colors) {
                Brush.verticalGradient(
                    0.0f to colors.accentPrimary.copy(alpha = 0.35f),
                    1.0f to colors.accentPrimary.copy(alpha = 0.02f)
                )
            }
            val gridLineColor = colors.chartGridLine
            val dashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f) }
            val dlPath = remember { Path() }
            val dlFillPath = remember { Path() }
            val ulPath = remember { Path() }
            val ulFillPath = remember { Path() }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // Draw Horizontal Grid lines (0%, 33%, 66%, 100%)
                val steps = 3
                for (i in 0..steps) {
                    val y = h * (i.toFloat() / steps)
                    drawLine(
                        color = gridLineColor,
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = dashEffect
                    )
                }

                if (history.size < 2) return@Canvas

                val stepX = w / 59f
                val startIndex = 60 - history.size

                // Reset reusable paths to avoid heap churn
                dlPath.reset()
                dlFillPath.reset()
                ulPath.reset()
                ulFillPath.reset()

                var firstPoint = true

                history.forEachIndexed { index, point ->
                    val actualIndex = startIndex + index
                    val x = actualIndex * stepX
                    val dlNormalized = (point.downloadBytesPerSec.toFloat() / maxSpeed).coerceIn(0f, 1f)
                    val ulNormalized = (point.uploadBytesPerSec.toFloat() / maxSpeed).coerceIn(0f, 1f)

                    val dlY = h - (dlNormalized * (h - 10.dp.toPx()))
                    val ulY = h - (ulNormalized * (h - 10.dp.toPx()))

                    if (firstPoint) {
                        dlPath.moveTo(x, dlY)
                        dlFillPath.moveTo(x, h)
                        dlFillPath.lineTo(x, dlY)

                        ulPath.moveTo(x, ulY)
                        ulFillPath.moveTo(x, h)
                        ulFillPath.lineTo(x, ulY)

                        firstPoint = false
                    } else {
                        dlPath.lineTo(x, dlY)
                        dlFillPath.lineTo(x, dlY)

                        ulPath.lineTo(x, ulY)
                        ulFillPath.lineTo(x, ulY)
                    }
                }

                val lastX = (startIndex + history.size - 1) * stepX
                dlFillPath.lineTo(lastX, h)
                dlFillPath.close()

                ulFillPath.lineTo(lastX, h)
                ulFillPath.close()

                // Draw Upload Fill and Line
                drawPath(
                    path = ulFillPath,
                    brush = ulFillBrush
                )
                drawPath(
                    path = ulPath,
                    color = colors.accentSecondary,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )

                // Draw Download Fill and Line
                drawPath(
                    path = dlFillPath,
                    brush = dlFillBrush
                )
                drawPath(
                    path = dlPath,
                    color = colors.accentPrimary,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )

                // Draw Current Point Glow Dot
                val lastPoint = history.last()
                val lastDlY = h - ((lastPoint.downloadBytesPerSec.toFloat() / maxSpeed).coerceIn(0f, 1f) * (h - 10.dp.toPx()))
                drawCircle(
                    color = colors.accentGlow,
                    radius = 4.dp.toPx(),
                    center = Offset(lastX, lastDlY)
                )
                drawCircle(
                    color = colors.accentPrimary,
                    radius = 2.dp.toPx(),
                    center = Offset(lastX, lastDlY)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Scale indicators
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "0 B/s • 60s",
                fontSize = 9.sp,
                color = colors.textTertiary
            )
            Text(
                text = "Peak: ${SpeedFormatter.formatSpeed(maxSpeed, unit)}",
                fontSize = 9.sp,
                color = colors.textTertiary
            )
        }
    }
}
