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

import android.content.Intent
import android.media.MediaPlayer
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ContactCache
import com.example.R
import com.example.model.CallRecord
import com.example.model.CallRecording
import com.example.model.CallType
import com.example.model.Contact
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.example.ui.theme.*
import com.example.ui.viewmodel.DialerViewModel
import com.example.util.CallAudioRecorder
import com.example.util.RichHapticEngine
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale
import androidx.core.net.toUri
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecentCallRow(
    group: CallGroup,
    onCallClick: () -> Unit,
    onDeleteRecord: (Int) -> Unit,
    getHistory: suspend (String) -> List<CallRecord>,
    viewModel: DialerViewModel,
    onHistoryClick: (String) -> Unit,
    /** Hoisted by the list so a screen of N rows does not open N blocked-number collectors. */
    blockedNumbers: Set<String>
) {
    val record = group.primary
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(false) }

    // SQUIRCLE ARCHITECTURE: Strictly enforce AppShapes.Avatar (percent = 32)
    val avatarShape = AppShapes.Avatar

    // Cards sit one tone above the page and get a soft hairline border, so lists look crisp in every theme.
    val baseCard = MaterialTheme.colorScheme.surfaceContainerLow
    val expandedCard = MaterialTheme.colorScheme.surfaceContainer
    val containerColor by animateColorAsState(
        targetValue = if (isExpanded) expandedCard else baseCard,
        animationSpec = NovaMotion.fade(NovaMotion.QUICK),
        label = "recentCardColor"
    )
    val cardSource = remember { MutableInteractionSource() }

    val isRowSwipeEnabled by viewModel.isRowSwipeEnabled

    val allContacts by viewModel.allContactsFlow.collectAsStateWithLifecycle()
    val currentContact = remember(record.number, allContacts) {
        if (record.number.isNotBlank()) ContactCache.getContact(record.number) else null
    }
    val resolvedName = currentContact?.name
        ?: if (record.name == "Unknown" || record.name.isBlank() || record.number.isBlank() || record.name in listOf("-1", "-2", "-3")) {
            stringResource(R.string.unknown)
        } else {
            record.number
        }

    // Prepared once for the whole list (see RecentsLookup): a row only does cheap map lookups.
    val lookup = LocalRecentsLookup.current

    val rowCardContent = @Composable {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .pressScale(cardSource, 0.985f)
                .animateContentSize(animationSpec = NovaMotion.smooth())
                .clickable(interactionSource = cardSource, indication = LocalIndication.current) {
                    RichHapticEngine.performHaptic(context, RichHapticEngine.HapticStyle.KEY_TICK)
                    isExpanded = !isExpanded
                },
            colors = CardDefaults.cardColors(containerColor = containerColor),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            shape = AppShapes.Card
        ) {
            Column {
                ListItem(
                    headlineContent = {
                        Column(
                            modifier = Modifier.offset(x = (-8).dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = resolvedName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    lineHeight = 18.sp,
                                    color = if (record.type == CallType.MISSED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f, fill = false),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (group.calls.size > 1) {
                                    Text(
                                        text = " (${group.calls.size})",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                val (icon, iconColor) = when (record.type) {
                                    CallType.MISSED -> Icons.AutoMirrored.Filled.CallMissed to getMissedCallColor()
                                    CallType.OUTGOING -> Icons.AutoMirrored.Filled.CallMade to getDialedCallColor()
                                    CallType.INCOMING -> Icons.AutoMirrored.Filled.CallReceived to getReceivedCallColor()
                                }
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = iconColor,
                                    modifier = Modifier.size(13.dp)
                                )

                                val physicalSimCount = lookup.physicalSimCount
                                if (physicalSimCount > 1) {
                                    val isSim1 = record.simSlot <= 1
                                    Surface(
                                        shape = AppShapes.Chip,
                                        color = if (isSim1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                                        border = if (!isSim1) BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)) else null
                                    ) {
                                        Text(
                                            text = stringResource(if (isSim1) R.string.sim_1 else R.string.sim_2),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (isSim1) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 4.5.dp, vertical = 0.5.dp)
                                        )
                                    }
                                }

                                if (record.isVerified) {
                                    val verifiedGreen = getCallGreenColor()
                                    Surface(
                                        shape = AppShapes.Chip,
                                        color = verifiedGreen.copy(alpha = 0.15f),
                                        border = BorderStroke(0.5.dp, verifiedGreen.copy(alpha = 0.5f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = verifiedGreen,
                                                modifier = Modifier.size(10.dp)
                                            )
                                            Text(
                                                text = stringResource(R.string.caller_verified),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = verifiedGreen
                                            )
                                        }
                                    }
                                }

                                val extraInfo = when {
                                    record.isVerified && record.number.isNotBlank() -> "${record.number} • "
                                    record.label.isNotBlank() && !record.label.equals("Mobile", ignoreCase = true) -> "${localizeContactLabel(record.label)} • "
                                    else -> ""
                                }
                                Text(
                                    text = "$extraInfo${record.timestamp}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    supportingContent = null,
                    leadingContent = {
                        Box(
                            modifier = Modifier
                                .offset(x = (-8).dp)
                                .size(42.dp)
                                .clip(avatarShape)
                                .background(record.avatarBg.copy(alpha = 0.85f)),
                            contentAlignment = Alignment.Center
                        ) {
                            // A stale/removed photo URI falls back to initials instead of a blank circle.
                            var imageFailed by remember(record.photoUri) { mutableStateOf(false) }
                            if (record.photoUri.isNotEmpty() && !imageFailed) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(record.photoUri)
                                        .size(128, 128)
                                        .crossfade(false)
                                        .build(),
                                    contentDescription = record.name,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                    onError = { imageFailed = true }
                                )
                            } else {
                                val isSaved = record.name != record.number && record.name != "Unknown" && record.name.isNotBlank()
                                if (isSaved) {
                                    Text(
                                        text = record.avatarText,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = record.avatarTextColor,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = record.avatarTextColor,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    },
                    trailingContent = {
                        IconButton(onClick = {
                            RichHapticEngine.performHaptic(context, RichHapticEngine.HapticStyle.SUCCESS)
                            onCallClick()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = stringResource(R.string.action_call),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )

                val isBlocked = isBlockedNumber(record.number, blockedNumbers)

                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )

                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            maxItemsInEachRow = 4
                        ) {
                            val isContact = record.name != record.number

                            DetailActionItem(
                                icon = Icons.AutoMirrored.Filled.Message,
                                label = stringResource(R.string.send_sms),
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                                            data = "smsto:${record.number}".toUri()
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        Toast.makeText(context, context.getString(R.string.error_open_messages), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )

                            DetailActionItem(
                                icon = Icons.Default.Block,
                                label = if (isBlocked) stringResource(R.string.unblock) else stringResource(R.string.block),
                                containerColor = if (isBlocked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                                contentColor = if (isBlocked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.error,
                                onClick = {
                                    RichHapticEngine.performHaptic(context, RichHapticEngine.HapticStyle.KEY_TICK)
                                    if (isBlocked) {
                                        viewModel.removeBlockedNumber(record.number)
                                    } else {
                                        viewModel.addBlockedNumber(record.number)
                                    }
                                }
                            )

                            DetailActionItem(
                                icon = Icons.Default.History,
                                label = stringResource(R.string.history),
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                onClick = {
                                    RichHapticEngine.performHaptic(context, RichHapticEngine.HapticStyle.KEY_TICK)
                                    onHistoryClick(record.number)
                                }
                            )

                            DetailActionItem(
                                icon = if (isContact) Icons.Default.Edit else Icons.Default.PersonAdd,
                                label = if (isContact) stringResource(R.string.btn_edit) else stringResource(R.string.action_add_contact),
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                onClick = {
                                    RichHapticEngine.performHaptic(context, RichHapticEngine.HapticStyle.KEY_TICK)
                                    if (isContact) {
                                        viewModel.oldContactToEdit.value = Contact(
                                            number = record.number,
                                            name = record.name,
                                            label = record.label,
                                            favorite = false,
                                            avatarText = record.avatarText,
                                            avatarBgValue = record.avatarBgValue,
                                            avatarTextColorValue = record.avatarTextColorValue,
                                            email = ""
                                        )
                                        viewModel.editContactName.value = record.name
                                        viewModel.editContactNumber.value = record.number
                                        viewModel.editContactLabel.value = record.label
                                        viewModel.isEditContactDialogVisible.value = true
                                    } else {
                                        viewModel.newContactName.value = ""
                                        viewModel.newContactNumber.value = record.number
                                        viewModel.newContactLabel.value = "Mobile"
                                        viewModel.isAddContactDialogVisible.value = true
                                    }
                                }
                            )

                        }
                    }
                }
            }
        }
    }

    if (isRowSwipeEnabled) {
        val dismissState = rememberSwipeToDismissBoxState(
            positionalThreshold = { totalDistance -> totalDistance * 0.75f },
            confirmValueChange = { dismissValue ->
                when (dismissValue) {
                    SwipeToDismissBoxValue.StartToEnd -> {
                        RichHapticEngine.performHaptic(context, RichHapticEngine.HapticStyle.SUCCESS)
                        onCallClick()
                        false
                    }
                    SwipeToDismissBoxValue.EndToStart -> {
                        RichHapticEngine.performHaptic(context, RichHapticEngine.HapticStyle.CLICK)
                        try {
                            val intent = Intent(Intent.ACTION_SENDTO, "smsto:${record.number}".toUri()).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            Toast.makeText(context, context.getString(R.string.error_open_messages), Toast.LENGTH_SHORT).show()
                        }
                        false
                    }
                    else -> false
                }
            }
        )

        SwipeToDismissBox(
            state = dismissState,
            backgroundContent = {
                val direction = dismissState.dismissDirection
                val bgContainerColor = when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> getCallGreenColor()
                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.primaryContainer
                    else -> Color.Transparent
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(MaterialTheme.shapes.medium)
                        .background(bgContainerColor)
                        .padding(horizontal = 20.dp),
                    contentAlignment = if (direction == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
                ) {
                    if (direction == SwipeToDismissBoxValue.StartToEnd) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Call, contentDescription = stringResource(R.string.action_swipe_call), tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.action_swipe_call), color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    } else if (direction == SwipeToDismissBoxValue.EndToStart) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.action_swipe_message), color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.Default.Email, contentDescription = stringResource(R.string.action_swipe_message), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            }
        ) {
            rowCardContent()
        }
    } else {
        rowCardContent()
    }

}
