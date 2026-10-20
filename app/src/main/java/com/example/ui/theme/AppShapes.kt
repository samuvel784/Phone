/*
 * Copyright (C) 2026 MovStore
 * Nova Dialer "Aurora" shape engine - single source of truth for UI geometry.
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

package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/*
 * Aurora geometry is softer and rounder than the upstream Secure Dialer shapes:
 * generous squircle avatars/cards, pill chips and dialogs with a 34dp radius.
 */

enum class ShapeStyle(val key: String) {
    SQUIRCLE("squircle"),
    ROUNDED("rounded"),
    CIRCULAR("circular"),
    SQUARE("square")
}

/**
 * Single source of truth for ALL UI geometry in the app.
 * Every component (Avatar, Button, Chip, Card, Dialog) MUST reference these tokens.
 */
data class UnifiedShapeTokens(
    val avatarShape: Shape,
    val buttonShape: Shape,
    val cardShape: Shape,
    val chipShape: Shape,
    val smallShape: Shape
)

fun calculateUnifiedShapes(style: ShapeStyle): UnifiedShapeTokens {
    return when (style) {
        ShapeStyle.SQUIRCLE,
        ShapeStyle.CIRCULAR -> UnifiedShapeTokens(
            avatarShape = RoundedCornerShape(percent = 36),
            buttonShape = RoundedCornerShape(percent = 32),
            cardShape = RoundedCornerShape(percent = 24),
            chipShape = RoundedCornerShape(percent = 50),
            smallShape = RoundedCornerShape(percent = 24)
        )
        ShapeStyle.ROUNDED -> UnifiedShapeTokens(
            avatarShape = RoundedCornerShape(percent = 28),
            buttonShape = RoundedCornerShape(percent = 24),
            cardShape = RoundedCornerShape(percent = 20),
            chipShape = RoundedCornerShape(percent = 30),
            smallShape = RoundedCornerShape(percent = 20)
        )
        ShapeStyle.SQUARE -> UnifiedShapeTokens(
            avatarShape = RoundedCornerShape(percent = 10),
            buttonShape = RoundedCornerShape(percent = 10),
            cardShape = RoundedCornerShape(percent = 8),
            chipShape = RoundedCornerShape(percent = 10),
            smallShape = RoundedCornerShape(percent = 8)
        )
    }
}

// CompositionLocal to provide shapes globally down the Compose tree
val LocalUnifiedShapes = compositionLocalOf { calculateUnifiedShapes(ShapeStyle.SQUIRCLE) }

// Clean shorthand accessor for composables
object AppShapes {
    // SINGLE SOURCE OF TRUTH AURORA TOKENS
    val Avatar = RoundedCornerShape(percent = 36)
    val Keypad = RoundedCornerShape(percent = 30)
    val Chip = RoundedCornerShape(percent = 50)
    val Card = RoundedCornerShape(26.dp)
    val Dialog = RoundedCornerShape(34.dp)
    val BottomSheet = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    val Small = RoundedCornerShape(percent = 30)

    val current: UnifiedShapeTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalUnifiedShapes.current
}
