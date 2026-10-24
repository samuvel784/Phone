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

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.model.Contact
import com.example.model.LabeledAddress
import com.example.model.LabeledEmail
import com.example.model.LabeledNumber
import com.example.ui.theme.AppShapes
import com.example.ui.viewmodel.DialerViewModel
import com.example.util.RichHapticEngine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactEditorScreen(
    contactToEdit: Contact? = null,
    viewModel: DialerViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val isEditing = contactToEdit != null

    var name by remember { mutableStateOf(contactToEdit?.name ?: "") }
    var photoUri by remember { mutableStateOf(contactToEdit?.photoUri ?: "") }
    var note by remember { mutableStateOf(contactToEdit?.note ?: "") }

    val initialNumbers = remember(contactToEdit) {
        val existing = contactToEdit?.getAllNumbers() ?: emptyList()
        if (existing.isNotEmpty()) {
            existing.toMutableStateList()
        } else {
            val prefilledNum = viewModel.newContactNumber.value
            mutableListOf(LabeledNumber(number = prefilledNum, label = "Mobile", isPrimary = true)).toMutableStateList()
        }
    }
    val phoneNumbers = remember { initialNumbers }

    val initialEmails = remember(contactToEdit) {
        (contactToEdit?.getAllEmails() ?: emptyList()).toMutableStateList()
    }
    val emails = remember { initialEmails }

    val initialAddresses = remember(contactToEdit) {
        (contactToEdit?.getAllAddresses() ?: emptyList()).toMutableStateList()
    }
    val addresses = remember { initialAddresses }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            photoUri = uri.toString()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isEditing) stringResource(R.string.btn_edit) else stringResource(R.string.action_add_contact),
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back)
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            if (name.isBlank() && phoneNumbers.all { it.number.isBlank() }) {
                                Toast.makeText(context, "Please enter a name or phone number", Toast.LENGTH_SHORT).show()
                                return@TextButton
                            }
                            RichHapticEngine.performHaptic(context, RichHapticEngine.HapticStyle.SUCCESS)
                            if (contactToEdit != null) {
                                viewModel.updateContactWithDetails(
                                    contact = contactToEdit,
                                    name = name,
                                    numbers = phoneNumbers.filter { it.number.isNotBlank() },
                                    emails = emails.filter { it.email.isNotBlank() },
                                    addresses = addresses.filter { it.address.isNotBlank() }
                                )
                            } else {
                                viewModel.addContactWithDetails(
                                    name = name,
                                    numbers = phoneNumbers.filter { it.number.isNotBlank() },
                                    emails = emails.filter { it.email.isNotBlank() },
                                    addresses = addresses.filter { it.address.isNotBlank() }
                                )
                            }
                            onBack()
                        }
                    ) {
                        Text(
                            text = stringResource(R.string.btn_save),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Profile Photo Picker
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(AppShapes.Avatar)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable {
                        photoPickerLauncher.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                if (photoUri.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(photoUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.AddAPhoto,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            if (photoUri.isNotBlank()) {
                TextButton(onClick = { photoUri = "" }) {
                    Text(stringResource(R.string.btn_delete))
                }
            }

            // Basic Info: Name
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.label_first_name)) },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = AppShapes.Card
            )

            // Phone Numbers Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = AppShapes.Card,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.label_phone_number),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    phoneNumbers.forEachIndexed { index, num ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = num.number,
                                onValueChange = { newText ->
                                    phoneNumbers[index] = num.copy(number = newText)
                                },
                                label = { Text("Phone ${index + 1} (${num.label})") },
                                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = AppShapes.Card
                            )
                            if (phoneNumbers.size > 1) {
                                IconButton(onClick = { phoneNumbers.removeAt(index) }) {
                                    Icon(
                                        Icons.Default.RemoveCircle,
                                        contentDescription = stringResource(R.string.remove_field),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = { phoneNumbers.add(LabeledNumber("", "Mobile", false)) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.action_add_contact))
                    }
                }
            }

            // Email Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = AppShapes.Card,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.label_email),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    emails.forEachIndexed { index, email ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = email.email,
                                onValueChange = { newText ->
                                    emails[index] = email.copy(email = newText)
                                },
                                label = { Text("Email ${index + 1}") },
                                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = AppShapes.Card
                            )
                            IconButton(onClick = { emails.removeAt(index) }) {
                                Icon(Icons.Default.RemoveCircle, contentDescription = stringResource(R.string.remove_field), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = { emails.add(LabeledEmail("", "Home")) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.label_email))
                    }
                }
            }

            // Addresses Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = AppShapes.Card,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.label_address),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    addresses.forEachIndexed { index, addr ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = addr.address,
                                onValueChange = { newText ->
                                    addresses[index] = addr.copy(address = newText)
                                },
                                label = { Text("Address ${index + 1}") },
                                leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = AppShapes.Card
                            )
                            IconButton(onClick = { addresses.removeAt(index) }) {
                                Icon(Icons.Default.RemoveCircle, contentDescription = stringResource(R.string.remove_field), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = { addresses.add(LabeledAddress("", "Home")) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.label_address))
                    }
                }
            }

            // Notes Section
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(stringResource(R.string.settings_all_call_notes)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Note, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                shape = AppShapes.Card,
                minLines = 3
            )
        }
    }
}
