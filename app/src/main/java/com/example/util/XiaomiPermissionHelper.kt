/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.util

import android.app.Activity
import android.app.AlertDialog
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Android/Kotlin utility and UI handler for custom phone dialer app that handles Xiaomi (MIUI/HyperOS)
 * background permissions ('Show on Lock screen' [Op Code 10020] and 'Display pop-up windows while running in the background' [Op Code 10021]).
 */
object XiaomiPermissionHelper {
    private const val OP_SHOW_ON_LOCK_SCREEN = 10020
    private const val OP_BACKGROUND_POPUP = 10021

    fun isXiaomiDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco")
    }

    fun arePermissionsEnabled(context: Context): Boolean {
        if (!isXiaomiDevice()) return true
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return true
            val uid = context.applicationInfo.uid
            val pkg = context.packageName

            val op10020Enabled = checkOp(appOps, OP_SHOW_ON_LOCK_SCREEN, uid, pkg)
            val op10021Enabled = checkOp(appOps, OP_BACKGROUND_POPUP, uid, pkg)

            op10020Enabled && op10021Enabled
        } catch (_: Exception) {
            true
        }
    }

    private fun checkOp(appOps: AppOpsManager, op: Int, uid: Int, pkg: String): Boolean {
        return try {
            val method = AppOpsManager::class.java.getMethod(
                "checkOpNoThrow",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                String::class.java
            )
            val result = method.invoke(appOps, op, uid, pkg) as? Int
            result == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            true
        }
    }

    fun showUniversalPermissionDialog(activity: Activity, onDismiss: (() -> Unit)? = null) {
        try {
            val builder = AlertDialog.Builder(activity)
                .setTitle("Xiaomi Permissions Required")
                .setMessage("Nova Dialer needs 'Show on Lock screen' and 'Display pop-up windows' permissions to show you full-screen incoming calls even when your phone is locked.")
                .setCancelable(false)
                .setPositiveButton("Open Settings") { _, _ ->
                    launchXiaomiPermissionSettings(activity)
                }
                .setNegativeButton("Cancel") { dialog, _ ->
                    dialog.dismiss()
                    onDismiss?.invoke()
                }
            builder.show()
        } catch (_: Exception) {}
    }

    fun launchXiaomiPermissionSettings(context: Context) {
        val intent = Intent("miui.intent.action.APP_PERM_EDITOR").apply {
            setClassName(
                "com.miui.permcenter",
                "com.miui.permcenter.permissions.PermissionsEditorActivity"
            )
            putExtra("extra_pkgname", context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                return
            }
        } catch (_: Exception) {}

        // Safe fallback intent
        try {
            val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallbackIntent)
        } catch (_: Exception) {}
    }
}
