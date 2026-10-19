/*
 * Copyright (C) 2026 NovaDial contributors
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
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.IntentCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Vibration
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.model.Contact
import com.example.ui.theme.AppShapes
import com.example.ui.viewmodel.DialerViewModel
import com.example.util.ContactRingtonePrefs
import com.example.util.RichHapticEngine
import androidx.core.net.toUri

/**
 * Per-contact ringtone and vibration assignments. Assignments are used by the
 * Fake Call simulator (real incoming ringtone playback is controlled by the OS,
 * which is a documented platform limitation).
 */
@Composable
fun ContactRingtonesSettings(
    viewModel: DialerViewModel,
    cardBgColor: Color
) {
    val context = LocalContext.current
    val allContacts by viewModel.allContactsFlow.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteContacts.collectAsStateWithLifecycle()

    val contacts = remember(allContacts, favorites) {
        val byNumber = LinkedHashMap<String, Contact>()
        favorites.forEach { if (it.number.isNotBlank()) byNumber[it.number.trim()] = it }
        allContacts.forEach { if (it.number.isNotBlank() && !byNumber.containsKey(it.number.trim())) byNumber[it.number.trim()] = it }
        byNumber.values.take(60)
    }

    var mappings by remember { mutableStateOf(ContactRingtonePrefs.getMap(context)) }
    fun refreshMappings() {
        mappings = ContactRingtonePrefs.getMap(context)
    }

    var pendingNumber by remember { mutableStateOf<String?>(null) }
    var patternMenuFor by remember { mutableStateOf<String?>(null) }
    val previewRingtone = remember { mutableStateOf<Ringtone?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { previewRingtone.value?.stop() }
        }
    }

    fun stopPreview() {
        runCatching { previewRingtone.value?.stop() }
        previewRingtone.value = null
    }

    fun playAssignment(number: String, assignment: ContactRingtonePrefs.Assignment?) {
        stopPreview()
        val a = assignment ?: return
        if (a.pattern != ContactRingtonePrefs.PATTERN_DEFAULT && a.pattern != ContactRingtonePrefs.PATTERN_NONE) {
            try {
                val vibrator = context.getSystemService(Vibrator::class.java)
                ContactRingtonePrefs.patternTimings(a.pattern)?.let { timings ->
                    if (timings.size > 1) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            vibrator?.vibrate(VibrationEffect.createWaveform(timings, -1))
                        } else {
                            @Suppress("DEPRECATION")
                            vibrator?.vibrate(timings, -1)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        if (a.ringtoneUri.isNotBlank()) {
            try {
                previewRingtone.value = RingtoneManager.getRingtone(context, a.ringtoneUri.toUri())?.apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        audioAttributes = AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    }
                    play()
                }
            } catch (_: Exception) {}
        }
    }

    val ringtonePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val number = pendingNumber
        if (number != null) {
            refreshMappings()
            val uri = result.data?.let {
                IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            }
            if (uri != null) {
                val existing = mappings[number]
                ContactRingtonePrefs.set(context, number, uri.toString(), existing?.pattern ?: ContactRingtonePrefs.PATTERN_DEFAULT)
                Toast.makeText(context, context.getString(R.string.ringtone_assigned_toast), Toast.LENGTH_SHORT).show()
            } else {
                ContactRingtonePrefs.remove(context, number)
                Toast.makeText(context, context.getString(R.string.ringtone_cleared_toast), Toast.LENGTH_SHORT).show()
            }
            refreshMappings()
        }
        pendingNumber = null
    }

    fun launchRingtonePicker(number: String) {
        pendingNumber = number
        val existing = mappings[number]?.ringtoneUri?.let { it.toUri() }
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, context.getString(R.string.ringtone_pick))
            if (existing != null) putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
        }
        ringtonePickerLauncher.launch(intent)
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
                    text = stringResource(R.string.contact_ringtones_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = stringResource(R.string.contact_ringtones_sub),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (contacts.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.contact_ringtones_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                )
            }
        } else {
            items(contacts, key = { "${it.id}_${it.number}" }) { contact ->
                val assignment = remember(mappings, contact.number) { ContactRingtonePrefs.find(context, contact.number) }
                val ringtoneTitle: String? = remember(assignment) {
                    if (assignment == null || assignment.ringtoneUri.isBlank()) null
                    else runCatching { RingtoneManager.getRingtone(context, assignment.ringtoneUri.toUri())?.getTitle(context) }
                        .getOrNull()
                        ?: context.getString(R.string.ringtone_pick)
                }
                val subtitle = buildString {
                    if (ringtoneTitle != null && assignment != null && assignment.ringtoneUri.isNotBlank()) {
                        append(ringtoneTitle)
                    } else {
                        append(context.getString(R.string.ringtone_row_sub_default))
                    }
                    val pattern = assignment?.pattern ?: ContactRingtonePrefs.PATTERN_DEFAULT
                    if (pattern != ContactRingtonePrefs.PATTERN_DEFAULT) {
                        append("  •  ").append(context.getString(R.string.vibration_pattern_label))
                            .append(": ").append(pattern)
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(AppShapes.Avatar)
                            .background(contact.avatarBg.copy(alpha = 0.85f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = contact.avatarText,
                            style = MaterialTheme.typography.titleSmall,
                            color = contact.avatarTextColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = contact.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = contact.number,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box {
                        IconButton(onClick = {
                            RichHapticEngine.performHaptic(context, RichHapticEngine.HapticStyle.KEY_TICK)
                            patternMenuFor = contact.number.trim()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Vibration,
                                contentDescription = stringResource(R.string.vibration_pattern_label),
                                tint = if (assignment?.pattern != null && assignment.pattern != ContactRingtonePrefs.PATTERN_DEFAULT)
                                    MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = patternMenuFor == contact.number.trim(),
                            onDismissRequest = { patternMenuFor = null }
                        ) {
                            ContactRingtonePrefs.patternNames().forEach { patternName ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = when (patternName) {
                                                ContactRingtonePrefs.PATTERN_DEFAULT -> stringResource(R.string.pattern_default)
                                                ContactRingtonePrefs.PATTERN_NONE -> stringResource(R.string.pattern_none)
                                                ContactRingtonePrefs.VIBRATION_SHORT -> stringResource(R.string.pattern_short)
                                                ContactRingtonePrefs.VIBRATION_LONG -> stringResource(R.string.pattern_long)
                                                ContactRingtonePrefs.VIBRATION_DOUBLE -> stringResource(R.string.pattern_double)
                                                ContactRingtonePrefs.VIBRATION_HEARTBEAT -> stringResource(R.string.pattern_heartbeat)
                                                ContactRingtonePrefs.VIBRATION_SOS -> stringResource(R.string.pattern_sos)
                                                else -> stringResource(R.string.pattern_staccato)
                                            }
                                        )
                                    },
                                    onClick = {
                                        patternMenuFor = null
                                        val currentUri = mappings[contact.number.trim()]?.ringtoneUri.orEmpty()
                                        ContactRingtonePrefs.set(context, contact.number, currentUri, patternName)
                                        refreshMappings()
                                    }
                                )
                            }
                        }
                    }
                    IconButton(onClick = {
                        RichHapticEngine.performHaptic(context, RichHapticEngine.HapticStyle.KEY_TICK)
                        launchRingtonePicker(contact.number)
                    }) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = stringResource(R.string.ringtone_pick),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = {
                        RichHapticEngine.performHaptic(context, RichHapticEngine.HapticStyle.KEY_TICK)
                        playAssignment(contact.number, mappings[contact.number.trim()])
                    }) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = stringResource(R.string.test_ringtone_label),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
