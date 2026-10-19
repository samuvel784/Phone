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

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.viewmodel.DialerViewModel
import com.example.util.RichHapticEngine
import com.example.util.VaultBiometricAuthHelper
import androidx.core.net.toUri

@Composable
fun PrivacySecurityAboutSettings(
    viewModel: DialerViewModel,
    cardBgColor: Color,
    onShowAbout: () -> Unit,
    onShowPrivacy: () -> Unit,
    highlightedTitle: String? = null
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val isBiometricLockEnabled by viewModel.isBiometricLockEnabled
    val isPocketProtectionEnabled by viewModel.isPocketProtectionEnabled

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // [Header] PRIVACY PROTECTION
        item {
            PreferenceHeader(stringResource(R.string.header_privacy_protection))
        }

        // Biometric Lock Card (FIXED: Authenticates before enabling to prevent permanent lockouts)
        item {
            HighlightableCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                cardBgColor = cardBgColor,
                isHighlighted = isMatchTitle("Biometric App Lock", highlightedTitle) ||
                        isMatchTitle("Privacy & Security Settings", highlightedTitle) ||
                        isMatchTitle("App Biometric Lock", highlightedTitle) ||
                        isMatchTitle("Biometric Lock", highlightedTitle),
                shape = MaterialTheme.shapes.medium
            ) {
                SettingsRowToggle(
                    title = stringResource(R.string.settings_biometric_lock),
                    subtitle = stringResource(R.string.settings_biometric_lock_sub),
                    checked = isBiometricLockEnabled,
                    onCheckedChange = { enabled ->
                        RichHapticEngine.performHaptic(context, RichHapticEngine.HapticStyle.KEY_TICK)
                        if (enabled && activity != null) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                VaultBiometricAuthHelper.authenticate(
                                    activity = activity,
                                    title = context.getString(R.string.settings_biometric_lock),
                                    subtitle = context.getString(R.string.settings_biometric_lock_sub),
                                    onSuccess = {
                                        viewModel.updateBiometricLockEnabled(true)
                                    }
                                )
                            } else {
                                viewModel.updateBiometricLockEnabled(true)
                            }
                        } else {
                            viewModel.updateBiometricLockEnabled(enabled)
                        }
                    },
                    icon = Icons.Default.Lock,
                    iconBgColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    iconTint = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Pocket Protection Card
        item {
            HighlightableCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                cardBgColor = cardBgColor,
                isHighlighted = isMatchTitle("Pocket Protection Mode", highlightedTitle) ||
                        isMatchTitle("Pocket Protection Guard", highlightedTitle) ||
                        isMatchTitle("Pocket Protection", highlightedTitle),
                shape = MaterialTheme.shapes.medium
            ) {
                SettingsRowToggle(
                    title = stringResource(R.string.settings_pocket_protection),
                    subtitle = stringResource(R.string.settings_pocket_protection_sub),
                    checked = isPocketProtectionEnabled,
                    onCheckedChange = {
                        RichHapticEngine.performHaptic(context, RichHapticEngine.HapticStyle.KEY_TICK)
                        viewModel.updatePocketProtectionEnabled(it)
                    },
                    icon = Icons.Default.ScreenLockPortrait,
                    iconBgColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    iconTint = MaterialTheme.colorScheme.secondary
                )
            }
        }

        // [Header] INCOMING CALL DIAGNOSTICS
        item {
            Spacer(modifier = Modifier.height(12.dp))
            PreferenceHeader("Incoming Call Diagnostics")
        }

        item {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
            val areNotificationsEnabled = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
            val canUseFsi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                notificationManager?.canUseFullScreenIntent() ?: false
            } else {
                true
            }
            val channel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                notificationManager?.getNotificationChannel("incoming_call_channel")
            } else null
            val channelImportance = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) channel?.importance ?: -1 else -1
            val isDefaultDialer = com.example.util.CallScreenPermissionHelper.isDefaultDialer(context)

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Full-Screen Intent: ${if (canUseFsi) "Available (Allowed)" else "Unavailable / Restricted"}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text("Notifications Enabled: ${if (areNotificationsEnabled) "Yes" else "No"}", style = MaterialTheme.typography.bodySmall)
                    Text("Incoming Channel Importance: $channelImportance ${if (channelImportance >= 4) "(High/Urgent)" else "(Standard/Low)"}", style = MaterialTheme.typography.bodySmall)
                    Text("Default Phone App: ${if (isDefaultDialer) "Yes (Default Dialer)" else "Not Default"}", style = MaterialTheme.typography.bodySmall)

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && !canUseFsi) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                                        data = android.net.Uri.fromParts("package", context.packageName, null)
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    try {
                                        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = android.net.Uri.fromParts("package", context.packageName, null)
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        context.startActivity(intent)
                                    } catch (_: Exception) {}
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Grant Full-Screen Intent Access")
                        }
                    }
                }
            }
        }

        // [Header] APP SPECIFICATIONS
        item {
            Spacer(modifier = Modifier.height(12.dp))
            PreferenceHeader(stringResource(R.string.header_app_specs))
        }

        // About NovaDial Card
        item {
            HighlightableCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                cardBgColor = cardBgColor,
                isHighlighted = isMatchTitle("App Version & Device Specifications", highlightedTitle) ||
                        isMatchTitle("App Information & Changelog", highlightedTitle) ||
                        isMatchTitle("About", highlightedTitle),
                shape = MaterialTheme.shapes.medium
            ) {
                SettingsRowNav(
                    title = stringResource(R.string.settings_about),
                    subtitle = stringResource(R.string.settings_about_sub),
                    onClick = onShowAbout,
                    icon = Icons.Default.Info,
                    iconBgColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    iconTint = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Privacy Policy Card
        item {
            HighlightableCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                cardBgColor = cardBgColor,
                isHighlighted = isMatchTitle("Zero-Data Privacy Policy", highlightedTitle) ||
                        isMatchTitle("Privacy", highlightedTitle),
                shape = MaterialTheme.shapes.medium
            ) {
                SettingsRowNav(
                    title = stringResource(R.string.settings_privacy),
                    subtitle = stringResource(R.string.settings_privacy_sub),
                    onClick = onShowPrivacy,
                    icon = Icons.Default.PrivacyTip,
                    iconBgColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    iconTint = MaterialTheme.colorScheme.secondary
                )
            }
        }

        // [Header] COMMUNITY & SUPPORT
        item {
            Spacer(modifier = Modifier.height(12.dp))
            PreferenceHeader(stringResource(R.string.header_community))
        }

        // Support Open Source Card
        item {
            HighlightableCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                cardBgColor = cardBgColor,
                isHighlighted = isMatchTitle("Help & Customer Support", highlightedTitle) ||
                        isMatchTitle("Support Open Source Development", highlightedTitle) ||
                        isMatchTitle("GitHub", highlightedTitle),
                shape = MaterialTheme.shapes.medium
            ) {
                SettingsRowNav(
                    title = stringResource(R.string.settings_contribute_github),
                    subtitle = stringResource(R.string.settings_support_desc),
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, "https://github.com".toUri()).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        try { context.startActivity(intent) } catch (_: Exception) {}
                    },
                    icon = Icons.Default.Code,
                    iconBgColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                    iconTint = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}
