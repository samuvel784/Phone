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

package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

/**
 * Inter, bundled with the app (SIL Open Font License, see assets/licenses/Inter-OFL.txt).
 * Only Latin, Greek and Cyrillic glyphs are included, other scripts (Hindi, Chinese, Japanese, Arabic...)
 * fall back to the system font automatically.
 */
val NovaFontFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold)
)

// Tabular digits: every digit has the same width, so numbers in the dialpad and call timer never jiggle.
private const val TABULAR_DIGITS = "tnum"

private fun style(
    size: Int,
    line: Int,
    weight: FontWeight,
    tracking: Float,
    tabular: Boolean = false
): TextStyle = TextStyle(
    fontFamily = NovaFontFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
    fontFeatureSettings = if (tabular) TABULAR_DIGITS else null
)

private fun novaTypography(expressive: Boolean): Typography {
    // The expressive variant uses a slightly heavier weight for titles and labels.
    val title = if (expressive) FontWeight.Bold else FontWeight.SemiBold
    val label = if (expressive) FontWeight.Bold else FontWeight.SemiBold
    return Typography(
        displayLarge = style(57, 64, FontWeight.SemiBold, -1.6f, tabular = true),
        displayMedium = style(45, 52, FontWeight.SemiBold, -1.0f, tabular = true),
        displaySmall = style(36, 44, FontWeight.SemiBold, -0.6f, tabular = true),
        headlineLarge = style(32, 40, FontWeight.SemiBold, -0.5f, tabular = true),
        headlineMedium = style(28, 36, FontWeight.SemiBold, -0.3f, tabular = true),
        headlineSmall = style(24, 32, FontWeight.SemiBold, -0.2f, tabular = true),
        titleLarge = style(22, 28, title, -0.2f, tabular = true),
        titleMedium = style(16, 24, title, 0.0f),
        titleSmall = style(14, 20, title, 0.05f),
        bodyLarge = style(16, 24, FontWeight.Normal, 0.0f),
        bodyMedium = style(14, 20, FontWeight.Normal, 0.05f),
        bodySmall = style(12, 16, FontWeight.Normal, 0.15f),
        labelLarge = style(14, 20, label, 0.05f),
        labelMedium = style(12, 16, label, 0.25f),
        labelSmall = style(11, 16, label, 0.35f)
    )
}

val Typography: Typography = novaTypography(expressive = false)
val ExpressiveTypography: Typography = novaTypography(expressive = true)
