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
import com.example.ContactCache
import com.example.data.AppDatabase
import com.example.data.DatabaseFileFamily
import com.example.data.DatabaseKeyManager
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
            // Close the instance so the files can be moved while no handle is open.
            AppDatabase.closeInstance()

            // Archive the active database and every recovery artifact *before* deleting key
            // material. A later recovery attempt (or support request) can still inspect them;
            // an explicit reset must never silently destroy the only copy of user data.
            if (!archiveDatabaseFiles(context)) {
                // Could not preserve the data safely: leave the key and database untouched so
                // the user can retry instead of losing the database permanently.
                return@withContext false
            }

            // Files are archived, so clearing the key now cannot strand recoverable data.
            DatabaseKeyManager.clearKey(context)

            // Clear in-memory ContactCache
            try {
                ContactCache.init(emptyList(), emptyList())
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

    /**
     * Moves the active database, its WAL/SHM sidecars and all recovery artifacts into a
     * timestamped `recovery_archive` directory beside the database.
     *
     * Returns true only when every existing file was moved. If any move fails the already
     * moved files are put back, so a failed reset leaves the installation exactly as it was.
     */
    internal fun archiveDatabaseFiles(context: Context): Boolean {
        val dbFile = context.getDatabasePath(AppDatabase.DATABASE_NAME)
        val existing = (listOf(dbFile) + DatabaseFileFamily.from(dbFile).recoveryArtifacts)
            .filter { it.exists() }
        if (existing.isEmpty()) return true

        val parent = dbFile.parentFile ?: return false
        val archiveDir = File(parent, "recovery_archive/${System.currentTimeMillis()}")
        if (!archiveDir.isDirectory && !archiveDir.mkdirs()) return false

        val moved = ArrayList<Pair<File, File>>()
        for (source in existing) {
            val destination = File(archiveDir, source.name)
            if (!moveFile(source, destination)) {
                // Roll back so a failed archive does not leave a half-reset installation.
                for ((original, archived) in moved.asReversed()) {
                    if (!original.exists() && archived.exists()) {
                        try { archived.renameTo(original) } catch (_: Exception) {}
                    }
                }
                return false
            }
            moved.add(source to destination)
        }
        return existing.none { it.exists() }
    }

    private fun moveFile(source: File, destination: File): Boolean {
        if (!source.exists()) return true
        if (source.absolutePath == destination.absolutePath) return true
        if (source.renameTo(destination)) return true
        return try {
            source.copyTo(destination, overwrite = true)
            source.delete()
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
