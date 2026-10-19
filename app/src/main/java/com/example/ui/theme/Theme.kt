/*
 * Copyright (C) 2026 MovStore
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt

/** Rounder, softer shapes: cards 24 dp, dialogs and sheets 32 dp. */
val UnifiedSquircleMaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = AppShapes.Chip,
    medium = AppShapes.Card,
    large = AppShapes.Dialog,
    extraLarge = RoundedCornerShape(40.dp)
)

// ---------------------------------------------------------------------------------------------------------------
// Call colours (used by the call log, dialpad and call screen)
// ---------------------------------------------------------------------------------------------------------------

@Composable
fun getMissedCallColor(): Color {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (isDark) Color(0xFFFF6B81) else Color(0xFFD92D4A)
}

@Composable
fun getCallGreenColor(): Color {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (isDark) Color(0xFF2DD4A0) else Color(0xFF0E9F6E)
}

@Composable
fun getOnCallGreenColor(): Color {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (isDark) Color(0xFF04281D) else Color(0xFFFFFFFF)
}

@Composable
fun getDeclineRedColor(): Color {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (isDark) Color(0xFFFF5470) else Color(0xFFE5334F)
}

@Composable
fun getOnDeclineRedColor(): Color {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (isDark) Color(0xFF3A0610) else Color(0xFFFFFFFF)
}

@Composable
fun getDialedCallColor(): Color {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (isDark) Color(0xFF42A5F5) else Color(0xFF1976D2)
}

@Composable
fun getReceivedCallColor(): Color {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (isDark) Color(0xFF3DDC97) else Color(0xFF0E8F5F)
}

fun parseHexColor(hex: String, fallback: Color = Color(0xFF6E5BFF)): Color {
    return try {
        val clean = hex.removePrefix("#").trim()
        val colorInt = when (clean.length) {
            6 -> "#FF$clean".toColorInt()
            8 -> "#$clean".toColorInt()
            else -> return fallback
        }
        Color(colorInt)
    } catch (_: Exception) {
        fallback
    }
}

val LocalM3Expressive = staticCompositionLocalOf { true }
val LocalAmoledMode = staticCompositionLocalOf { false }

enum class ThemeMode(val key: String) {
    DAY("day"),
    DEFAULT("default"),
    NIGHT("night");

