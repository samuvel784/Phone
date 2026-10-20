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

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.model.Contact
import com.example.model.getInitials
import com.example.ui.theme.AppShapes
import com.example.ui.viewmodel.DialerViewModel
import com.example.util.RichHapticEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/** Distinct box designs per page so Home and Favorites don't look identical. */
enum class ContactCardStyle { HOME, FAVORITES }

/** Flattened grid model: letter section headers + contact cards. */
private sealed interface ContactGridItem {
    data class Header(val letter: Char) : ContactGridItem
    data class Person(val contact: Contact) : ContactGridItem
}

@Composable
fun ContactsTabContent(
    viewModel: DialerViewModel,
    favoriteContacts: List<Contact>,
    onCallClick: (Contact) -> Unit,
    onAddContactClick: () -> Unit,
    onToggleFavorite: (Contact) -> Unit,
    hasPermission: Boolean = true,
    isLoading: Boolean = false,
    onRequestPermission: () -> Unit = {},
    onEditContact: (Contact) -> Unit = {},
    onDeleteContact: (Contact) -> Unit = {},
    startWithFavoritesOnly: Boolean = false,
    cardStyle: ContactCardStyle = ContactCardStyle.HOME
) {
    val gridState = androidx.compose.runtime.saveable.rememberSaveable(saver = androidx.compose.foundation.lazy.grid.LazyGridState.Saver) {
        androidx.compose.foundation.lazy.grid.LazyGridState()
    }
    val coroutineScope = rememberCoroutineScope()

    val allContacts by viewModel.allContactsFlow.collectAsStateWithLifecycle()
    val selectedAccountFilter by viewModel.selectedAccountFilter
    val searchQuery by viewModel.searchQuery

    // Base list depends on the mode (favorites-only vs all), respecting the account filter.
    val baseContacts = remember(startWithFavoritesOnly, favoriteContacts, allContacts, selectedAccountFilter) {
        when {
            startWithFavoritesOnly -> favoriteContacts.sortedBy { it.name.uppercase(Locale.ROOT) }
            selectedAccountFilter.isBlank() -> allContacts
            selectedAccountFilter.equals("Phone", ignoreCase = true) -> allContacts.filter {
                it.accountName.isBlank() || it.accountName.equals("Phone", ignoreCase = true) || it.accountType.contains("local", ignoreCase = true)
            }
            else -> allContacts.filter { it.accountName == selectedAccountFilter }
        }
    }

    // Apply the shared header search to the visible list.
    val contacts = remember(baseContacts, searchQuery) {
        val query = searchQuery.trim()
        if (query.isEmpty()) {
            baseContacts
        } else {
            baseContacts.filter {
                it.name.contains(query, ignoreCase = true) || it.number.contains(query, ignoreCase = true)
            }
        }
    }

    val alphabet = remember(contacts) {
        val uniqueLetters = contacts.asSequence()
            .map { it.name.trim() }
            .filter { it.isNotEmpty() }
            .map { it.first().uppercaseChar() }
            .distinct()
            .toList()

        val letterChars = uniqueLetters.filter { it.isLetter() }.sorted()
        val otherChars = uniqueLetters.filter { !it.isLetter() }

        val result = mutableListOf<Char>()
        result.addAll(letterChars)
        if (otherChars.isNotEmpty() || result.isEmpty()) {
            result.add('#')
        }

        if (result.size <= 1) ('A'..'Z').toList() + '#' else result
    }

    // Flattened list with section headers so the grid can render letter rows.
    val gridItems = remember(contacts) {
        val list = ArrayList<ContactGridItem>(contacts.size + 12)
        var lastLetter: Char? = null
        contacts.forEach { contact ->
            val firstChar = contact.name.trim().firstOrNull()?.uppercaseChar() ?: '#'
            val letter = if (firstChar.isLetter()) firstChar else '#'
            if (letter != lastLetter) {
                list.add(ContactGridItem.Header(letter))
                lastLetter = letter
            }
            list.add(ContactGridItem.Person(contact))
        }
        list
    }

    val letterToIndex = remember(gridItems) {
        val map = HashMap<Char, Int>()
        gridItems.forEachIndexed { index, item ->
            if (item is ContactGridItem.Header && !map.containsKey(item.letter)) {
                map[item.letter] = index
            }
        }
        map
    }

    val activeLetter = remember(gridState, alphabet, gridItems) {
        derivedStateOf {
            when (val item = gridItems.getOrNull(gridState.firstVisibleItemIndex)) {
                is ContactGridItem.Header -> item.letter
                is ContactGridItem.Person -> {
                    val firstChar = item.contact.name.trim().firstOrNull()?.uppercaseChar() ?: '#'
                    if (firstChar.isLetter()) firstChar else '#'
                }
                null -> alphabet.firstOrNull() ?: '#'
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp)
        ) {
            if (!hasPermission && !isLoading) {
                PermissionPromptCard(onRequestPermission = onRequestPermission)
            }

            if (hasPermission && !isLoading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AddContactPill(onClick = onAddContactClick)
                }

                if (startWithFavoritesOnly) {
                    val isDefaultDialer by viewModel.isDefaultDialer
                    val androidCtx = LocalContext.current
                    val sosContacts = remember(androidCtx) { com.example.telecom.EmergencySosPrefs.getContacts(androidCtx) }
                    var dismissedSafetyItems by remember { mutableStateOf(emptySet<String>()) }

                    val safetyActions = remember(isDefaultDialer, hasPermission, sosContacts, dismissedSafetyItems) {
                        val list = mutableListOf<SafetyActionItem>()
                        if (!isDefaultDialer && "default_dialer" !in dismissedSafetyItems) {
                            list.add(
                                SafetyActionItem(
                                    id = "default_dialer",
                                    title = "Set as Default Phone App",
                                    reason = "Required for caller ID, screening, and call history",
                                    actionText = "Set Up",
                                    icon = Icons.Default.Call,
                                    onAction = onRequestPermission
                                )
                            )
                        }
                        if (sosContacts.isEmpty() && "sos_contacts" !in dismissedSafetyItems) {
                            list.add(
                                SafetyActionItem(
                                    id = "sos_contacts",
                                    title = "Set Emergency SOS Contacts",
                                    reason = "Configure contacts to alert during emergencies",
                                    actionText = "Set Up",
                                    icon = Icons.Default.Star,
                                    onAction = { viewModel.selectTabBySlotKey("SETTINGS") }
                                )
                            )
                        }
                        list
                    }

                    if (safetyActions.isNotEmpty()) {
                        SafetyActionCenter(
                            items = safetyActions,
                            onDismissItem = { id -> dismissedSafetyItems = dismissedSafetyItems + id }
                        )
                    }
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(30.dp),
                        strokeWidth = 3.dp
                    )
                }
            } else if (contacts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyStateIllustration(
                        title = if (startWithFavoritesOnly) stringResource(R.string.no_favorites_title) else stringResource(R.string.no_contacts_title),
                        subtitle = if (startWithFavoritesOnly) stringResource(R.string.no_favorites_subtitle) else stringResource(R.string.no_contacts_subtitle)
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(if (cardStyle == ContactCardStyle.FAVORITES) 2 else 1),
                    state = gridState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(end = 26.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(
                        if (cardStyle == ContactCardStyle.FAVORITES) 12.dp else 8.dp
                    ),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 140.dp)
                ) {
                    // One lazy "items" call for the whole list. Registering every contact as its own item() inside a
                    // forEach made the grid rebuild thousands of item definitions each time it recomposed.
                    items(
                        count = gridItems.size,
                        key = { index ->
                            when (val entry = gridItems[index]) {
                                is ContactGridItem.Header -> "header_${entry.letter}"
                                is ContactGridItem.Person -> "contact_${entry.contact.id}_${entry.contact.number}"
                            }
                        },
                        span = { index ->
                            if (gridItems[index] is ContactGridItem.Header) GridItemSpan(maxLineSpan) else GridItemSpan(1)
                        },
                        contentType = { index ->
                            if (gridItems[index] is ContactGridItem.Header) "header" else "contact"
                        }
                    ) { index ->
                        when (val entry = gridItems[index]) {
                            is ContactGridItem.Header -> LetterSectionHeader(letter = entry.letter)
                            is ContactGridItem.Person -> Box(modifier = Modifier.animateItem()) {
                                ContactBoxCard(
                                    contact = entry.contact,
                                    style = cardStyle,
                                    viewModel = viewModel,
                                    onCall = onCallClick,
                                    onToggleFavorite = onToggleFavorite,
                                    onEditContact = onEditContact,
                                    onDeleteContact = onDeleteContact
                                )
                            }
                        }
                    }
                }
            }
        }

        // Modern A–Z scroller rail with floating preview bubble
        if (hasPermission && !isLoading && contacts.isNotEmpty()) {
            val haptic = LocalHapticFeedback.current
            val maxScrollTarget = (gridItems.size - 1).coerceAtLeast(0)

            var previewLetter by remember { mutableStateOf<Char?>(null) }
            var isDragging by remember { mutableStateOf(false) }

            LaunchedEffect(previewLetter, isDragging) {
                if (!isDragging && previewLetter != null) {
                    delay(700)
                    previewLetter = null
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(34.dp)
                    .padding(vertical = 44.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f))
                    .pointerInput(alphabet, letterToIndex, maxScrollTarget) {
                        detectTapGestures { offset ->
                            val index = (offset.y / size.height * alphabet.size)
                                .toInt()
                                .coerceIn(0, alphabet.size - 1)
                            val char = alphabet[index]
                            previewLetter = char
                            isDragging = false
                            letterToIndex[char]?.let { targetIndex ->
                                val safeIndex = targetIndex.coerceIn(0, maxScrollTarget)
                                coroutineScope.launch {
                                    gridState.animateScrollToItem(safeIndex)
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            }
                        }
                    }
                    .pointerInput(alphabet, letterToIndex, maxScrollTarget) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                isDragging = true
                                val index = (offset.y / size.height * alphabet.size)
                                    .toInt()
                                    .coerceIn(0, alphabet.size - 1)
                                previewLetter = alphabet[index]
                            },
                            onDragEnd = {
                                isDragging = false
                                previewLetter = null
                            },
                            onDragCancel = {
                                isDragging = false
                                previewLetter = null
                            }
                        ) { change, _ ->
                            val index = (change.position.y / size.height * alphabet.size)
                                .toInt()
                                .coerceIn(0, alphabet.size - 1)
                            val char = alphabet[index]
                            previewLetter = char
                            letterToIndex[char]?.let { targetIndex ->
                                val safeIndex = targetIndex.coerceIn(0, maxScrollTarget)
                                coroutineScope.launch {
                                    gridState.scrollToItem(safeIndex)
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                        }
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                alphabet.forEach { char ->
                    val isActive = char == activeLetter.value
                    val bgColor by animateColorAsState(
                        targetValue = if (isActive) MaterialTheme.colorScheme.primary else Color.Transparent,
                        label = "railBg"
                    )
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(bgColor)
                    ) {
                        Text(
                            text = char.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = if (isActive) 10.sp else 8.5.sp,
                            fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Medium,
                            color = if (isActive) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                            }
                        )
                    }
                }
            }

            // Floating letter preview bubble (center of the screen, like modern dialers)
            previewLetter?.let { letter ->
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(84.dp)
                        .shadow(14.dp, CircleShape)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = letter.toString(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

/* ------------------------------------------------------------------------------------------ */
/*  Small building blocks                                                                      */
/* ------------------------------------------------------------------------------------------ */

/** Modern rounded letter section header with a fading divider. */
@Composable
private fun LetterSectionHeader(letter: Char) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = letter.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.outlineVariant,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0f)
                        )
                    )
                )
        )
    }
}

