/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.model.Contact
import com.example.model.LabeledNumber
import com.example.ui.theme.AppShapes
import com.example.ui.theme.LocalAmoledMode
import com.example.ui.viewmodel.DialerViewModel
import com.example.util.ContactRingtonePrefs
import com.example.util.ContactShareUtils
import com.example.util.RichHapticEngine

/**
 * A per-contact profile shown as a bottom sheet. Everything about one person lives here:
 * every phone number (tap to call, or message it), favourite, edit, share, copy, block and
 * delete. Tapping a contact on the Home list opens this instead of dialling blindly.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ContactProfileSheet(
    contact: Contact,
    viewModel: DialerViewModel,
    onCall: (Contact) -> Unit,
    onToggleFavorite: (Contact) -> Unit,
    onEditContact: (Contact) -> Unit,
    onDeleteContact: (Contact) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isAmoled = LocalAmoledMode.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val numbers = remember(contact) { contact.getAllNumbers() }
    val primaryNumber = numbers.firstOrNull()?.number ?: contact.number

    val blocked by viewModel.blockedNumbersFlow.collectAsStateWithLifecycle()
    val blockedEntry = remember(blocked, contact) {
        blocked.firstOrNull { ContactRingtonePrefs.numbersMatch(it.number, contact.number) }
    }

    var showShareDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showShareDialog) {
        AlertDialog(
            onDismissRequest = { showShareDialog = false },
            title = { Text(stringResource(R.string.share_recording)) },
            text = { Text("Choose contact sharing format:") },
            confirmButton = {
                Button(onClick = {
                    showShareDialog = false
                    ContactShareUtils.shareAsVCard(context, contact)
                    onDismiss()
                }) {
                    Text("Share as vCard (.vcf)")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showShareDialog = false
                    ContactShareUtils.shareAsText(context, contact)
                    onDismiss()
                }) {
                    Text("Share as Text")
                }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.btn_delete)) },
            text = { Text("Delete contact \"${contact.name.ifBlank { primaryNumber }}\"? This will permanently remove the contact.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDismiss()
                        onDeleteContact(contact)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.btn_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    val ringtonePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.let {
            androidx.core.content.IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        }
        if (uri != null) {
            val existing = ContactRingtonePrefs.get(context, contact.number)
            ContactRingtonePrefs.set(context, contact.number, uri.toString(), existing?.pattern ?: ContactRingtonePrefs.PATTERN_DEFAULT)
            Toast.makeText(context, context.getString(R.string.ringtone_assigned_toast), Toast.LENGTH_SHORT).show()
        } else {
            ContactRingtonePrefs.remove(context, contact.number)
            Toast.makeText(context, context.getString(R.string.ringtone_cleared_toast), Toast.LENGTH_SHORT).show()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = if (isAmoled) Color(0xFF0A0A0A) else MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
        ) {
            // ── Header: avatar, name, favourite toggle ─────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                ContactBoxAvatar(contact = contact, size = 64.dp, showStar = false)
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = contact.name.ifBlank { primaryNumber },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (primaryNumber.isNotBlank()) {
                        Text(
                            text = primaryNumber,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(onClick = {
                    RichHapticEngine.performHaptic(context, RichHapticEngine.HapticStyle.KEY_TICK)
                    onToggleFavorite(contact)
                }) {
                    Icon(
                        imageVector = if (contact.favorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = stringResource(R.string.tab_favorites),
                        tint = if (contact.favorite) FavoriteGold else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Primary call / message ─────────────────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        RichHapticEngine.performHaptic(context, RichHapticEngine.HapticStyle.KEY_TICK)
                        onCall(contact.copy(number = primaryNumber, label = numbers.firstOrNull()?.label ?: contact.label))
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.action_call))
                }
                FilledTonalButton(
                    onClick = {
                        sendMessage(context, primaryNumber)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Message, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.action_message))
                }
            }

            // ── All phone numbers ─────────────────────────────────────────────────
            if (numbers.size > 1) {
                Spacer(modifier = Modifier.height(20.dp))
                numbers.forEach { num ->
                    NumberRow(
                        num = num,
                        onCall = {
                            onCall(contact.copy(number = num.number, label = num.label))
                            onDismiss()
                        },
                        onMessage = {
                            sendMessage(context, num.number)
                            onDismiss()
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── More options ──────────────────────────────────────────────────────
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                maxItemsInEachRow = 4
            ) {
                DetailActionItem(
                    icon = if (contact.favorite) Icons.Default.Star else Icons.Default.StarBorder,
                    label = stringResource(R.string.tab_favorites),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = { onToggleFavorite(contact) }
                )
                DetailActionItem(
                    icon = Icons.Default.MusicNote,
                    label = "Ringtone",
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    onClick = {
                        val existing = ContactRingtonePrefs.get(context, contact.number)?.ringtoneUri?.toUri()
                        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Ringtone for ${contact.name}")
                            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                        }
                        ringtonePickerLauncher.launch(intent)
                    }
                )
                DetailActionItem(
                    icon = Icons.Default.Share,
                    label = stringResource(R.string.share_recording),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = {
                        showShareDialog = true
                    }
                )
                DetailActionItem(
                    icon = Icons.Default.ContentCopy,
                    label = stringResource(R.string.dialpad_copy),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = {
                        copyToClipboard(context, primaryNumber)
                        Toast.makeText(context, context.getString(R.string.toast_number_copied), Toast.LENGTH_SHORT).show()
                    }
                )
                DetailActionItem(
                    icon = Icons.Default.Block,
                    label = stringResource(if (blockedEntry == null) R.string.block else R.string.unblock),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = {
                        if (blockedEntry == null) {
                            viewModel.addBlockedNumber(contact.number)
                        } else {
                            viewModel.removeBlockedNumber(blockedEntry.number)
                        }
                    }
                )
                DetailActionItem(
                    icon = Icons.Default.Delete,
                    label = stringResource(R.string.btn_delete),
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                    contentColor = MaterialTheme.colorScheme.error,
                    onClick = {
                        showDeleteConfirm = true
                    }
                )
                DetailActionItem(
                    icon = Icons.Default.Edit,
                    label = stringResource(R.string.btn_edit),
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    onClick = {
                        onDismiss()
                        onEditContact(contact)
                    }
                )
            }
        }
    }
}

@Composable
private fun NumberRow(
    num: LabeledNumber,
    onCall: () -> Unit,
    onMessage: () -> Unit
) {
    Surface(
        onClick = onCall,
        shape = AppShapes.Chip,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Call,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = localizeContactLabel(num.label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Start
                )
                Text(
                    text = num.number,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = onMessage) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Message,
                    contentDescription = stringResource(R.string.action_message),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private val FavoriteGold = Color(0xFFEAB308)

private fun sendMessage(context: Context, number: String) {
    if (number.isBlank()) return
    try {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = "smsto:$number".toUri()
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (_: Exception) {
    }
}

private fun copyToClipboard(context: Context, number: String) {
    try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("phone", number))
    } catch (_: Exception) {
    }
}
