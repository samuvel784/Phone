/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer

/**
 * One motion language for the whole app. Springs for things the user touches (they feel alive and can be
 * interrupted), short eased tweens for things that fade. Using the same specs everywhere makes the app feel calm.
 */
object NovaMotion {
    const val QUICK = 160
    const val MEDIUM = 280

    /** Fast, slightly springy: button presses, tab indicator. */
    fun <T> snappy(): SpringSpec<T> = spring(dampingRatio = 0.78f, stiffness = 520f)

    /** Soft landing without overshoot: panels, sheets, content changes. */
    fun <T> smooth(): SpringSpec<T> = spring(dampingRatio = 0.92f, stiffness = 380f)

    /** A little bounce for playful moments such as the keypad button appearing. */
    fun <T> bouncy(): SpringSpec<T> = spring(dampingRatio = 0.62f, stiffness = 320f)

    fun <T> fade(duration: Int = MEDIUM): TweenSpec<T> = tween(duration, easing = FastOutSlowInEasing)
}

/**
 * Presses shrink the element a little and spring back. The scale is read inside graphicsLayer, so while the
 * animation runs only the drawing is updated, the composable is not recomposed on every frame.
 */
fun Modifier.pressScale(interactionSource: InteractionSource, pressedScale: Float = 0.96f): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale = animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = NovaMotion.snappy(),
        label = "pressScale"
    )
    graphicsLayer {
        val s = scale.value
        scaleX = s
        scaleY = s
    }
}

/**
 * The page background: the base colour with a soft brand-coloured glow at the top. It is only drawn (no extra
 * layout or composables), so it costs almost nothing.
 */
fun Modifier.novaCanvas(): Modifier = composed {
    val nova = NovaTheme.colors
    val background = MaterialTheme.colorScheme.background
    drawBehind {
        drawRect(color = background)
        // wash of brand colour fading out over the upper half
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(nova.canvasTint, Color.Transparent),
                startY = 0f,
                endY = size.height * 0.45f
            ),
            size = Size(size.width, size.height * 0.45f)
        )
        // glow in the top-right corner
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(nova.glow.copy(alpha = nova.glow.alpha * 0.45f), Color.Transparent),
                center = Offset(size.width * 0.92f, 0f),
                radius = size.width * 0.85f
            )
        )
    }
}