/** Gradient pill "Add contact" action with a soft drop shadow and press-scale. */
@Composable
private fun AddContactPill(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "addPillScale"
    )

    Row(
        modifier = Modifier
            .testTag("add_contact_fab")
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(10.dp, CircleShape)
            .clip(CircleShape)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.82f)
                    )
                )
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 18.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.action_add_contact),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}

/** Rounded, tonal permission card. */
@Composable
private fun PermissionPromptCard(onRequestPermission: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f),
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.permissions_required),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.contacts_perm_desc),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onRequestPermission,
                shape = CircleShape,
                modifier = Modifier.height(42.dp)
            ) {
                Text(
                    text = stringResource(R.string.enable_contacts_perm),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/* ------------------------------------------------------------------------------------------ */
/*  Contact card                                                                               */
/* ------------------------------------------------------------------------------------------ */

/**
 * A single contact "box". Tapping opens the full [ContactProfileSheet] (numbers, message,
 * favourite, edit, share, copy, block, delete); the trailing call button on the Home layout
 * dials the primary number directly.
 *
 * [ContactCardStyle.HOME] lays the avatar left (wide box with a quick-call bubble);
 * [ContactCardStyle.FAVORITES] centres the avatar on top with a gold star so the page
 * looks distinct.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContactBoxCard(
    contact: Contact,
    style: ContactCardStyle,
    viewModel: DialerViewModel,
    onCall: (Contact) -> Unit,
    onToggleFavorite: (Contact) -> Unit,
    onEditContact: (Contact) -> Unit,
    onDeleteContact: (Contact) -> Unit
) {
    val context = LocalContext.current
    var showProfile by remember { mutableStateOf(false) }

    val numbers = remember(contact) { contact.getAllNumbers() }
    val primaryNumber = numbers.firstOrNull()?.number ?: contact.number
    val primaryLabel = numbers.firstOrNull()?.label ?: contact.label

    val isFavorites = style == ContactCardStyle.FAVORITES

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.975f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "cardScale"
    )

    val shape = if (isFavorites) RoundedCornerShape(28.dp) else RoundedCornerShape(24.dp)

    // Solid tonal cards with a hairline border: crisp in Day, Midnight and Night. Favourites get a soft tint of
    // the accent colour at the top, so they stand out without looking heavy.
    val baseColor = MaterialTheme.colorScheme.surfaceContainerLow
    val tint = MaterialTheme.colorScheme.primaryContainer
    val background: Brush = if (isFavorites) {
        Brush.verticalGradient(listOf(androidx.compose.ui.graphics.lerp(baseColor, tint, 0.55f), baseColor))
    } else {
        SolidColor(baseColor)
    }
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(background)
            .border(1.dp, borderColor, shape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    RichHapticEngine.performHaptic(context, RichHapticEngine.HapticStyle.KEY_TICK)
                    showProfile = true
                },
                onLongClick = {
                    RichHapticEngine.performHaptic(context, RichHapticEngine.HapticStyle.HEAVY_CLICK)
                    showProfile = true
                }
            )
            .padding(
                start = 14.dp,
                end = if (isFavorites) 14.dp else 10.dp,
                top = if (isFavorites) 18.dp else 12.dp,
                bottom = if (isFavorites) 16.dp else 12.dp
            )
    ) {
        if (isFavorites) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ContactBoxAvatar(contact = contact, size = 58.dp, showStar = true)
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = contact.name.ifBlank { contact.number },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = contact.number,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ContactBoxAvatar(contact = contact, size = 48.dp, showStar = contact.favorite)
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = contact.name.ifBlank { contact.number },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = contact.number,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                QuickCallBubble(
                    onClick = {
                        RichHapticEngine.performHaptic(context, RichHapticEngine.HapticStyle.KEY_TICK)
                        onCall(contact.copy(number = primaryNumber, label = primaryLabel))
                    }
                )
            }
        }
    }

    if (showProfile) {
        ContactProfileSheet(
            contact = contact,
            viewModel = viewModel,
            onCall = onCall,
            onToggleFavorite = onToggleFavorite,
            onEditContact = onEditContact,
            onDeleteContact = onDeleteContact,
            onDismiss = { showProfile = false }
        )
    }
}

/** Circular tonal call shortcut used on the Home contact rows. */
@Composable
private fun QuickCallBubble(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "callScale"
    )

    Box(
        modifier = Modifier
            .size(40.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.13f))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Call,
            contentDescription = stringResource(R.string.action_call),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(19.dp)
        )
    }
}

