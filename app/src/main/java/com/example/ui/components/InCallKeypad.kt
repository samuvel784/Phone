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

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.CallManager
import com.example.R
import com.example.model.Contact
import com.example.model.DialpadMatch
import com.example.model.getAvatarShape
import com.example.ui.theme.LocalAmoledMode
import com.example.ui.theme.LocalM3Expressive
import com.example.util.DialpadSearch
import com.example.util.RichHapticEngine

private data class KeypadItem(val digit: String, val letters: String)

private val IN_CALL_KEYPAD_ITEMS = listOf(
    KeypadItem("1", ""),
    KeypadItem("2", "ABC"),
    KeypadItem("3", "DEF"),
    KeypadItem("4", "GHI"),
    KeypadItem("5", "JKL"),
    KeypadItem("6", "MNO"),
    KeypadItem("7", "PQRS"),
    KeypadItem("8", "TUV"),
    KeypadItem("9", "WXYZ"),
    KeypadItem("*", ""),
    KeypadItem("0", "+"),
    KeypadItem("#", "")
)

private enum class InCallKeypadMode { DTMF, ADD_CALL }

/**
 * In-call keypad with two deliberately separate modes:
 *  - DTMF sends tones to the current call.
 *  - Add call / Search types a name or number, shows contact results, and only places a second
 *    call after the user selects a result (or types a number) and confirms.
 *
 * Contact search is only offered when [isAddCallEnabled] is true. The call-only lock-screen
 * surface passes false, so it can never expose private contacts or recents.
 */
