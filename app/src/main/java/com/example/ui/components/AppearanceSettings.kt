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

package com.example.ui.components

import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.getColorSchemeForTheme
import com.example.ui.theme.parseHexColor
import com.example.ui.viewmodel.DialerViewModel
import com.example.util.RichHapticEngine
import com.example.ui.theme.ThemeMode

@Composable
fun AppearanceSettings(
    viewModel: DialerViewModel,
    cardBgColor: Color,
    highlightedTitle: String? = null
) {
    val context = LocalContext.current
    val themeMode by viewModel.themeMode
    val isM3Expressive by viewModel.isM3Expressive

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
    ) {
        // [Header] THEME & STYLING
        item {
            PreferenceHeader(stringResource(R.string.header_theme_styling))
        }

        // Appearance Mode Card (Day / Default / Night)
        item {
            HighlightableCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                cardBgColor = cardBgColor,
                isHighlighted = isMatchTitle("Appearance Mode", highlightedTitle) ||
                        isMatchTitle("Appearance Settings", highlightedTitle) ||
                        isMatchTitle("Appearance & Theme", highlightedTitle) ||
                        isMatchTitle("Dark Mode & Theme Mode", highlightedTitle),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    MaterialTheme.shapes.small
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_theme_mode),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = stringResource(R.string.settings_theme_mode_sub),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ThemeModeOption(
                            label = stringResource(R.string.theme_day),
                            sublabel = stringResource(R.string.theme_day_sub),
                            accent = MaterialTheme.colorScheme.primary,
                            surfaceColor = Color.White,
                            selected = themeMode == ThemeMode.DAY,
                            onClick = { viewModel.updateThemeMode(ThemeMode.DAY) },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeModeOption(
                            label = stringResource(R.string.theme_default),
                            sublabel = stringResource(R.string.theme_default_sub),
                            accent = MaterialTheme.colorScheme.primary,
                            surfaceColor = Color(0xFF060A12),
                            selected = themeMode == ThemeMode.DEFAULT,
                            onClick = { viewModel.updateThemeMode(ThemeMode.DEFAULT) },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeModeOption(
                            label = stringResource(R.string.theme_night),
                            sublabel = stringResource(R.string.theme_night_sub),
                            accent = MaterialTheme.colorScheme.primary,
                            surfaceColor = Color.Black,
                            selected = themeMode == ThemeMode.NIGHT,
                            onClick = { viewModel.updateThemeMode(ThemeMode.NIGHT) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Accent Color Card
        item {
            val accentColor by viewModel.themeColor
            val customHex by viewModel.customColorHex
            val dark = themeMode != ThemeMode.DAY
            var showCustomDialog by remember { mutableStateOf(false) }

            val palettes = remember(dark) {
                listOf(
                    "nova_aurora" to getColorSchemeForTheme("nova_aurora", dark).primary,
                    "nova_nebula" to getColorSchemeForTheme("nova_nebula", dark).primary,
                    "nova_glacier" to getColorSchemeForTheme("nova_glacier", dark).primary,
                    "nova_ember" to getColorSchemeForTheme("nova_ember", dark).primary,
                    "nova_forest" to getColorSchemeForTheme("nova_forest", dark).primary,
                    "nova_mono" to getColorSchemeForTheme("nova_mono", dark).primary
                )
            }

            HighlightableCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                cardBgColor = cardBgColor,
                isHighlighted = isMatchTitle("Accent Color", highlightedTitle),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                    MaterialTheme.shapes.small
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ColorLens,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_accent_color),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = accentLabel(accentColor, customHex),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AccentSwatch(
                            label = stringResource(R.string.accent_color_system),
                            brush = systemAccentBrush(),
                            selected = accentColor == "system",
                            onClick = { viewModel.updateThemeColor("system") }
                        )
                        palettes.forEach { (key, color) ->
                            AccentSwatch(
                                label = stringResource(
                                    when (key) {
                                        "nova_aurora" -> R.string.accent_palette_aurora
                                        "nova_nebula" -> R.string.accent_palette_nebula
                                        "nova_glacier" -> R.string.accent_palette_glacier
                                        "nova_ember" -> R.string.accent_palette_ember
                                        "nova_forest" -> R.string.accent_palette_forest
                                        else -> R.string.accent_palette_mono
                                    }
                                ),
                                color = color,
                                selected = accentColor == key,
                                onClick = { viewModel.updateThemeColor(key) }
                            )
                        }
                        AccentSwatch(
                            label = stringResource(R.string.accent_color_custom),
                            color = parseHexColor(customHex),
                            selected = accentColor == "custom",
                            onClick = { showCustomDialog = true }
                        )
                    }
                }
            }

            if (showCustomDialog) {
                CustomAccentDialog(
                    initialHex = customHex,
                    onDismiss = { showCustomDialog = false },
                    onApply = { hex ->
                        viewModel.updateCustomColorHex(hex)
                        viewModel.updateThemeColor("custom")
                        showCustomDialog = false
                    }
                )
            }
        }

        // M3 Expressive Card
        item {
            HighlightableCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                cardBgColor = cardBgColor,
                isHighlighted = isMatchTitle("Expressive Material 3 Layout", highlightedTitle),
                shape = MaterialTheme.shapes.medium
            ) {
                SettingsRowToggle(
                    title = stringResource(R.string.settings_m3_expressive),
                    subtitle = stringResource(R.string.settings_m3_expressive_sub),
                    checked = isM3Expressive,
                    onCheckedChange = { viewModel.updateM3Expressive(it) },
                    icon = Icons.Default.AutoAwesome,
                    iconBgColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                    iconTint = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}

@Composable
fun ThemeModeOption(
    label: String,
    sublabel: String,
    accent: Color,
    surfaceColor: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    }
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(if (selected) 2.dp else 1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(surfaceColor, CircleShape)
                    .border(3.dp, accent, CircleShape)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = sublabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun accentLabel(key: String, customHex: String): String = when (key) {
    "nova_aurora" -> stringResource(R.string.accent_palette_aurora)
    "nova_nebula" -> stringResource(R.string.accent_palette_nebula)
    "nova_glacier" -> stringResource(R.string.accent_palette_glacier)
    "nova_ember" -> stringResource(R.string.accent_palette_ember)
    "nova_forest" -> stringResource(R.string.accent_palette_forest)
    "nova_mono" -> stringResource(R.string.accent_palette_mono)
    "custom" -> customHex
    else -> stringResource(R.string.accent_color_system)
}

private fun systemAccentBrush(): Brush = Brush.sweepGradient(
    listOf(
        Color(0xFFFF6B6B),
        Color(0xFFFFD166),
        Color(0xFF06D6A0),
        Color(0xFF4DA3FF),
        Color(0xFFB9AEFF),
        Color(0xFFFF6B6B)
    )
)

@Composable
private fun AccentSwatch(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    color: Color? = null,
    brush: Brush? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(64.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .then(
                    if (brush != null) {
                        Modifier.background(brush)
                    } else {
                        Modifier.background(color ?: MaterialTheme.colorScheme.primary)
                    }
                )
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    },
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

@Composable
private fun CustomAccentDialog(
    initialHex: String,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit
) {
    var hex by remember {
        mutableStateOf(if (initialHex.startsWith("#")) initialHex else "#$initialHex")
    }
    val normalized = hex.trim().removePrefix("#")
    val isValid = (normalized.length == 6 || normalized.length == 8) &&
        normalized.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.theme_custom_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.theme_custom_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (isValid) parseHexColor(hex) else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    OutlinedTextField(
                        value = hex,
                        onValueChange = { hex = it },
                        label = { Text(stringResource(R.string.theme_hex_code_label)) },
                        singleLine = true,
                        isError = !isValid,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmed = hex.trim()
                    onApply(if (trimmed.startsWith("#")) trimmed else "#$trimmed")
                },
                enabled = isValid
            ) {
                Text(stringResource(R.string.theme_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}