/* ------------------------------------------------------------------------------------------ */
/*  Avatar                                                                                     */
/* ------------------------------------------------------------------------------------------ */

@Composable
internal fun ContactBoxAvatar(
    contact: Contact,
    size: Dp,
    showStar: Boolean
) {
    Box(contentAlignment = Alignment.Center) {
        // Soft halo ring behind the avatar for a modern, layered look.
        Box(
            modifier = Modifier
                .size(size + 6.dp)
                .clip(AppShapes.Avatar)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
        )
        Box(
            modifier = Modifier
                .size(size)
                .clip(AppShapes.Avatar)
                .background(contact.avatarBg),
            contentAlignment = Alignment.Center
        ) {
            // A stale/removed photo URI must fall back to the initials, never leave a blank circle.
            var imageFailed by remember(contact.photoUri) { mutableStateOf(false) }
            if (contact.photoUri.isNotEmpty() && !imageFailed) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(contact.photoUri)
                        .size(128, 128)
                        .crossfade(false)
                        .build(),
                    contentDescription = contact.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    onError = { imageFailed = true }
                )
            } else {
                val isSaved = contact.name.isNotBlank() && contact.name != contact.number && contact.name != "Unknown"
                if (isSaved) {
                    Text(
                        text = contact.avatarText.ifEmpty { getInitials(contact.name) },
                        style = MaterialTheme.typography.titleMedium,
                        color = contact.avatarTextColor,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = contact.avatarTextColor,
                        modifier = Modifier.size(size / 2)
                    )
                }
            }
        }
        if (showStar) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = Color(0xFFEAB308),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