@Composable
fun InCallKeypad(
    onClose: () -> Unit,
    avatarShapeType: String = "circular",
    contacts: List<Contact> = emptyList(),
    isAddCallEnabled: Boolean = false,
    /** Receives DTMF digits only in the DTMF mode. Injectable so tests can prove the separation. */
    onDtmf: (Char) -> Unit = { CallManager.playDtmf(it) },
    /** Returns true when the Add Call request was accepted (placed or awaiting SIM choice). */
    onAddCall: (name: String, number: String) -> Boolean = { _, _ -> false }
) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(InCallKeypadMode.DTMF) }
    var dtmfInput by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var selectedMatch by remember { mutableStateOf<DialpadMatch?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    // Restricted call-only mode cannot retain a previously selected Add Call mode.
    LaunchedEffect(isAddCallEnabled) {
        if (!isAddCallEnabled && mode == InCallKeypadMode.ADD_CALL) {
            mode = InCallKeypadMode.DTMF
            selectedMatch = null
        }
    }

    val matches = remember(searchQuery, contacts) {
        if (searchQuery.isBlank()) {
            emptyList()
        } else {
            DialpadSearch.findContactMatches(
                contacts = contacts,
                rawQuery = searchQuery,
                limit = 6
            )
        }
    }

    val selectedNumber = selectedMatch?.number
        ?: searchQuery.trim().takeIf { value -> value.any { it.isDigit() } }
    val selectedName = selectedMatch?.name ?: searchQuery.trim()
    val canConfirm = selectedNumber != null && !isSubmitting

    fun submitAddCall() {
        if (!canConfirm) return
        val number = selectedNumber ?: return
        isSubmitting = true
        RichHapticEngine.performHaptic(context, RichHapticEngine.HapticStyle.SUCCESS)
        val accepted = onAddCall(selectedName.ifBlank { number }, number)
        if (!accepted) isSubmitting = false
    }

    val isAmoled = LocalAmoledMode.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = MaterialTheme.shapes.large,
        border = if (isAmoled) BorderStroke(1.dp, Color(0xFF222222)) else null,
        colors = CardDefaults.cardColors(
            containerColor = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
        )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = mode == InCallKeypadMode.DTMF,
                    onClick = {
                        RichHapticEngine.performHaptic(context, RichHapticEngine.HapticStyle.KEY_TICK)
                        mode = InCallKeypadMode.DTMF
                        selectedMatch = null
                    },
                    label = { Text(stringResource(R.string.in_call_keypad_mode_dtmf)) },
                    modifier = Modifier.testTag("in_call_keypad_mode_dtmf")
                )
                if (isAddCallEnabled) {
                    FilterChip(
                        selected = mode == InCallKeypadMode.ADD_CALL,
                        onClick = {
                            RichHapticEngine.performHaptic(context, RichHapticEngine.HapticStyle.KEY_TICK)
                            mode = InCallKeypadMode.ADD_CALL
                        },
                        label = { Text(stringResource(R.string.in_call_keypad_mode_add_call)) },
                        modifier = Modifier.testTag("in_call_keypad_mode_add_call")
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (mode == InCallKeypadMode.DTMF) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = dtmfInput.ifEmpty { stringResource(R.string.keypad) },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Start
                    )
                    if (dtmfInput.isNotEmpty()) {
                        IconButton(
                            onClick = { dtmfInput = "" },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.dialpad_clear),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.in_call_add_call_dtmf_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                )
            } else {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        selectedMatch = null
                        isSubmitting = false
                    },
                    label = { Text(stringResource(R.string.in_call_add_call_search_hint)) },
                    singleLine = true,
                    enabled = !isSubmitting,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("in_call_add_call_search")
                )
                Text(
                    text = stringResource(R.string.in_call_add_call_search_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 2.dp)
                )
            }

            if (mode == InCallKeypadMode.ADD_CALL) {
                if (searchQuery.isNotBlank() && matches.isEmpty() && selectedNumber == null) {
                    Text(
                        text = stringResource(R.string.in_call_add_call_no_matches),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    )
                } else if (matches.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.dialpad_matches_title),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        matches.forEach { match ->
                            Surface(
                                onClick = {
                                    RichHapticEngine.performHaptic(
                                        context,
                                        RichHapticEngine.HapticStyle.KEY_TICK
                                    )
                                    selectedMatch = match
                                    searchQuery = match.number
                                },
                                shape = MaterialTheme.shapes.small,
                                color = if (selectedMatch?.number == match.number) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    Color.Transparent
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("in_call_add_call_result_${match.number}")
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) {
                                    Text(
                                        text = match.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        HighlightedNumberText(
                                            fullNumber = match.number,
                                            query = searchQuery,
                                            style = MaterialTheme.typography.bodySmall,
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
                            }
                        }
                    }
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                for (r in 0 until 4) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (c in 0 until 3) {
                            val item = IN_CALL_KEYPAD_ITEMS[r * 3 + c]
                            InCallKeypadButton(
                                key = item.digit,
                                letters = item.letters,
                                onClick = {
                                    if (mode == InCallKeypadMode.ADD_CALL) {
                                        // Search mode only edits the query. It must never emit DTMF.
                                        searchQuery += item.digit
                                        selectedMatch = null
                                        isSubmitting = false
                                    } else {
                                        dtmfInput += item.digit
                                        onDtmf(item.digit[0])
                                    }
                                },
                                avatarShapeType = avatarShapeType,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(58.dp)
                                    .testTag("in_call_key_${item.digit}")
                            )
                        }
                    }
                }
            }

            if (mode == InCallKeypadMode.ADD_CALL) {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { submitAddCall() },
                    enabled = canConfirm,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("in_call_add_call_confirm")
                ) {
                    Text(
                        text = if (selectedNumber != null) {
                            stringResource(R.string.in_call_add_call_selected, selectedNumber)
                        } else {
                            stringResource(R.string.btn_call)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = onClose,
                modifier = Modifier.height(44.dp)
            ) {
                Text(
                    text = stringResource(R.string.close),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
fun InCallKeypadButton(
    key: String,
    avatarShapeType: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    letters: String = ""
) {
    val context = LocalContext.current
    val isExpressive = LocalM3Expressive.current
    val buttonShape = getAvatarShape(avatarShapeType)

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1.0f,
        animationSpec = spring(
            stiffness = Spring.StiffnessHigh,
            dampingRatio = Spring.DampingRatioMediumBouncy
        ),
        label = "keypad_button_scale"
    )

    val isAmoled = LocalAmoledMode.current
    val buttonColor = if (isAmoled) {
        Color(0xFF141414)
    } else if (isExpressive) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
    } else {
        MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
    }

    Surface(
        onClick = {
            RichHapticEngine.performHaptic(context, RichHapticEngine.HapticStyle.KEY_TICK)
            onClick()
        },
        interactionSource = interactionSource,
        modifier = modifier.scale(scale),
        shape = buttonShape,
        color = buttonColor,
        contentColor = MaterialTheme.colorScheme.primary,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = key,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 24.sp
            )
            if (letters.isNotBlank()) {
                Text(
                    text = letters,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    letterSpacing = 1.sp
                )
            }
        }
    }
}
