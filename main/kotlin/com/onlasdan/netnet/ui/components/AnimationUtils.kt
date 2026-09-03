package com.onlasdan.netnet.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

/**
 * Provides a staggered slide-up and fade-in entrance animation for individual list items or cards.
 */
@Composable
fun StaggeredAnimatedItem(
    index: Int,
    modifier: Modifier = Modifier,
    baseDelayMs: Int = 40,
    staggerIntervalMs: Int = 55,
    content: @Composable () -> Unit
) {
    val alphaAnim = remember { Animatable(0f) }
    val offsetYAnim = remember { Animatable(32f) }
    val scaleAnim = remember { Animatable(0.96f) }

    LaunchedEffect(Unit) {
        val totalDelay = (baseDelayMs + index * staggerIntervalMs).toLong()
        delay(totalDelay)

        // Run animations concurrently
        alphaAnim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
        )
    }

    LaunchedEffect(Unit) {
        val totalDelay = (baseDelayMs + index * staggerIntervalMs).toLong()
        delay(totalDelay)

        offsetYAnim.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
        )
    }

    LaunchedEffect(Unit) {
        val totalDelay = (baseDelayMs + index * staggerIntervalMs).toLong()
        delay(totalDelay)

        scaleAnim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
        )
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                alpha = alphaAnim.value
                translationY = offsetYAnim.value
                scaleX = scaleAnim.value
                scaleY = scaleAnim.value
            }
    ) {
        content()
    }
}

/**
 * Modifier extension to apply a subtle pulse/breathe animation for active indicator chips or gauges.
 */
fun Modifier.pulseAnimation(
    enabled: Boolean = true,
    minScale: Float = 0.96f,
    maxScale: Float = 1.04f,
    durationMs: Int = 1200
): Modifier = composed {
    if (!enabled) return@composed this

    val infiniteTransition = rememberInfiniteTransition(label = "pulse_transition")
    val scale by infiniteTransition.animateFloat(
        initialValue = minScale,
        targetValue = maxScale,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    this.scale(scale)
}
