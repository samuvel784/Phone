/*
 * Copyright (C) 2026 MovStore
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ContactCache
import com.example.R
import com.example.model.DialpadMatch
import com.example.ui.theme.*
import com.example.ui.viewmodel.DialerViewModel
import com.example.util.RichHapticEngine
import com.example.util.T9HighlightHelper

const val MAX_DIALPAD_MATCH_ROWS = 6

/** Bright blue used for the typed digits inside a matching phone number. */
val DialpadMatchHighlightColor = Color(0xFF2196F3)

/**
 * Live T9 suggestions. Rendered at the TOP of the keypad sheet so the typed
 * number can stay down by the keypad (see the layout note in [DialpadOverlay]).
 */
@Composable
private fun DialpadMatchesSection(
    matches: List<DialpadMatch>,
    query: String,
    onCallClick: (String) -> Unit
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.dialpad_matches_title),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.dialpad_matches_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 168.dp)
            .padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        itemsIndexed(
            items = matches.take(MAX_DIALPAD_MATCH_ROWS),
            key = { index, _ -> index },
            contentType = { _, _ -> "dialpad_match" }
        ) { _, match ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(AppShapes.Card)
                    .clickable {
                        RichHapticEngine.performHaptic(context, RichHapticEngine.HapticStyle.KEY_TICK)
                        onCallClick(match.number)
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(AppShapes.Avatar)
                        .background(match.avatarBg.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = match.avatarText,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = match.avatarTextColor
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = match.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // The number comes first and gets the full width it needs. The label is
                        // secondary: it must never push the actual number off the row.
                        HighlightedNumberText(
                            fullNumber = match.number,
                            query = query,
                            style = MaterialTheme.typography.bodyMedium,
                            normalColor = MaterialTheme.colorScheme.onSurface,
                            highlightColor = DialpadMatchHighlightColor
                        )
                        if (match.label.isNotBlank()) {
                            Text(
                                text = "• ${match.label}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
fun HighlightedNumberText(
    fullNumber: String,
    query: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodySmall,
    normalColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    highlightColor: Color = DialpadMatchHighlightColor,
    maxLines: Int = 1
) {
    val annotatedString = remember(fullNumber, query, normalColor, highlightColor) {
        // T9HighlightHelper maps the typed digits onto the *formatted* number, so a query such as
        // "12345" still highlights correctly inside "+1 (234) 567-890".
        val cleanQuery = query.filter { it.isDigit() || it == '+' }
        T9HighlightHelper.highlightNumber(
            number = fullNumber,
            query = cleanQuery,
            highlightColor = highlightColor,
            defaultColor = normalColor,
            highlightFontWeight = FontWeight.Bold
        )
    }
    Text(
        text = annotatedString,
        style = style,
        modifier = modifier,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
fun DialpadOverlay(
    inputValue: String,
    onValueChange: (String) -> Unit,
    onClose: () -> Unit,
    onCallClick: (String) -> Unit,
    onSpeedDialCall: (String) -> Unit,
    speedDialMap: Map<Int, String>,
    voicemailNumber: String,
    viewModel: DialerViewModel? = null,
    onCallWithSim: ((String, String) -> Unit)? = null,
    dialpadMatches: List<DialpadMatch> = emptyList()
) {
    val context = LocalContext.current
    val isExpressive = LocalM3Expressive.current
    val isAmoled = LocalAmoledMode.current

    val dialKeyColor = if (isAmoled) {
        Color(0xFF141414)
    } else if (isExpressive) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
    } else {
        MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.BottomSheet),
        color = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surface,
        border = if (isAmoled) BorderStroke(1.dp, Color(0xFF222222)) else null,
        tonalElevation = if (isAmoled) 0.dp else 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(AppShapes.Chip)
                    .background(MaterialTheme.colorScheme.outlineVariant)
                    .clickable {
                        RichHapticEngine.performHaptic(context, RichHapticEngine.HapticStyle.KEY_TICK)
                        onClose()
                    }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (inputValue.isNotBlank() && dialpadMatches.isNotEmpty()) {
                        DialpadMatchesSection(
                            matches = dialpadMatches,
                            query = inputValue,
                            onCallClick = onCallClick
                        )
                    }

                    val isUnsavedNumber = remember(inputValue) {
                        !inputValue.isBlank() &&
                            (viewModel?.isNumberUnsaved(inputValue)
                                ?: (ContactCache.getContact(inputValue) == null))
                    }

                    if (isUnsavedNumber && inputValue.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    RichHapticEngine.performHaptic(context, RichHapticEngine.HapticStyle.KEY_TICK)
                                    viewModel?.openAddContactWithNumber(inputValue)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp),
                                shape = AppShapes.Chip,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.dialpad_create_contact), style = MaterialTheme.typography.labelMedium)
                            }

                            Button(
                                onClick = {
                                    RichHapticEngine.performHaptic(context, RichHapticEngine.HapticStyle.KEY_TICK)
                                    viewModel?.addToExistingPendingNumber?.value = inputValue
                                    viewModel?.isAddToExistingSheetVisible?.value = true
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp),
                                shape = AppShapes.Chip,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Icon(Icons.Default.GroupAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.dialpad_add_to_existing), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Display typed number & backspace
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    val formattedText = remember(inputValue) {
                        com.example.util.DialpadFormatter.format(inputValue)
                    }
                    Text(
                        text = formattedText.ifEmpty { stringResource(R.string.dialpad_enter_number) },
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = if (inputValue.length > 10) 24.sp else 32.sp
                        ),
                        color = if (inputValue.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (inputValue.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            RichHapticEngine.performHaptic(context, RichHapticEngine.HapticStyle.KEY_TICK)
                            if (inputValue.isNotEmpty()) {
                                onValueChange(inputValue.dropLast(1))
                            }
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Backspace,
                            contentDescription = stringResource(R.string.search_clear),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 12 Keypad buttons (1 to #)
            val rows = listOf(
                listOf("1" to "", "2" to "ABC", "3" to "DEF"),
                listOf("4" to "GHI", "5" to "JKL", "6" to "MNO"),
                listOf("7" to "PQRS", "8" to "TUV", "9" to "WXYZ"),
                listOf("*" to "", "0" to "+", "#" to "")
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                rows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        row.forEach { (digit, letters) ->
                            val interactionSource = remember { MutableInteractionSource() }
                            val isPressed by interactionSource.collectIsPressedAsState()
                            val scale by animateFloatAsState(
                                targetValue = if (isPressed) 0.92f else 1.0f,
                                animationSpec = spring(
                                    stiffness = Spring.StiffnessHigh,
                                    dampingRatio = Spring.DampingRatioMediumBouncy
                                ),
                                label = "dialpad_key_scale"
                            )

                            Surface(
                                onClick = {
                                    RichHapticEngine.performHaptic(context, RichHapticEngine.HapticStyle.KEY_TICK)
                                    onValueChange(inputValue + digit)
                                },
                                interactionSource = interactionSource,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(60.dp)
                                    .scale(scale)
                                    .testTag("dialpad_key_$digit"),
                                shape = AppShapes.Card,
                                color = dialKeyColor,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Text(
                                        text = digit,
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    )
                                    if (letters.isNotBlank()) {
                                        Text(
                                            text = letters,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 9.sp,
                                                letterSpacing = 1.5.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action row (Call, etc.)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.size(56.dp))

                FloatingActionButton(
                    onClick = {
                        if (inputValue.isNotBlank()) {
                            RichHapticEngine.performHaptic(context, RichHapticEngine.HapticStyle.SUCCESS)
                            onCallClick(inputValue)
                        }
                    },
                    modifier = Modifier.size(64.dp),
                    shape = CircleShape,
                    containerColor = getCallGreenColor(),
                    contentColor = getOnCallGreenColor()
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = stringResource(R.string.action_call),
                        modifier = Modifier.size(28.dp)
                    )
                }

                Box(
                    modifier = Modifier.size(56.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (speedDialMap.isNotEmpty() || voicemailNumber.isNotBlank()) {
                        var showSpeedDialMenu by remember { mutableStateOf(false) }
                        IconButton(onClick = { showSpeedDialMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.PersonAdd,
                                contentDescription = "Speed Dial / Voicemail",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        DropdownMenu(
                            expanded = showSpeedDialMenu,
                            onDismissRequest = { showSpeedDialMenu = false }
                        ) {
                            if (voicemailNumber.isNotBlank()) {
                                DropdownMenuItem(
                                    text = { Text("Voicemail ($voicemailNumber)") },
                                    onClick = {
                                        showSpeedDialMenu = false
                                        onSpeedDialCall(voicemailNumber)
                                    }
                                )
                            }
                            speedDialMap.forEach { (key, number) ->
                                DropdownMenuItem(
                                    text = { Text("Speed Dial $key: $number") },
                                    onClick = {
                                        showSpeedDialMenu = false
                                        onSpeedDialCall(number)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
