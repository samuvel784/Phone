/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Colours that Material's ColorScheme has no slot for: the brand gradient, glow, glass chrome and the
 * success / danger colours used by the call buttons. Provided by [MyApplicationTheme].
 */
@Immutable
class NovaColors(
    val gradientStart: Color,
    val gradientEnd: Color,
    val glow: Color,
    val canvasTint: Color,
    val success: Color,
    val warning: Color,
    val isDark: Boolean
) {
    /** The signature diagonal gradient of the app (keypad button, call screen, highlights). */
    val brand: Brush = Brush.linearGradient(listOf(gradientStart, gradientEnd))
}

val LocalNovaColors = staticCompositionLocalOf {
    NovaColors(
        gradientStart = Color(0xFF6E5BFF),
        gradientEnd = Color(0xFFB04CFF),
        glow = Color(0x666E5BFF),
        canvasTint = Color(0x1A6E5BFF),
        success = Color(0xFF0E9F6E),
        warning = Color(0xFFF59E0B),
        isDark = false
    )
}

object NovaTheme {
    val colors: NovaColors
        @Composable
        @ReadOnlyComposable
        get() = LocalNovaColors.current
}

/** Builds the brand colours from the final colour scheme, so they follow every accent (and dynamic colour). */
internal fun buildNovaColors(primary: Color, dark: Boolean): NovaColors {
    val hsl = primary.toHsl()
    val hue = hsl[0]
    val sat = if (hsl[1] < 0.30f) hsl[1] else hsl[1].coerceIn(0.60f, 0.95f)

    // White icons and text sit on the gradient (keypad button, avatar ring, lock badge). Each end is darkened until
    // white has at least 3:1 contrast on it, which is the WCAG level for large text and icons.
    fun vivid(h: Float): Color {
        var l = if (dark) 0.60f else 0.52f
        var c = hslColor(h, sat, l)
        var guard = 0
        while (contrastRatio(c, Color.White) < 3.0f && guard++ < 40) {
            l -= 0.02f
            c = hslColor(h, sat, l)
        }
        return c
    }
    // Warm hues (orange, yellow, lime) would turn muddy olive if rotated forward, so they shade toward red/pink instead.
    val shift = if (hue in 20f..110f) -34f else 38f
    val start = vivid(hue)
    val end = vivid(hue + shift)
    return NovaColors(
        gradientStart = start,
        gradientEnd = end,
        glow = start.copy(alpha = if (dark) 0.45f else 0.32f),
        canvasTint = start.copy(alpha = if (dark) 0.16f else 0.10f),
        success = if (dark) Color(0xFF2DD4A0) else Color(0xFF0E9F6E),
        warning = if (dark) Color(0xFFFBBF24) else Color(0xFFD97706),
        isDark = dark
    )
}
