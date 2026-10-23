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

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.AppShapes

@Composable
fun InCallControlGrid(
    isInCallDialpadOpen: Boolean,
    onToggleDialpad: () -> Unit,
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    isSpeakerOn: Boolean,
    onToggleSpeaker: () -> Unit,
    isOnHold: Boolean,
    onToggleHold: () -> Unit,
    isBluetoothOn: Boolean,
    onToggleBluetooth: () -> Unit,
    isAddCallDialogOpen: Boolean,
    onOpenAddCallDialog: () -> Unit,
    isQuickNotesOpen: Boolean = false,
    onOpenQuickNotes: () -> Unit = {},
    avatarShapeType: String = "squircle",
    /** False when Telecom offers no Bluetooth call-audio endpoint for the live call. */
    isBluetoothAvailable: Boolean = true,
    /** Hold/unhold is only actionable when the call reports hold support. */
    isHoldEnabled: Boolean = true,
    /** Add Call is only offered while a second call may actually be placed. */
    isAddCallEnabled: Boolean = true,
    /** Lock-screen call mode hides private contact picking instead of only disabling it. */
    showAddCall: Boolean = true,
    /** Lock-screen call mode hides user quick responses. */
    showQuickNotes: Boolean = true,
    /** Swap is shown only when an active and a held call can really be switched. */
    isSwapVisible: Boolean = false,
    onSwap: () -> Unit = {},
    /** Merge is shown only when Telecom reports the calls as conferenceable. */
    isMergeVisible: Boolean = false,
    onMerge: () -> Unit = {},
    /** Controls requiring an established conversation (Mute, Speaker, Hold, Bluetooth, Add Call) are disabled until the call is active. */
    isCallActive: Boolean = true
) {
    val btnShape = AppShapes.Keypad
    val buttonHeight = 68.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Row 1: Keypad, Mute, Speaker
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            InCallButton(
                icon = Icons.Default.Dialpad,
                label = stringResource(R.string.keypad),
                isActive = isInCallDialpadOpen,
                onClick = onToggleDialpad,
                shape = btnShape,
                modifier = Modifier.weight(1f).height(buttonHeight),
                enabled = true // Keypad remains enabled and accessible while dialing
            )
            InCallButton(
                icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                label = stringResource(R.string.mute),
                isActive = isMuted,
                onClick = onToggleMute,
                shape = btnShape,
                modifier = Modifier.weight(1f).height(buttonHeight),
                enabled = isCallActive
            )
            InCallButton(
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                label = stringResource(R.string.speaker),
                isActive = isSpeakerOn,
                onClick = onToggleSpeaker,
                shape = btnShape,
                modifier = Modifier.weight(1f).height(buttonHeight),
                enabled = isCallActive
            )
        }

        // Row 2: Hold/Unhold, Bluetooth, Add Call
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            InCallButton(
                icon = if (isOnHold) Icons.Default.PlayArrow else Icons.Default.Pause,
                label = if (isOnHold) stringResource(R.string.btn_unhold) else stringResource(R.string.hold),
                isActive = isOnHold,
                onClick = onToggleHold,
                shape = btnShape,
                modifier = Modifier.weight(1f).height(buttonHeight),
                enabled = isHoldEnabled && isCallActive
            )
            InCallButton(
                icon = Icons.Default.Bluetooth,
                label = stringResource(R.string.bluetooth),
                isActive = isBluetoothOn,
                onClick = onToggleBluetooth,
                shape = btnShape,
                modifier = Modifier.weight(1f).height(buttonHeight),
                enabled = isBluetoothAvailable && isCallActive
            )
            if (showAddCall) {
                InCallButton(
                    icon = Icons.Default.GroupAdd,
                    label = stringResource(R.string.add_call),
                    isActive = isAddCallDialogOpen,
                    onClick = onOpenAddCallDialog,
                    shape = btnShape,
                    modifier = Modifier.weight(1f).height(buttonHeight),
                    enabled = isAddCallEnabled && isCallActive
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
        }

        // Row 3: multi-call actions. Only rendered when Telecom reports the operation as possible,
        // so Swap/Merge never appear because a contact was merely selected.
        if ((isSwapVisible || isMergeVisible) && isCallActive) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSwapVisible) {
                    InCallButton(
                        icon = Icons.Default.SwapHoriz,
                        label = stringResource(R.string.btn_swap),
                        isActive = false,
                        onClick = onSwap,
                        shape = btnShape,
                        modifier = Modifier.weight(1f).height(buttonHeight)
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                if (isMergeVisible) {
                    InCallButton(
                        icon = Icons.AutoMirrored.Filled.CallMerge,
                        label = stringResource(R.string.btn_merge),
                        isActive = false,
                        onClick = onMerge,
                        shape = btnShape,
                        modifier = Modifier.weight(1f).height(buttonHeight)
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.weight(1f))
            }
        }

        // Row 4: Quick Notes. This row is omitted on the lock-screen call-only surface,
        // where the private destination is unavailable.
        if (showQuickNotes) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                InCallButton(
                    icon = Icons.Default.Quickreply,
                    label = stringResource(R.string.quick_responses),
                    isActive = isQuickNotesOpen,
                    onClick = onOpenQuickNotes,
                    shape = btnShape,
                    modifier = Modifier.weight(1f).height(buttonHeight),
                    enabled = isCallActive
                )
                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}
