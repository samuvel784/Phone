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

package com.example.ui

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.AddToExistingContactSheet
import com.example.ui.components.ContactEditorScreen
import com.example.ui.viewmodel.DialerViewModel

@Composable
fun MainScreenContactDialogs(
    viewModel: DialerViewModel
) {
    var isAddContactDialogVisible by viewModel.isAddContactDialogVisible
    var isEditContactDialogVisible by viewModel.isEditContactDialogVisible
    var isAddToExistingSheetVisible by viewModel.isAddToExistingSheetVisible
    val addToExistingPendingNumber by viewModel.addToExistingPendingNumber
    val allContacts by viewModel.allContactsFlow.collectAsStateWithLifecycle()
    var oldContactToEdit by viewModel.oldContactToEdit

    if (isAddToExistingSheetVisible) {
        AddToExistingContactSheet(
            contacts = allContacts,
            pendingNumber = addToExistingPendingNumber,
            onContactSelected = { contact ->
                isAddToExistingSheetVisible = false
                viewModel.openAddToExistingContactWithNumber(addToExistingPendingNumber, contact)
            },
            onDismiss = { isAddToExistingSheetVisible = false },
            viewModel = viewModel
        )
    }

    if (isAddContactDialogVisible || isEditContactDialogVisible) {
        ContactEditorScreen(
            contactToEdit = if (isEditContactDialogVisible) oldContactToEdit else null,
            viewModel = viewModel,
            onBack = {
                isAddContactDialogVisible = false
                isEditContactDialogVisible = false
                oldContactToEdit = null
            }
        )
    }
}
