/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.ui.components

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Sos
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.model.getAvatarShape
import com.example.model.getInitials
import com.example.telecom.EmergencySosManager
import com.example.telecom.EmergencySosPrefs
import com.example.telecom.EmergencySosService
import com.example.ui.viewmodel.DialerViewModel
import com.example.util.LocationHelper
import com.example.util.RichHapticEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/**
 * Emergency SOS screen.
 *
 * Shows the user exactly what happens when SOS is triggered, lets them configure the emergency
 * contacts and the number to call, and exposes two actions:
 *  - A. the big hold-to-activate SOS button (calls the emergency number and texts contacts), and
 *  - B. a "send test alert" action that only texts contacts, so the user can verify it works.
 */
@Composable
fun EmergencySosSettings(
    viewModel: DialerViewModel,
    cardBgColor: Color
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var contacts by remember { mutableStateOf(EmergencySosPrefs.getContacts(context)) }
    var autoAlert by remember { mutableStateOf(EmergencySosPrefs.isAutoAlertEnabled(context)) }
    var includeLocation by remember { mutableStateOf(EmergencySosPrefs.isIncludeLocation(context)) }
    var callNumber by remember { mutableStateOf(EmergencySosPrefs.getEmergencyCallNumber(context)) }
    var customMessage by remember { mutableStateOf(EmergencySosPrefs.getCustomMessage(context)) }
    var countdownSeconds by remember { mutableIntStateOf(EmergencySosPrefs.getCountdownSeconds(context)) }
    var shakeEnabled by remember { mutableStateOf(EmergencySosPrefs.isShakeToTriggerEnabled(context)) }

    // Non-zero while a cancellable countdown is running before the alert is sent.
    var countdownRemaining by remember { mutableIntStateOf(0) }
    var countdownJob by remember { mutableStateOf<Job?>(null) }

    var showAddDialog by remember { mutableStateOf(false) }
    var contactSearchInput by remember { mutableStateOf("") }
    val allContacts by viewModel.allContactsFlow.collectAsStateWithLifecycle()

    var hasLocation by remember { mutableStateOf(LocationHelper.hasLocationPermission(context)) }
    var activating by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasLocation = LocationHelper.hasLocationPermission(context)
    }

    fun executeSos() {
        scope.launch {
            val location = if (includeLocation && hasLocation) {
                LocationHelper.getLastKnownLocation(context)
            } else {
                null
            }

            if (autoAlert) {
                when {
                    contacts.isEmpty() -> snackbarHostState.showSnackbar(
                        context.getString(R.string.sos_no_contacts_toast)
                    )
                    else -> {
                        val message = EmergencySosManager.buildAlertMessage(context, includeLocation, location)
                        val sent = EmergencySosManager.alertContacts(context, contacts, message)
                        snackbarHostState.showSnackbar(context.getString(R.string.sos_sent_toast, sent))
                    }
                }
            }

            if (callNumber.isNotBlank()) {
                EmergencySosManager.callEmergencyNumber(context, callNumber)
            }
            activating = false
        }
    }

    fun cancelCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        countdownRemaining = 0
        activating = false
    }

    /** Starts the SOS flow, honouring the user's countdown so an accidental trigger can be cancelled. */
    fun triggerSos() {
        if (activating) return
        activating = true
        RichHapticEngine.performHaptic(context, RichHapticEngine.HapticStyle.KEY_TICK)
        val total = EmergencySosPrefs.getCountdownSeconds(context)
        if (total <= 0) {
            executeSos()
            return
        }
        countdownRemaining = total
        countdownJob?.cancel()
        countdownJob = scope.launch {
            var remaining = total
            while (remaining > 0) {
                delay(1.seconds)
                remaining--
                countdownRemaining = remaining
            }
            countdownRemaining = 0
            executeSos()
        }
    }

    fun sendTestAlert() {
        if (contacts.isEmpty()) {
            scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.sos_no_contacts_toast)) }
            return
        }
        scope.launch {
            val location = if (includeLocation && hasLocation) {
                LocationHelper.getLastKnownLocation(context)
            } else {
                null
            }
            val message = EmergencySosManager.buildAlertMessage(context, includeLocation, location)
            val sent = EmergencySosManager.alertContacts(context, contacts, message)
            snackbarHostState.showSnackbar(context.getString(R.string.sos_sent_toast, sent))
        }
    }

    val scrollState = androidx.compose.runtime.saveable.rememberSaveable(saver = androidx.compose.foundation.ScrollState.Saver) {
        androidx.compose.foundation.ScrollState(0)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ---------------------------------------------------- INSTRUCTIONS
            HighlightableCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                cardBgColor = cardBgColor,
                shape = MaterialTheme.shapes.medium
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.sos_instructions_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.sos_instructions_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ---------------------------------------------------- PERMISSIONS
            HighlightableCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                cardBgColor = cardBgColor,
                shape = MaterialTheme.shapes.medium
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (hasLocation) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (hasLocation) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringResource(
                                if (hasLocation) R.string.sos_permissions_ok else R.string.sos_permissions_needed
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (!hasLocation) {
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = {
                                val perms = mutableListOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                                permissionLauncher.launch(perms.toTypedArray())
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.sos_grant_permissions))
                        }
                    }
                }
            }

            // ---------------------------------------------------- SOS BUTTON
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .size(148.dp)
                        .pointerInput(activating) {
                            detectTapGestures(
                                onLongPress = { triggerSos() },
                                onTap = {
                                    RichHapticEngine.performHaptic(context, RichHapticEngine.HapticStyle.KEY_TICK)
                                    scope.launch {
                                        snackbarHostState.showSnackbar(context.getString(R.string.sos_hold_hint))
                                    }
                                }
                            )
                        },
                    shape = CircleShape,
                    color = if (activating) {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    contentColor = MaterialTheme.colorScheme.onError,
                    shadowElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sos,
                            contentDescription = null,
                            modifier = Modifier.size(42.dp)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.sos_button_label),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Text(
                text = stringResource(R.string.sos_hold_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            )

            // ---------------------------------------------------- BEHAVIOUR
            HighlightableCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                cardBgColor = cardBgColor,
                shape = MaterialTheme.shapes.medium
            ) {
                Column {
                    SettingsRowToggle(
                        title = stringResource(R.string.sos_auto_alert_title),
                        subtitle = stringResource(R.string.sos_auto_alert_sub),
                        checked = autoAlert,
                        onCheckedChange = {
                            autoAlert = it
                            EmergencySosPrefs.setAutoAlertEnabled(context, it)
                        }
                    )

                    AnimatedVisibility(
                        visible = autoAlert,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column {
                            SettingsRowToggle(
                                title = stringResource(R.string.sos_include_location_title),
                                subtitle = stringResource(R.string.sos_include_location_sub),
                                checked = includeLocation,
                                onCheckedChange = {
                                    includeLocation = it
                                    EmergencySosPrefs.setIncludeLocation(context, it)
                                }
                            )
                            if (includeLocation && !hasLocation) {
                                Text(
                                    text = stringResource(R.string.sos_no_location),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ---------------------------------------------------- NUMBER TO CALL
            HighlightableCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                cardBgColor = cardBgColor,
                shape = MaterialTheme.shapes.medium
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.sos_call_number_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.sos_call_number_sub),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = callNumber,
                        onValueChange = {
                            val capped = it.take(20)
                            callNumber = capped
                            EmergencySosPrefs.setEmergencyCallNumber(context, capped)
                        },
                        label = { Text(stringResource(R.string.sos_call_number_title)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // ---------------------------------------------------- CUSTOM MESSAGE
            HighlightableCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                cardBgColor = cardBgColor,
                shape = MaterialTheme.shapes.medium
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.sos_custom_message_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.sos_custom_message_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customMessage,
                        onValueChange = {
                            val capped = it.take(300)
                            customMessage = capped
                            EmergencySosPrefs.setCustomMessage(context, capped)
                        },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.sos_alert_message),
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        singleLine = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                    )
                }
            }

            // ---------------------------------------------------- TRIGGERS
            HighlightableCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                cardBgColor = cardBgColor,
                shape = MaterialTheme.shapes.medium
            ) {
                Column {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.sos_countdown_setting),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.sos_countdown_setting_sub),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0, 3, 5, 10).forEach { secs ->
                                FilterChip(
                                    selected = countdownSeconds == secs,
                                    onClick = {
                                        countdownSeconds = secs
                                        EmergencySosPrefs.setCountdownSeconds(context, secs)
                                    },
                                    label = { Text(if (secs == 0) "0" else "${secs}s") }
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    SettingsRowToggle(
                        title = stringResource(R.string.sos_shake_title),
                        subtitle = stringResource(R.string.sos_shake_sub),
                        checked = shakeEnabled,
                        onCheckedChange = {
                            shakeEnabled = it
                            EmergencySosPrefs.setShakeToTriggerEnabled(context, it)
                            if (it) EmergencySosService.start(context) else EmergencySosService.stop(context)
                        }
                    )
                }
            }

            // Android does not expose the power button to third-party apps — say so plainly.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.sos_power_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ---------------------------------------------------- CONTACTS
            PreferenceHeader(title = stringResource(R.string.sos_contacts_title))
            HighlightableCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                cardBgColor = cardBgColor,
                shape = MaterialTheme.shapes.medium
            ) {
                Column {
                    if (contacts.isEmpty()) {
                        Text(
                            text = stringResource(R.string.sos_contacts_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    } else {
                        contacts.forEach { contact ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = contact.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = contact.number,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = {
                                    EmergencySosPrefs.removeContact(context, contact)
                                    contacts = EmergencySosPrefs.getContacts(context)
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    ListItem(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) { detectTapGestures(onTap = { showAddDialog = true }) },
                        headlineContent = {
                            Text(
                                text = stringResource(R.string.sos_contact_add),
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }

            // ---------------------------------------------------- TEST
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                OutlinedButton(
                    onClick = { sendTestAlert() },
                    enabled = contacts.isNotEmpty()
                ) {
                    Text(stringResource(R.string.sos_test_alert))
                }
            }

            Spacer(Modifier.height(24.dp))
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }

    if (countdownRemaining > 0) {
        AlertDialog(
            onDismissRequest = { cancelCountdown() },
            icon = {
                Icon(
                    imageVector = Icons.Default.Sos,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.sos_countdown_title),
                    fontWeight = FontWeight.Bold
                )
            },
            text = { Text(stringResource(R.string.sos_countdown_body, countdownRemaining)) },
            confirmButton = {
                TextButton(onClick = { cancelCountdown() }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        countdownJob?.cancel()
                        countdownJob = null
                        countdownRemaining = 0
                        executeSos()
                    }
                ) {
                    Text(stringResource(R.string.sos_countdown_now))
                }
            }
        )
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(stringResource(R.string.sos_contact_add)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = contactSearchInput,
                        onValueChange = { contactSearchInput = it },
                        label = { Text(stringResource(R.string.add_call_search_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    val filteredContacts = remember(contactSearchInput, allContacts) {
                        val query = contactSearchInput.trim()
                        if (query.isBlank()) {
                            allContacts
                        } else {
                            allContacts.filter { contact ->
                                contact.name.contains(query, ignoreCase = true) ||
                                contact.number.contains(query) ||
                                contact.getAllNumbers().any { it.number.contains(query) }
                            }
                        }
                    }

                    if (filteredContacts.isEmpty()) {
                        Text(
                            text = stringResource(R.string.sos_contacts_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(
                                items = filteredContacts,
                                key = { "${it.id}_${it.number}" }
                            ) { contact ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        RichHapticEngine.performHaptic(context, RichHapticEngine.HapticStyle.KEY_TICK)
                                        if (contact.number.isNotBlank()) {
                                            EmergencySosPrefs.addContact(context, contact.name, contact.number)
                                            contacts = EmergencySosPrefs.getContacts(context)
                                            contactSearchInput = ""
                                            showAddDialog = false
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            modifier = Modifier.size(36.dp),
                                            shape = getAvatarShape("circular"),
                                            color = contact.avatarBg
                                        ) {
                                            var imageFailed by remember(contact.photoUri) { mutableStateOf(false) }
                                            Box(contentAlignment = Alignment.Center) {
                                                if (contact.photoUri.isNotEmpty() && !imageFailed) {
                                                    AsyncImage(
                                                        model = ImageRequest.Builder(context)
                                                            .data(contact.photoUri)
                                                            .size(128, 128)
                                                            .crossfade(true)
                                                            .build(),
                                                        contentDescription = contact.name,
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentScale = ContentScale.Crop,
                                                        onError = { imageFailed = true }
                                                    )
                                                } else {
                                                    Text(
                                                        text = contact.avatarText.ifEmpty { getInitials(contact.name) },
                                                        color = contact.avatarTextColor,
                                                        style = MaterialTheme.typography.labelSmall
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = contact.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = contact.number,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }
}
