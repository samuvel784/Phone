/*
 * Copyright (C) 2026 Nova Dialer contributors
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

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.ui.viewmodel.DialerViewModel
import com.example.util.DuplicateContact
import com.example.util.DuplicateFinder
import com.example.util.DuplicateGroup
import com.example.util.RichHapticEngine
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

private sealed interface DuplicateAction {
    data class Merge(val group: DuplicateGroup, val keep: DuplicateContact) : DuplicateAction
    data class DeleteOthers(val group: DuplicateGroup, val keep: DuplicateContact) : DuplicateAction
    data class DeleteOne(val contact: DuplicateContact) : DuplicateAction
    data object MergeAll : DuplicateAction
    data object DeleteAll : DuplicateAction
}

@Composable
fun DuplicateFinderSettings(
    viewModel: DialerViewModel,
    cardBgColor: Color
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(true) }
    var groups by remember { mutableStateOf<List<DuplicateGroup>>(emptyList()) }
    var keepSelection by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var pendingAction by remember { mutableStateOf<DuplicateAction?>(null) }
    var toastMessage by remember { mutableStateOf<String?>(null) }

    val hasPermission = ContextCompat.checkSelfPermission(
        context, Manifest.permission.READ_CONTACTS
    ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.WRITE_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED

    fun keepIdFor(group: DuplicateGroup): Long =
        keepSelection[group.sharedNumber]
            ?: group.contacts.firstOrNull()?.contactId
            ?: -1L

    fun scan() {
        scope.launch {
            isLoading = true
            val result = DuplicateFinder.findDuplicates(context)
            groups = result
            keepSelection = result.associate { group ->
                group.sharedNumber to (group.contacts.firstOrNull()?.contactId ?: -1L)
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        if (hasPermission) scan() else isLoading = false
    }

    // Confirmation dialog — every destructive/permanent action is confirmed first.
    pendingAction?.let { action ->
        val (title, desc, confirmLabel) = when (action) {
            is DuplicateAction.Merge -> Triple(
                stringResource(R.string.dup_merge_confirm_title),
                stringResource(R.string.dup_merge_confirm_desc),
                stringResource(R.string.dup_merge)
            )
            is DuplicateAction.DeleteOthers -> Triple(
                stringResource(R.string.dup_delete_confirm_title),
                stringResource(R.string.dup_delete_confirm_desc),
                stringResource(R.string.dup_delete_others)
            )
            is DuplicateAction.DeleteOne -> Triple(
                stringResource(R.string.dup_delete_confirm_title),
                stringResource(R.string.dup_delete_one_confirm_desc),
                stringResource(R.string.delete)
            )
            is DuplicateAction.MergeAll -> Triple(
                stringResource(R.string.dup_merge_confirm_title),
                stringResource(R.string.dup_merge_all_confirm_desc),
                stringResource(R.string.dup_merge_all)
            )
            is DuplicateAction.DeleteAll -> Triple(
                stringResource(R.string.dup_delete_confirm_title),
                stringResource(R.string.dup_delete_all_confirm_desc),
                stringResource(R.string.dup_delete_all)
            )
        }
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text(title, fontWeight = FontWeight.Bold) },
            text = { Text(desc) },
            confirmButton = {
                Button(
                    onClick = {
                        pendingAction = null
                        scope.launch {
                            when (action) {
                                is DuplicateAction.Merge -> {
                                    val count = DuplicateFinder.merge(
                                        context, action.keep, action.group.contacts
                                    )
                                    toastMessage = context.getString(R.string.dup_merged, count)
                                }
                                is DuplicateAction.DeleteOthers -> {
                                    val others = action.group.contacts
                                        .filter { it.contactId != action.keep.contactId }
                                    val count = DuplicateFinder.delete(context, others)
                                    toastMessage = context.getString(R.string.dup_deleted, count)
                                }
                                is DuplicateAction.DeleteOne -> {
                                    val count = DuplicateFinder.delete(context, listOf(action.contact))
                                    toastMessage = context.getString(R.string.dup_deleted, count)
                                }
                                is DuplicateAction.MergeAll -> {
                                    var total = 0
                                    for (group in groups) {
                                        val keep = group.contacts.firstOrNull {
                                            it.contactId == keepIdFor(group)
                                        } ?: continue
                                        total += DuplicateFinder.merge(context, keep, group.contacts)
                                    }
                                    toastMessage = context.getString(R.string.dup_merged, total)
                                }
                                is DuplicateAction.DeleteAll -> {
                                    var total = 0
                                    for (group in groups) {
                                        val keepId = keepIdFor(group)
                                        val others = group.contacts.filter { it.contactId != keepId }
                                        total += DuplicateFinder.delete(context, others)
                                    }
                                    toastMessage = context.getString(R.string.dup_deleted, total)
                                }
                            }
                            RichHapticEngine.performHaptic(context, RichHapticEngine.HapticStyle.SUCCESS)
                            scan()
                        }
                    },
                    colors = when (action) {
                        is DuplicateAction.Merge, is DuplicateAction.MergeAll ->
                            ButtonDefaults.buttonColors()
                        else -> ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    }
                ) {
                    Text(confirmLabel)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingAction = null }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp)
            ) {
                Text(
                    text = stringResource(R.string.dup_finder_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = stringResource(R.string.dup_finder_sub),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (!hasPermission) {
            item {
                DuplicateInfoCard(
                    icon = Icons.Default.ContentCopy,
                    title = stringResource(R.string.dup_permission_title),
                    description = stringResource(R.string.dup_permission_desc),
                    cardBgColor = cardBgColor
                )
            }
            return@LazyColumn
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (isLoading) {
                        stringResource(R.string.dup_scanning)
                    } else {
                        stringResource(R.string.dup_groups_found, groups.size)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                TextButton(onClick = { scan() }, enabled = !isLoading) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.dup_rescan))
                }
            }
        }

        // Bulk actions across every group.
        if (!isLoading && groups.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = { pendingAction = DuplicateAction.MergeAll },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Merge, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.dup_merge_all))
                    }
                    OutlinedButton(
                        onClick = { pendingAction = DuplicateAction.DeleteAll },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.dup_delete_all))
                    }
                }
            }
        }

        if (isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        } else if (groups.isEmpty()) {
            item {
                DuplicateInfoCard(
                    icon = Icons.Default.Check,
                    title = stringResource(R.string.dup_none_title),
                    description = stringResource(R.string.dup_none_desc),
                    cardBgColor = cardBgColor
                )
            }
        } else {
            items(groups, key = { it.sharedNumber }) { group ->
                val keepId = keepIdFor(group)
                DuplicateGroupCard(
                    group = group,
                    keepContactId = keepId,
                    cardBgColor = cardBgColor,
                    onSelectKeep = { contact ->
                        keepSelection = keepSelection + (group.sharedNumber to contact.contactId)
                    },
                    onMerge = {
                        val keep = group.contacts.firstOrNull { it.contactId == keepId }
                        if (keep != null) pendingAction = DuplicateAction.Merge(group, keep)
                    },
                    onDeleteOthers = {
                        val keep = group.contacts.firstOrNull { it.contactId == keepId }
                        if (keep != null) pendingAction = DuplicateAction.DeleteOthers(group, keep)
                    },
                    onDeleteOne = { contact ->
                        pendingAction = DuplicateAction.DeleteOne(contact)
                    }
                )
            }
        }
    }

    toastMessage?.let { message ->
        ToastMessage(message = message, onDismiss = { toastMessage = null })
    }
}

@Composable
private fun DuplicateGroupCard(
    group: DuplicateGroup,
    keepContactId: Long,
    cardBgColor: Color,
    onSelectKeep: (DuplicateContact) -> Unit,
    onMerge: () -> Unit,
    onDeleteOthers: () -> Unit,
    onDeleteOne: (DuplicateContact) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text(
                text = group.sharedNumber,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            group.contacts.forEach { contact ->
                val selected = contact.contactId == keepContactId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectKeep(contact) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = selected, onClick = { onSelectKeep(contact) })
                    Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                        Text(
                            text = contact.name.ifBlank { contact.number },
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = contact.number,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { onDeleteOne(contact) }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onMerge,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Merge, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.dup_merge))
                }
                OutlinedButton(
                    onClick = onDeleteOthers,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.dup_delete_others))
                }
            }
        }
    }
}

@Composable
private fun DuplicateInfoCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    cardBgColor: Color
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun ToastMessage(message: String, onDismiss: () -> Unit) {
    LaunchedEffect(message) {
        kotlinx.coroutines.delay(2200.milliseconds)
        onDismiss()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 96.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
    }
}
