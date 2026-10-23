/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.ui.components

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.MyInCallService
import com.example.R
import com.example.util.CallPermissionAcknowledgement
import com.example.util.CallPermissionStatus
import com.example.util.CallPresentationPermissions
import com.example.util.CallPresentationPermissionsReader
import com.example.util.CallScreenPermissionHelper
import com.example.util.RichHapticEngine
import com.example.util.needsReminder

private const val PREFERENCES_NAME = "dialer_prefs"
private const val IGNORE_LOCK_SCREEN_POPUP = "ignore_lock_screen_popup"

/**
 * Permanent Settings entry for the two incoming-call presentation permissions. It is intentionally
 * independent of the dismissible top banner: dismissing the banner never removes this entry.
 */
@Composable
fun FullScreenCallPermissionCard(
    modifier: Modifier = Modifier
) {
    val state = rememberCallPermissionUiState()
    var showSetup by remember { mutableStateOf(false) }

    val summary = if (state.needsReminder) {
        R.string.full_screen_features_attention
    } else {
        R.string.full_screen_features_ready
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("full_screen_call_features_card")
            .clickable { showSetup = true },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(42.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Fullscreen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.full_screen_features_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.full_screen_features_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(summary),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (state.needsReminder) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    fontWeight = FontWeight.Medium
                )
            }

            Button(
                onClick = { showSetup = true },
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = stringResource(R.string.btn_configure),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    if (showSetup) {
        CallPermissionSetupDialog(onDismiss = { showSetup = false })
    }
}

/**
 * Two independent rows, each with its own status and settings action. The dialog is also the
 * permanent entry point used after the top banner has been dismissed.
 */
@Composable
fun CallPermissionSetupDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val permissions = rememberCallPresentationPermissions()
    var acknowledged by remember {
        mutableStateOf(CallPermissionAcknowledgement.isAcknowledged(context))
    }
    var fallbackNotice by remember { mutableStateOf<Int?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.permission_setup_title),
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                PermissionSetupRow(
                    title = stringResource(R.string.lock_screen_display_title),
                    description = stringResource(R.string.lock_screen_display_desc),
                    status = permissions.lockScreenStatus,
                    actionLabel = if (permissions.lockScreenStatus == CallPermissionStatus.DISABLED) {
                        stringResource(R.string.lock_screen_display_enable)
                    } else {
                        stringResource(R.string.btn_configure)
                    },
                    onAction = {
                        RichHapticEngine.performHaptic(
                            context,
                            RichHapticEngine.HapticStyle.KEY_TICK
                        )
                        val result = CallScreenPermissionHelper.openLockScreenSettings(context)
                        fallbackNotice = when {
                            !result.opened -> R.string.permission_settings_unavailable
                            result.usedFallback -> R.string.lock_screen_display_fallback
                            else -> null
                        }
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                PermissionSetupRow(
                    title = stringResource(R.string.background_popup_title),
                    description = stringResource(R.string.background_popup_desc),
                    status = permissions.backgroundPopupStatus,
                    actionLabel = stringResource(R.string.btn_configure),
                    onAction = {
                        RichHapticEngine.performHaptic(
                            context,
                            RichHapticEngine.HapticStyle.KEY_TICK
                        )
                        val result = CallScreenPermissionHelper.openBackgroundPopupSettings(context)
                        fallbackNotice = when {
                            !result.opened -> R.string.permission_settings_unavailable
                            result.usedFallback -> R.string.background_popup_fallback
                            else -> null
                        }
                    }
                )

                if (permissions.needsBackgroundPopupAttention) {
                    BackgroundPopupAcknowledgement(
                        acknowledged = acknowledged,
                        onAcknowledgedChange = { enabled ->
                            CallPermissionAcknowledgement.setAcknowledged(context, enabled)
                            acknowledged = enabled
                        }
                    )
                }

                fallbackNotice?.let { notice ->
                    Text(
                        text = stringResource(notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Text(
                    text = stringResource(R.string.permission_manual_guide),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.permission_sheet_done))
            }
        }
    )
}

/**
 * Non-blocking top banner shown on the main interface. It disappears automatically when every
 * applicable, verifiable setting is satisfied, and stays dismissed after the user taps Ignore.
 */
@Composable
fun LockScreenAndPopupPermissionsBanner(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }
    var isIgnored by remember {
        mutableStateOf(prefs.getBoolean(IGNORE_LOCK_SCREEN_POPUP, false))
    }
    var permissions by remember {
        mutableStateOf(readPermissions(context))
    }
    var acknowledged by remember {
        mutableStateOf(CallPermissionAcknowledgement.isAcknowledged(context))
    }
    var fallbackNotice by remember { mutableStateOf<Int?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isIgnored = prefs.getBoolean(IGNORE_LOCK_SCREEN_POPUP, false)
                permissions = readPermissions(context)
                acknowledged = CallPermissionAcknowledgement.isAcknowledged(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (isIgnored || !permissions.needsReminder(acknowledged)) {
        return
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("lock_screen_popup_banner"),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.permission_setup_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = {
                        prefs.edit { putBoolean(IGNORE_LOCK_SCREEN_POPUP, true) }
                        isIgnored = true
                    },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = stringResource(R.string.permission_ignore),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            BannerPermissionRow(
                title = stringResource(R.string.lock_screen_display_title),
                description = stringResource(R.string.lock_screen_display_short),
                status = permissions.lockScreenStatus,
                actionLabel = if (permissions.lockScreenStatus == CallPermissionStatus.DISABLED) {
                    stringResource(R.string.lock_screen_display_enable)
                } else {
                    stringResource(R.string.btn_configure)
                },
                onConfigure = {
                    RichHapticEngine.performHaptic(
                        context,
                        RichHapticEngine.HapticStyle.KEY_TICK
                    )
                    val result = CallScreenPermissionHelper.openLockScreenSettings(context)
                    fallbackNotice = when {
                        !result.opened -> R.string.permission_settings_unavailable
                        result.usedFallback -> R.string.lock_screen_display_fallback
                        else -> null
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(8.dp))

            BannerPermissionRow(
                title = stringResource(R.string.background_popup_title),
                description = stringResource(R.string.background_popup_short),
                status = permissions.backgroundPopupStatus,
                actionLabel = stringResource(R.string.btn_configure),
                onConfigure = {
                    RichHapticEngine.performHaptic(
                        context,
                        RichHapticEngine.HapticStyle.KEY_TICK
                    )
                    val result = CallScreenPermissionHelper.openBackgroundPopupSettings(context)
                    fallbackNotice = when {
                        !result.opened -> R.string.permission_settings_unavailable
                        result.usedFallback -> R.string.background_popup_fallback
                        else -> null
                    }
                }
            )

            if (permissions.needsBackgroundPopupAttention) {
                Spacer(modifier = Modifier.height(8.dp))
                BackgroundPopupAcknowledgement(
                    acknowledged = acknowledged,
                    onAcknowledgedChange = { enabled ->
                        CallPermissionAcknowledgement.setAcknowledged(context, enabled)
                        acknowledged = enabled
                    }
                )
            }

            fallbackNotice?.let { notice ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
private fun PermissionSetupRow(
    title: String,
    description: String,
    status: CallPermissionStatus,
    actionLabel: String,
    onAction: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(
                    R.string.permission_status_label,
                    permissionStatusLabel(status)
                ),
                style = MaterialTheme.typography.labelMedium,
                color = permissionStatusColor(status),
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.width(8.dp))
            FilledTonalButton(
                onClick = onAction,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
private fun BannerPermissionRow(
    title: String,
    description: String,
    status: CallPermissionStatus,
    actionLabel: String,
    onConfigure: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
            )
            Text(
                text = stringResource(
                    R.string.permission_status_label,
                    permissionStatusLabel(status)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = permissionStatusColor(status)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = onConfigure,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
internal fun permissionStatusLabel(status: CallPermissionStatus): String {
    return stringResource(
        when (status) {
            CallPermissionStatus.ENABLED -> R.string.permission_status_enabled
            CallPermissionStatus.DISABLED -> R.string.permission_status_disabled
            CallPermissionStatus.UNABLE_TO_VERIFY -> R.string.permission_status_unable
        }
    )
}

@Composable
internal fun permissionStatusColor(status: CallPermissionStatus): Color {
    return when (status) {
        CallPermissionStatus.ENABLED -> MaterialTheme.colorScheme.primary
        CallPermissionStatus.DISABLED -> MaterialTheme.colorScheme.error
        CallPermissionStatus.UNABLE_TO_VERIFY -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

/**
 * Acknowledgement controls for a setting that Android cannot verify. The text is deliberately
 * separate from the OS-reported status row so the UI never claims the OEM permission was verified.
 */
@Composable
private fun BackgroundPopupAcknowledgement(
    acknowledged: Boolean,
    onAcknowledgedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (acknowledged) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.permission_background_popup_acknowledged),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = { onAcknowledgedChange(false) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = stringResource(R.string.permission_background_popup_ack_undo),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        } else {
            Text(
                text = stringResource(R.string.permission_background_popup_ack_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(
                onClick = { onAcknowledgedChange(true) },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text(
                    text = stringResource(R.string.permission_background_popup_acknowledge),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun rememberCallPresentationPermissions(): CallPresentationPermissions {
    return rememberCallPermissionUiState().permissions
}

/**
 * Permissions plus the user's manual acknowledgement of the unverifiable OEM pop-up setting.
 * Both are re-read on ON_RESUME so returning from Android Settings updates the UI immediately.
 */
private data class CallPermissionUiState(
    val permissions: CallPresentationPermissions,
    val backgroundPopupAcknowledged: Boolean
) {
    val needsReminder: Boolean
        get() = permissions.needsReminder(backgroundPopupAcknowledged)
}

@Composable
private fun rememberCallPermissionUiState(): CallPermissionUiState {
    val context = LocalContext.current
    var state by remember(context) {
        mutableStateOf(readCallPermissionUiState(context))
    }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                state = readCallPermissionUiState(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return state
}

private fun readCallPermissionUiState(context: Context): CallPermissionUiState {
    return CallPermissionUiState(
        permissions = readPermissions(context),
        backgroundPopupAcknowledged = CallPermissionAcknowledgement.isAcknowledged(context)
    )
}

private fun readPermissions(context: Context): CallPresentationPermissions {
    return CallPresentationPermissionsReader.read(context, MyInCallService.CHANNEL_INCOMING)
}
