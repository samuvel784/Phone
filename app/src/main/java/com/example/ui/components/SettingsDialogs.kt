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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.R

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_about)) },
        text = {
            Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                Text("NovaDial v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.about_app_desc), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.about_mission_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

@Composable
fun PrivacyDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.privacy_policy_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
            ) {
                Text(stringResource(R.string.privacy_intro), style = MaterialTheme.typography.titleSmall)
                Text(
                    "• " + stringResource(R.string.privacy_section_1_desc) + "\n" +
                    "• " + stringResource(R.string.privacy_section_2_desc) + "\n" +
                    "• " + stringResource(R.string.privacy_section_3_desc),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

fun exportRecordingToDownloads(context: android.content.Context, filePath: String) {
    // Delegate to the recorder helper so encrypted recordings are decrypted into the private
    // cache only for the duration of the export. Keeping a separate plaintext copy path here
    // would silently export ciphertext for recordings made after the encryption rollout.
    val exported = com.example.util.CallAudioRecorder.exportRecordingToPublicDownloads(
        context,
        java.io.File(filePath)
    )
    android.widget.Toast.makeText(
        context,
        if (exported) "Exported successfully to Downloads!" else "Export failed",
        android.widget.Toast.LENGTH_LONG
    ).show()
}

