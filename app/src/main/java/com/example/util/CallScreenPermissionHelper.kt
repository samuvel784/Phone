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
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.telecom.TelecomManager
import androidx.activity.result.ActivityResultLauncher
import androidx.core.net.toUri

object CallScreenPermissionHelper {

    /**
     * Result of a user-initiated settings launch. [usedFallback] distinguishes an OEM that does
     * not expose the dedicated page from a successful launch of the exact page.
     */
    data class SettingsOpenResult(
        val opened: Boolean,
        val usedFallback: Boolean,
        val reason: String? = null
    )

    fun findActivity(context: Context): Activity? {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    fun isDefaultDialer(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val rm = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
                rm?.isRoleHeld(RoleManager.ROLE_DIALER) == true
            } else {
                val tm = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                tm?.defaultDialerPackage == context.packageName
            }
        } catch (_: Exception) {
            false
        }
    }

    fun canUseFullScreenIntent(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        return try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.canUseFullScreenIntent() ?: false
        } catch (e: Exception) {
            // Fail closed and expose a useful diagnostic. Assuming access here would make the
            // settings UI and call notification claim a full-screen launch that cannot happen.
            android.util.Log.w(
                "Nova Dialer Notifications",
                "canUseFullScreenIntent() failed: ${e.javaClass.simpleName}: ${e.message}"
            )
            false
        }
    }

    fun getDefaultDialerIntent(context: Context): Intent {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                return rm.createRequestRoleIntent(RoleManager.ROLE_DIALER)
            }
        }
        @Suppress("DEPRECATION")
        return Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
            putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, context.packageName)
        }
    }

    fun hasCallScreeningRole(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val rm = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
                rm?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true
            } else {
                // Before Android 10 the default dialer screens incoming calls.
                isDefaultDialer(context)
            }
        } catch (_: Exception) {
            false
        }
    }

    fun requestCallScreeningRole(context: Context, launcher: ActivityResultLauncher<Intent>? = null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val rm = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
                if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
                    val intent = rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
                    if (launcher != null) {
                        launcher.launch(intent)
                        return
                    }
                    val activity = findActivity(context)
                    if (activity != null) {
                        activity.startActivity(intent)
                        return
                    }
                    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    return
                }
            } catch (_: Exception) {}
        }
        // Fallback: the default dialer screens calls on older versions.
        requestDefaultDialer(context, launcher)
    }

    fun requestDefaultDialer(context: Context, launcher: ActivityResultLauncher<Intent>? = null) {
        val intent = getDefaultDialerIntent(context)
        try {
                if (launcher != null) {
                    launcher.launch(intent)
                    return
                }
                val activity = findActivity(context)
                if (activity != null) {
                    activity.startActivity(intent)
                    return
                } else {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    return
                }
        } catch (_: Exception) {}

        // Fallback for restricted OEMs (HyperOS, ColorOS, Nothing OS): open Default Apps system settings
        try {
            val defaultAppsIntent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).apply {
                if (findActivity(context) == null) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(defaultAppsIntent)
        } catch (_: Exception) {
            openAppDetailsSettings(context)
        }
    }

    /**
     * The package-specific Android 14+ page, or null when this Android version has no such page.
     * Kept as a factory so tests can assert the exact action and package URI without launching it.
     */
    fun fullScreenIntentSettingsIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
        return Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
            data = "package:${context.packageName}".toUri()
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Opens the full-screen access page and never silently claims it succeeded. OEMs that do not
     * implement [Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT] get a clear fallback result so
     * the settings UI can show manual navigation guidance.
     */
    fun openFullScreenIntentSettings(context: Context): SettingsOpenResult {
        val intent = fullScreenIntentSettingsIntent(context)
            ?: return SettingsOpenResult(
                opened = false,
                usedFallback = false,
                reason = "full-screen access setting is not applicable before Android 14"
            )
        val launched = launchFromContext(context, intent)
        if (launched) {
            return SettingsOpenResult(opened = true, usedFallback = false)
        }
        val fallbackOpened = openAppDetailsSettings(context)
        return SettingsOpenResult(
            opened = fallbackOpened,
            usedFallback = true,
            reason = "Failed to launch full screen intent settings page directly"
        )
    }

    /**
     * Opens the best supported page for the lock-screen half of incoming-call presentation.
     *
     * Android 14+ has a package-specific full-screen special-access page. Older versions do not,
     * so the incoming-call notification page is opened instead; it is the verifiable prerequisite
     * for a call notification to appear over the lock screen. Every failure ends at App Info and
     * is reported to the caller instead of being swallowed.
     */
    fun openLockScreenSettings(context: Context): SettingsOpenResult {
        val access = IncomingCallAccessReader.read(context, com.example.MyInCallService.CHANNEL_INCOMING)

        // A disabled notification permission or a lowered incoming channel is the actual blocker,
        // even on Android 14+. Open the page that can fix it instead of the full-screen page.
        if (!access.notificationsEnabled && launchAppNotificationSettings(context)) {
            return SettingsOpenResult(
                opened = true,
                usedFallback = true,
                reason = "Notifications are disabled; opened app notification settings"
            )
        }
        if (access.channelCheckSupported && (!access.channelExists || !access.channelSupportsHeadsUp)) {
            if (access.channelExists && launchIncomingChannelSettings(context)) {
                return SettingsOpenResult(
                    opened = true,
                    usedFallback = true,
                    reason = "The incoming-call channel needs attention; opened channel settings"
                )
            }
            if (launchAppNotificationSettings(context)) {
                return SettingsOpenResult(
                    opened = true,
                    usedFallback = true,
                    reason = "The incoming-call channel needs attention; opened app notification settings"
                )
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return openFullScreenIntentSettings(context)
        }

        if (launchIncomingChannelSettings(context)) {
            return SettingsOpenResult(
                opened = true,
                usedFallback = true,
                reason = "Android 14+ full-screen access page is not applicable; opened the incoming-call notification channel"
            )
        }
        if (launchAppNotificationSettings(context)) {
            return SettingsOpenResult(
                opened = true,
                usedFallback = true,
                reason = "Opened app notification settings instead of a dedicated lock-screen page"
            )
        }

        val fallbackOpened = openAppDetailsSettings(context)
        return SettingsOpenResult(
            opened = fallbackOpened,
            usedFallback = true,
            reason = "No dedicated lock-screen page is available; opened App Info"
        )
    }

    /**
     * The manufacturer "Display pop-up windows while running in the background" page is not part
     * of the Android SDK. Only a page that actually resolves is used; otherwise App Info is opened
     * so the user can follow the manual path without the app pretending the setting was changed.
     */
    fun openBackgroundPopupSettings(context: Context): SettingsOpenResult {
        val intent = backgroundPopupSettingsIntent(context)
        if (intent != null && launchFromContext(context, intent)) {
            return SettingsOpenResult(opened = true, usedFallback = false)
        }

        val fallbackOpened = openAppDetailsSettings(context)
        return SettingsOpenResult(
            opened = fallbackOpened,
            usedFallback = true,
            reason = if (intent == null) {
                "No manufacturer-specific background pop-up page is available; opened App Info"
            } else {
                "Failed to launch the manufacturer background pop-up page; opened App Info"
            }
        )
    }

    fun requestFullScreenIntentPermission(context: Context): SettingsOpenResult =
        openLockScreenSettings(context)

    /**
     * True when the device is likely to expose the manufacturer background-pop-up permission.
     * A resolvable OEM page is authoritative; the MIUI family is checked as a fallback because
     * some HyperOS builds restrict direct component launches.
     */
    fun isBackgroundPopupSettingLikely(
        context: Context,
        manufacturer: String = Build.MANUFACTURER
    ): Boolean {
        return backgroundPopupSettingsIntent(context, manufacturer) != null ||
                isXiaomiFamily(manufacturer)
    }

    /**
     * Returns a resolvable manufacturer page for the background pop-up permission, or null.
     * Kept as a factory so tests can assert the exact action, component and package extras.
     */
    fun backgroundPopupSettingsIntent(
        context: Context,
        manufacturer: String = Build.MANUFACTURER
    ): Intent? {
        return backgroundPopupSettingsCandidates(context, manufacturer)
            .firstOrNull { canResolve(context, it) }
    }

    /**
     * Candidate intents in priority order. Public for tests and for diagnostics; nothing is
     * launched unless [backgroundPopupSettingsIntent] finds a resolver.
     */
    fun backgroundPopupSettingsCandidates(
        context: Context,
        manufacturer: String = Build.MANUFACTURER
    ): List<Intent> {
        if (!isXiaomiFamily(manufacturer)) return emptyList()

        return listOf(
            Intent("miui.intent.action.APP_PERM_EDITOR").setClassName(
                "com.miui.securitycenter",
                "com.miui.permcenter.permissions.PermissionsEditorActivity"
            ),
            Intent("miui.intent.action.APP_PERM_EDITOR")
        ).onEach { intent ->
            // Different MIUI/HyperOS versions read different extra names.
            intent.putExtra("extra_pkgname", context.packageName)
            intent.putExtra("packageName", context.packageName)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun isXiaomiFamily(manufacturer: String): Boolean {
        val normalized = manufacturer.lowercase(java.util.Locale.ROOT)
        return normalized.contains("xiaomi") ||
                normalized.contains("redmi") ||
                normalized.contains("poco")
    }

    private fun canResolve(context: Context, intent: Intent): Boolean {
        return try {
            val component = intent.component
            if (component != null) {
                // An explicit OEM component does not need CATEGORY_DEFAULT. MATCH_DEFAULT_ONLY
                // can incorrectly hide a valid component, so check the component directly.
                context.packageManager.getActivityInfo(component, 0)
                true
            } else {
                context.packageManager.resolveActivity(intent, 0) != null
            }
        } catch (_: Exception) {
            false
        }
    }

    fun openChannelNotificationSettings(context: Context): Boolean {
        if (launchIncomingChannelSettings(context)) return true
        if (launchAppNotificationSettings(context)) return true
        return openAppDetailsSettings(context)
    }

    private fun launchIncomingChannelSettings(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        return try {
            val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                putExtra(Settings.EXTRA_CHANNEL_ID, com.example.MyInCallService.CHANNEL_INCOMING)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            launchFromContext(context, intent)
        } catch (_: Exception) {
            false
        }
    }

    private fun launchAppNotificationSettings(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        return try {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            launchFromContext(context, intent)
        } catch (_: Exception) {
            false
        }
    }

    fun openAppDetailsSettings(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            launchFromContext(context, intent)
        } catch (_: Exception) {
            false
        }
    }

    private fun launchFromContext(context: Context, intent: Intent): Boolean {
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val activity = findActivity(context)
            if (activity != null) {
                activity.startActivity(intent)
            } else {
                context.startActivity(intent)
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}
