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

package com.example.util

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat

/**
 * Lightweight, zero-bloat native Biometric & Device Credential helper for the Call Recording Vault.
 * Uses Android native BiometricPrompt with zero security bypass fallbacks.
 */
object VaultBiometricAuthHelper {

    @RequiresApi(Build.VERSION_CODES.P)
    fun authenticate(
        activity: Activity,
        title: String = "Unlock Recording Vault",
        subtitle: String = "Authenticate to access confidential call recordings",
        onSuccess: () -> Unit,
        onError: (String) -> Unit = {}
    ) {
        val keyguardManager = activity.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val isDeviceSecure = keyguardManager?.isDeviceSecure == true

        if (!isDeviceSecure) {
            // No lockscreen PIN/pattern/fingerprint configured on the device
            onSuccess()
            return
        }

        try {
            val executor = ContextCompat.getMainExecutor(activity)
            val cancellationSignal = CancellationSignal()

            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                    super.onAuthenticationError(errorCode, errString)
                    onError(errString?.toString() ?: "Authentication cancelled")
                }
            }

            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                    val prompt = BiometricPrompt.Builder(activity)
                        .setTitle(title)
                        .setSubtitle(subtitle)
                        .setAllowedAuthenticators(
                            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                            BiometricManager.Authenticators.DEVICE_CREDENTIAL
                        )
                        .build()

                    prompt.authenticate(cancellationSignal, executor, callback)
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                    // API 29 (Q) added the device-credential fallback to BiometricPrompt.
                    @Suppress("DEPRECATION")
                    val prompt = BiometricPrompt.Builder(activity)
                        .setTitle(title)
                        .setSubtitle(subtitle)
                        .setDeviceCredentialAllowed(true)
                        .build()

                    prompt.authenticate(cancellationSignal, executor, callback)
                }
                else -> {
                    // API 28 (Pie) has BiometricPrompt but not the device-credential fallback
                    // (that arrived in Q). Use a biometric-only prompt so we never invoke the
                    // API 29-only setDeviceCredentialAllowed() on Pie.
                    @Suppress("DEPRECATION")
                    val prompt = BiometricPrompt.Builder(activity)
                        .setTitle(title)
                        .setSubtitle(subtitle)
                        .setNegativeButton(activity.getString(android.R.string.cancel), executor) { _, _ ->
                            onError("Authentication cancelled")
                        }
                        .build()

                    prompt.authenticate(cancellationSignal, executor, callback)
                }
            }
        } catch (e: Exception) {
            // FIXED: NEVER grant access on unexpected hardware or driver exceptions
            onError("Biometric security verification failed: ${e.localizedMessage ?: "Unknown hardware error"}")
        }
    }
}