    companion object {
        fun fromKey(key: String?): ThemeMode =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: DEFAULT
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Surfaces: three layered backgrounds. Day = soft porcelain, Default = midnight, Night = true black (AMOLED).
// Cards sit one step above the page, sheets and menus two steps above, so depth comes from colour, not shadows.
// ---------------------------------------------------------------------------------------------------------------

private class ModeSurfaces(
    val background: Color,
    val surface: Color,
    val dim: Color,
    val bright: Color,
    val lowest: Color,
    val low: Color,
    val container: Color,
    val high: Color,
    val highest: Color,
    val variant: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val outlineVariant: Color,
    val inverseSurface: Color,
    val inverseOnSurface: Color,
    val error: Color,
    val onError: Color,
    val errorContainer: Color,
    val onErrorContainer: Color
)

private val DaySurfaces = ModeSurfaces(
    background = Color(0xFFF6F7FB), surface = Color(0xFFFFFFFF),
    dim = Color(0xFFE3E6F0), bright = Color(0xFFFFFFFF),
    lowest = Color(0xFFFFFFFF), low = Color(0xFFFAFAFE), container = Color(0xFFF2F4FA),
    high = Color(0xFFECEFF7), highest = Color(0xFFE4E8F2),
    variant = Color(0xFFE9ECF5), onSurface = Color(0xFF0E1120), onSurfaceVariant = Color(0xFF596079),
    outline = Color(0xFF8890A8), outlineVariant = Color(0xFFDDE1EC),
    inverseSurface = Color(0xFF1E2236), inverseOnSurface = Color(0xFFF2F4FB),
    error = Color(0xFFD92D4A), onError = Color.White,
    errorContainer = Color(0xFFFFE3E8), onErrorContainer = Color(0xFF5A0A1B)
)

private val MidnightSurfaces = ModeSurfaces(
    background = Color(0xFF090B13), surface = Color(0xFF0D1019),
    dim = Color(0xFF090B13), bright = Color(0xFF232A42),
    lowest = Color(0xFF07080F), low = Color(0xFF0F1220), container = Color(0xFF141829),
    high = Color(0xFF1A1F34), highest = Color(0xFF222842),
    variant = Color(0xFF1B2036), onSurface = Color(0xFFE9ECF8), onSurfaceVariant = Color(0xFFA0A8C2),
    outline = Color(0xFF6B7390), outlineVariant = Color(0xFF262D47),
    inverseSurface = Color(0xFFE9ECF8), inverseOnSurface = Color(0xFF141829),
    error = Color(0xFFFF6B81), onError = Color(0xFF3A0610),
    errorContainer = Color(0xFF4A1020), onErrorContainer = Color(0xFFFFD9DF)
)

private val OnyxSurfaces = ModeSurfaces(
    background = Color(0xFF000000), surface = Color(0xFF000000),
    dim = Color(0xFF000000), bright = Color(0xFF1C1C23),
    lowest = Color(0xFF000000), low = Color(0xFF08080B), container = Color(0xFF0E0E12),
    high = Color(0xFF15151B), highest = Color(0xFF1C1C23),
    variant = Color(0xFF16161C), onSurface = Color(0xFFFFFFFF), onSurfaceVariant = Color(0xFFC9CBD6),
    outline = Color(0xFF5A5C68), outlineVariant = Color(0xFF24252D),
    inverseSurface = Color(0xFFF2F3F8), inverseOnSurface = Color(0xFF0E0E12),
    error = Color(0xFFFF6B81), onError = Color(0xFF3A0610),
    errorContainer = Color(0xFF3A0F19), onErrorContainer = Color(0xFFFFD9DF)
)

private fun surfacesFor(mode: ThemeMode): ModeSurfaces = when (mode) {
    ThemeMode.DAY -> DaySurfaces
    ThemeMode.DEFAULT -> MidnightSurfaces
    ThemeMode.NIGHT -> OnyxSurfaces
}

private fun assembleScheme(mode: ThemeMode, accent: AccentTokens): ColorScheme {
    val s = surfacesFor(mode)
    val base = if (mode == ThemeMode.DAY) lightColorScheme() else darkColorScheme()
    return base.copy(
        primary = accent.primary,
        onPrimary = accent.onPrimary,
        primaryContainer = accent.primaryContainer,
        onPrimaryContainer = accent.onPrimaryContainer,
        inversePrimary = accent.primaryContainer,
        secondary = accent.secondary,
        onSecondary = accent.onSecondary,
        secondaryContainer = accent.secondaryContainer,
        onSecondaryContainer = accent.onSecondaryContainer,
        tertiary = accent.tertiary,
        onTertiary = accent.onTertiary,
        tertiaryContainer = accent.tertiaryContainer,
        onTertiaryContainer = accent.onTertiaryContainer,
        background = s.background,
        onBackground = s.onSurface,
        surface = s.surface,
        onSurface = s.onSurface,
        surfaceVariant = s.variant,
        onSurfaceVariant = s.onSurfaceVariant,
        surfaceTint = Color.Transparent,
        inverseSurface = s.inverseSurface,
        inverseOnSurface = s.inverseOnSurface,
        outline = s.outline,
        outlineVariant = s.outlineVariant,
        scrim = Color(0x99000000),
        surfaceBright = s.bright,
        surfaceDim = s.dim,
        surfaceContainerLowest = s.lowest,
        surfaceContainerLow = s.low,
        surfaceContainer = s.container,
        surfaceContainerHigh = s.high,
        surfaceContainerHighest = s.highest,
        error = s.error,
        onError = s.onError,
        errorContainer = s.errorContainer,
        onErrorContainer = s.onErrorContainer
    )
}

/** Colour scheme of one accent in the Default (dark) or Day (light) mode. Used by the Appearance swatches too. */
fun getColorSchemeForTheme(
    themeColor: String,
    darkTheme: Boolean,
    customColorHex: String = "#6E5BFF"
): ColorScheme {
    val mode = if (darkTheme) ThemeMode.DEFAULT else ThemeMode.DAY
    val surfaces = surfacesFor(mode)
    val accent = buildAccent(accentSeed(themeColor, customColorHex), darkTheme, surfaces.container, surfaces.background)
    return assembleScheme(mode, accent)
}

/** Dynamic (wallpaper) colours keep their own accent, but the Night mode still gets true-black surfaces. */
private fun nightOverride(scheme: ColorScheme): ColorScheme {
    val s = OnyxSurfaces
    return scheme.copy(
        background = s.background, surface = s.surface, surfaceVariant = s.variant,
        surfaceTint = Color.Transparent, outline = s.outline, outlineVariant = s.outlineVariant,
        surfaceContainerLowest = s.lowest, surfaceContainerLow = s.low, surfaceContainer = s.container,
        surfaceContainerHigh = s.high, surfaceContainerHighest = s.highest,
        onBackground = s.onSurface, onSurface = s.onSurface, onSurfaceVariant = s.onSurfaceVariant
    )
}

@Composable
fun MyApplicationTheme(
    themeMode: ThemeMode? = null,
    darkTheme: Boolean? = null,
    dynamicColor: Boolean = false,
    themeColor: String = "system",
    customColorHex: String = "#6E5BFF",
    isAmoledMode: Boolean = false,
    isM3Expressive: Boolean = true,
    content: @Composable () -> Unit,
) {
    val mode = themeMode ?: when (darkTheme) {
        false -> ThemeMode.DAY
        true -> ThemeMode.DEFAULT
        null -> ThemeMode.DEFAULT
    }
    val dark = mode != ThemeMode.DAY

    val useDynamic = dynamicColor && themeColor == "system" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val context = LocalContext.current

    // Building a colour scheme allocates dozens of objects, so it is done once per setting change,
    // not on every recomposition.
    val colorScheme = remember(mode, useDynamic, themeColor, customColorHex) {
        if (useDynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val dynamic = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            if (mode == ThemeMode.NIGHT) nightOverride(dynamic) else dynamic.copy(surfaceTint = Color.Transparent)
        } else {
            val surfaces = surfacesFor(mode)
            assembleScheme(mode, buildAccent(accentSeed(themeColor, customColorHex), dark, surfaces.container, surfaces.background))
        }
    }

    val novaColors = remember(colorScheme, dark) {
        buildNovaColors(colorScheme.primary, colorScheme.outlineVariant, colorScheme.surface, dark)
    }
    val unifiedShapes = remember { calculateUnifiedShapes(ShapeStyle.SQUIRCLE) }
    val isNight = mode == ThemeMode.NIGHT

    CompositionLocalProvider(
        LocalM3Expressive provides isM3Expressive,
        LocalAmoledMode provides isNight,
        LocalUnifiedShapes provides unifiedShapes,
        LocalNovaColors provides novaColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = UnifiedSquircleMaterialShapes,
            typography = if (isM3Expressive) ExpressiveTypography else Typography,
            content = content
        )
    }
}
