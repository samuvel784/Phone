/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils

/**
 * Builds a complete, well-balanced accent (primary, secondary, tertiary and their containers) from ONE seed colour.
 * The built-in palettes and the user's custom colour all go through the same code, so every accent looks equally
 * polished and keeps readable contrast in light and dark mode.
 */
internal class AccentTokens(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color
)

internal fun Color.toHsl(): FloatArray {
    val out = FloatArray(3)
    ColorUtils.colorToHSL(this.toArgb(), out)
    return out
}

internal fun hslColor(hue: Float, saturation: Float, lightness: Float): Color {
    val h = ((hue % 360f) + 360f) % 360f
    return Color(ColorUtils.HSLToColor(floatArrayOf(h, saturation.coerceIn(0f, 1f), lightness.coerceIn(0f, 1f))))
}

/** Seed colour of an accent key. The keys are the ones the Appearance screen already stores. */
internal fun accentSeed(key: String, customHex: String): Color = when (key) {
    "nova_nebula" -> Color(0xFFC13DE8)
    "nova_glacier" -> Color(0xFF1AA7EC)
    "nova_ember" -> Color(0xFFF26B1D)
    "nova_forest" -> Color(0xFF12B981)
    "nova_mono" -> Color(0xFF8790AB)
    "custom" -> parseHexColor(customHex)
    else -> Color(0xFF6E5BFF) // nova_aurora, "system" and any older key
}

/** WCAG contrast ratio between two opaque colours (1 = identical, 21 = black on white). */
internal fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance() + 0.05f
    val lb = b.luminance() + 0.05f
    return if (la > lb) la / lb else lb / la
}

/** White or near-black, whichever is easier to read on [background]. */
internal fun readableOn(background: Color): Color {
    val dark = Color(0xFF0B0D14)
    return if (contrastRatio(background, Color.White) >= contrastRatio(background, dark)) Color.White else dark
}

internal fun buildAccent(seed: Color, dark: Boolean, containerBase: Color, page: Color): AccentTokens {
    val hsl = seed.toHsl()
    val hue = hsl[0]
    val rawSat = hsl[1]
    // keep grey-ish seeds grey (the "mono" accent); colourful seeds get a vivid, consistent saturation
    val sat = if (rawSat < 0.30f) rawSat else rawSat.coerceIn(0.55f, 0.95f)

    // The accent is used for text, icons, switches and selected tabs on the page, so its lightness is chosen by
    // MEASURED contrast against the page (at least 4.5:1, the WCAG AA level for normal text), not guessed.
    fun tone(h: Float, satScale: Float): Color {
        var l = if (dark) 0.76f else 0.50f
        var c = hslColor(h, sat * satScale, l)
        var guard = 0
        while (contrastRatio(c, page) < 4.5f && guard++ < 40) {
            l += if (dark) 0.02f else -0.02f
            c = hslColor(h, sat * satScale, l)
        }
        return c
    }

    val primary = tone(hue, 1f)
    val secondary = tone(hue - 24f, 0.78f)
    val tertiary = tone(hue + 46f, 0.90f)

    fun container(c: Color) = if (dark) lerp(containerBase, c, 0.30f) else lerp(Color.White, c, 0.15f)
    fun onContainer(c: Color) = if (dark) lerp(c, Color.White, 0.78f) else lerp(c, Color.Black, 0.62f)

    return AccentTokens(
        primary = primary, onPrimary = readableOn(primary),
        primaryContainer = container(primary), onPrimaryContainer = onContainer(primary),
        secondary = secondary, onSecondary = readableOn(secondary),
        secondaryContainer = container(secondary), onSecondaryContainer = onContainer(secondary),
        tertiary = tertiary, onTertiary = readableOn(tertiary),
        tertiaryContainer = container(tertiary), onTertiaryContainer = onContainer(tertiary)
    )
}
