/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.util

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.example.CallManager
import com.example.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object AppResetManager {

    suspend fun resetApp(context: Context): Boolean = withContext(Dispatchers.IO) {
        // Guard: Never reset during an active call
        if (CallManager.currentCall.value != null || CallManager.calls.value.isNotEmpty()) {
            return@withContext false
        }

        try {
            // Close and clear Room database tables safely
            try {
                val db = AppDatabase.getDatabase(context)
                db.clearAllTables()
                db.close()
            } catch (_: Exception) {}

            // Clear database key material so next launch generates a fresh key
            try {
                com.example.data.DatabaseKeyManager.clearKey(context)
            } catch (_: Exception) {}

            // Delete database file, WAL/SHM sidecars, and recovery artifacts
            try {
                val dbFile = context.getDatabasePath(AppDatabase.DATABASE_NAME)
                if (dbFile.exists()) dbFile.delete()
                val parent = dbFile.parentFile ?: File(".")
                val name = dbFile.name
                val artifactsToDelete = listOf(
                    "-wal", "-shm", "-journal",
                    "_encrypted_temp.db", "_swap_original.db",
                    "_plaintext_bak.db", "_unreadable_bak.db"
                )
                artifactsToDelete.forEach { suffix ->
                    val f = if (suffix.startsWith("-")) File("${dbFile.absolutePath}$suffix") else File(parent, "${name}$suffix")
                    if (f.exists()) try { f.delete() } catch (_: Exception) {}
                }
            } catch (_: Exception) {}

            // Clear in-memory ContactCache
            try {
                com.example.ContactCache.init(emptyList(), emptyList())
            } catch (_: Exception) {}

            // Clear SharedPreferences
            try {
                context.getSharedPreferences("dialer_prefs", Context.MODE_PRIVATE).edit().clear().commit()
                context.getSharedPreferences("database_prefs", Context.MODE_PRIVATE).edit().clear().commit()
            } catch (_: Exception) {}

            // Clear internal caches and temp files while PRESERVING exported contact/backup files (.vcf, backup, etc.)
            try {
                cleanDirectoryPreservingExports(context.cacheDir)
                cleanDirectoryPreservingExports(context.filesDir)
            } catch (_: Exception) {}

            // Cancel app notifications
            try {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                notificationManager?.cancelAll()
            } catch (_: Exception) {}

            // Restart app to initial fresh-install state. This must never suspend: dispatching
            // onto Dispatchers.Main from a caller that already blocks the main thread deadlocks
            // (which is exactly how the unit-test suite used to hang for hours).
            restartApp(context)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun restartApp(context: Context) {
        val restart = Runnable {
            try {
                val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    context.startActivity(intent)
                }
                if (context is Activity) {
                    context.finishAffinity()
                }
                Runtime.getRuntime().exit(0)
            } catch (_: Exception) {}
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            restart.run()
        } else {
            Handler(Looper.getMainLooper()).post(restart)
        }
    }

    private fun cleanDirectoryPreservingExports(dir: File?) {
        if (dir == null || !dir.exists() || !dir.isDirectory) return
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                cleanDirectoryPreservingExports(file)
                if (file.listFiles().isNullOrEmpty()) {
                    file.delete()
                }
            } else {
                val name = file.name.lowercase()
                val isExportOrBackup = name.endsWith(".vcf") ||
                        name.endsWith(".backup") ||
                        name.endsWith(".json") ||
                        name.endsWith(".csv") ||
                        name.endsWith(".zip") ||
                        name.endsWith(".tar") ||
                        name.contains("backup") ||
                        name.contains("export") ||
                        name.contains("contacts")

                if (!isExportOrBackup) {
                    try {
                        file.delete()
                    } catch (_: Exception) {}
                }
            }
        }
    }
}
