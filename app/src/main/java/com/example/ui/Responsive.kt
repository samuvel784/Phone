/*
 * Copyright (C) 2026 MovStore
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.example.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.floor

/**
 * Shared breakpoints for Nova Dialer.
 *
 * The values follow the Material window-size classes so phones, foldables, tablets and
 * Chromebooks behave consistently: a phone is always [Compact], an unfolded foldable or a small
 * tablet is [Medium], and a tablet/desktop window is [Expanded].
 */
enum class NovaWindowWidth {
    Compact,
    Medium,
    Expanded
}

object NovaBreakpoints {
    /** Below this width a layout must fit a single phone column. */
    val COMPACT_MAX = 599.dp

    /** From this width a window has room for wider gutters and more grid cells. */
    val EXPANDED_MIN = 840.dp

    /** A short window (landscape phone, split-screen) needs compact vertical spacing. */
    val COMPACT_HEIGHT_MAX = 560.dp

    /** Readable line length for lists and contact cards. Backgrounds stay full-bleed. */
    val CONTENT_MAX_WIDTH = 640.dp

    /** Settings talk to the user in longer sentences, so they get a slightly wider frame. */
    val SETTINGS_MAX_WIDTH = 720.dp

    /** A dialpad wider than this wastes space and looks stretched on tablets. */
    val DIALPAD_MAX_WIDTH = 480.dp
}

/** Classifies a window width into the shared breakpoints. */
fun novaWindowWidth(width: Dp): NovaWindowWidth = when {
    width <= NovaBreakpoints.COMPACT_MAX -> NovaWindowWidth.Compact
    width < NovaBreakpoints.EXPANDED_MIN -> NovaWindowWidth.Medium
    else -> NovaWindowWidth.Expanded
}

/** True when vertical space is scarce, e.g. a phone in landscape. */
fun isCompactHeight(height: Dp): Boolean = height < NovaBreakpoints.COMPACT_HEIGHT_MAX

/**
 * Number of equal columns that fit in [availableWidth] when every cell must be at least
 * [minCellWidth] wide. Always returns at least one column and never exceeds [maxColumns].
 *
 * Kept as a pure function so the breakpoint behaviour can be unit-tested without rendering.
 */
fun responsiveColumns(
    availableWidth: Dp,
    minCellWidth: Dp,
    maxColumns: Int = Int.MAX_VALUE
): Int {
    if (maxColumns <= 1) return 1
    val cell = minCellWidth.value.coerceAtLeast(1f)
    val raw = floor(availableWidth.value / cell).toInt()
    return raw.coerceIn(1, maxColumns)
}

/**
 * Outer padding that grows with the window so text does not run edge-to-edge on tablets while
 * phones keep their existing 16 dp rhythm.
 */
fun adaptiveHorizontalPadding(width: Dp): Dp = when (novaWindowWidth(width)) {
    NovaWindowWidth.Compact -> 16.dp
    NovaWindowWidth.Medium -> 24.dp
    NovaWindowWidth.Expanded -> 32.dp
}

/**
 * Centres [content] in a readable column while the caller keeps the full-bleed background.
 *
 * Use this inside a full-width/height surface, not around it: the frame only limits the width of
 * its children, it never paints a background of its own.
 */
@Composable
fun NovaContentFrame(
    modifier: Modifier = Modifier,
    maxWidth: Dp = NovaBreakpoints.CONTENT_MAX_WIDTH,
    contentAlignment: Alignment = Alignment.TopCenter,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = contentAlignment
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = maxWidth)
                .fillMaxWidth(),
            content = content
        )
    }
}

/**
 * Width of the empty gutter on each side of a [maxWidth] content column inside [windowWidth].
 * Used to keep floating controls (FAB, banners) aligned with the centred content instead of the
 * window edge. Returns 0.dp while the window is narrower than the column.
 */
fun contentSideInset(windowWidth: Dp, maxWidth: Dp = NovaBreakpoints.CONTENT_MAX_WIDTH): Dp {
    val gutter = (windowWidth.value - maxWidth.value) / 2f
    return if (gutter > 0f) gutter.dp else 0.dp
}
