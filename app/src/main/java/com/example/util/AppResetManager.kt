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
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Explicit, user-confirmed reset.
 *
 * This is intentionally destructive: a "fresh install" reset must not leave an encrypted archive
 * that can never be opened again, nor plaintext migration leftovers. Files the user exported
 * through the document picker live outside the app's private directories and are never touched.
 * If the user wants a recovery copy, they must export one through the normal export flow before
 * resetting; the reset never creates a private, unreadable "backup".
 */
object AppResetManager {

    enum class ResetReason {
        /** The user explicitly pressed "Reset app" and confirmed it. */
        USER_CONFIRMED,

        /** The recovery screen offered reset as a last resort after a database failure. */
        RECOVERY_LAST_RESORT
    }

    data class ResetResult(
        val success: Boolean,
        val failure: String? = null
    )

    // A second tap on the reset button must not run the destructive sequence in parallel.
    private val resetInProgress = AtomicBoolean(false)

    suspend fun resetApp(context: Context): Boolean =
        performReset(context, ResetReason.USER_CONFIRMED).success

    suspend fun resetForRecovery(context: Context): ResetResult =
        performReset(context, ResetReason.RECOVERY_LAST_RESORT)

    private suspend fun performReset(context: Context, reason: ResetReason): ResetResult =
        withContext(Dispatchers.IO) {
            if (!resetInProgress.compareAndSet(false, true)) {
                return@withContext ResetResult(false, "A reset is already in progress.")
            }
            try {
                // Never reset while a call is live or being torn down.
                if (CallManager.currentCall.value != null || CallManager.calls.value.isNotEmpty()) {
                    return@withContext ResetResult(false, "A call is still active.")
                }

                try {
                    android.util.Log.i("NovaDialerReset", "Starting reset ($reason)")
                    AppDatabase.closeInstance()

                    val detached = moveDatabaseArtifactsToPending(context)
                        ?: return@withContext ResetResult(false, "Could not detach the database files.")
                    val pending = detached.pending
                    val moved = detached.moved

                    val keyClear = DatabaseKeyManager.clearKeyDetailed(context)
                    if (!keyClear.success) {
                        // If the key is still usable, restore the files and leave the install intact.
                        // If the key material was already destroyed, restoring would only produce an
                        // unreadable database, so finish the reset and report the cleanup failure.
                        if (keyClear.aliasRemoved) {
                            deletePending(pending)
                            clearPreferences(context)
                            clearPrivateDirectories(context)
                            restartApp(context)
                            return@withContext ResetResult(
                                false,
                                "The encryption key was removed but its metadata could not be cleared."
                            )
                        }
                        restorePending(context, moved)
                        return@withContext ResetResult(false, "Could not erase the database key.")
                    }

                    if (!deletePending(pending)) {
                        return@withContext ResetResult(false, "Could not delete the database files.")
                    }

                    if (!clearPreferences(context)) {
                        return@withContext ResetResult(false, "Could not clear app preferences.")
                    }

                    // Cache/files only contain app-private scratch data and app-managed exports.
                    // Genuine user exports/backups are written to document-picker locations outside
                    // these directories, so they survive by construction.
                    if (!clearPrivateDirectories(context)) {
                        return@withContext ResetResult(false, "Could not clear private app storage.")
                    }

                    try {
                        ContactCache.init(emptyList(), emptyList())
                    } catch (_: Exception) {
                    }

                    try {
                        val notificationManager =
                            context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                        notificationManager?.cancelAll()
                    } catch (_: Exception) {
                    }

                    restartApp(context)
                    ResetResult(true)
                } catch (failure: Exception) {
                    ResetResult(false, failure.message ?: "Reset failed.")
                }
            } finally {
                resetInProgress.set(false)
            }
        }

    private data class DetachedArtifacts(
        val pending: File,
        val moved: List<Pair<File, File>>
    )

    /**
     * Moves the active database, its sidecars, migration leftovers and every previous recovery
     * archive into a pending directory. Returns null (after rolling back) when any move fails.
     */
    private fun moveDatabaseArtifactsToPending(context: Context): DetachedArtifacts? {
        val dbFile = context.getDatabasePath(AppDatabase.DATABASE_NAME)
        val parent = dbFile.parentFile ?: return null
        // A previous reset that failed after detaching its files left a staging directory behind.
        // Those files are already unreachable (the key may be gone), so a retry must clear them
        // instead of keeping an archive that can never be opened.
        val pendingRoot = File(parent, "reset_pending")
        if (pendingRoot.exists() && !pendingRoot.deleteRecursively()) return null
        val pending = File(pendingRoot, System.currentTimeMillis().toString())
        if (!pending.mkdirs() && !pending.isDirectory) return null

        val family = DatabaseFileFamily.from(dbFile)
        val sources = LinkedHashSet<File>()
        sources.add(family.active)
        sources.addAll(family.sidecars)
        sources.add(family.encryptedTemp)
        sources.add(family.swapBackup)
        sources.add(family.legacyPlaintextBackup)
        sources.add(family.unreadableBackup)

        val archiveRoot = File(parent, "recovery_archive")
        if (archiveRoot.exists()) {
            archiveRoot.walkTopDown().filter { it.isFile }.forEach { sources.add(it) }
        }
        // Any sibling that belongs to this database (for example a leftover *_bak copy written by
        // an older build) must not survive a fresh-install reset either.
        parent.listFiles()?.forEach { candidate ->
            if (candidate.isFile &&
                candidate.name.startsWith(AppDatabase.DATABASE_NAME) &&
                candidate.name != "reset_pending"
            ) {
                sources.add(candidate)
            }
        }

        val moved = ArrayList<Pair<File, File>>()
        for (source in sources) {
            if (!source.exists()) continue
            val destination = uniqueDestination(pending, source.name)
            if (!moveFile(source, destination)) {
                restorePending(context, moved)
                pending.deleteRecursively()
                return null
            }
            moved.add(source to destination)
        }

        if (archiveRoot.exists()) {
            archiveRoot.deleteRecursively()
        }
        return DetachedArtifacts(pending, moved)
    }

    private fun uniqueDestination(pending: File, name: String): File {
        var candidate = File(pending, name)
        if (!candidate.exists()) return candidate
        var index = 1
        while (true) {
            candidate = File(pending, "$index-$name")
            if (!candidate.exists()) return candidate
            index++
        }
    }

    private fun deletePending(pending: File): Boolean {
        if (pending.exists()) {
            if (!pending.deleteRecursively()) {
                // A lingering handle can make the first attempt fail; try once more after a yield.
                Thread.sleep(50L)
                if (!pending.deleteRecursively()) return false
            }
        }
        // Leave no empty reset_pending shell behind: a fresh install has no reset staging area.
        // delete() only succeeds on an empty directory, so concurrent staging is never harmed.
        val parent = pending.parentFile
        if (parent != null && parent.name == "reset_pending" && parent.isDirectory) {
            parent.delete()
        }
        return true
    }

    private fun restorePending(context: Context, moved: List<Pair<File, File>>) {
        for ((original, archived) in moved.asReversed()) {
            if (!original.exists() && archived.exists()) {
                val parent = original.parentFile
                if (parent != null && !parent.exists()) parent.mkdirs()
                try {
                    if (!archived.renameTo(original)) {
                        archived.copyTo(original, overwrite = true)
                        archived.delete()
                    }
                } catch (_: Exception) {
                }
            }
        }
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

    /**
     * Erases every SharedPreferences file, not only the two this app happens to write today.
     *
     * A fresh install has no "permission prompt already shown", app-lock, ringtone or SIM-tracking
     * state. Enumerating two well-known files would silently retain the rest, so the whole
     * `shared_prefs` directory is processed for both credential-protected and device-protected
     * storage. Each file is cleared through the SharedPreferences API first so in-memory caches in
     * this process are emptied too, then the directory is removed and its absence verified.
     */
    private fun clearPreferences(context: Context): Boolean {
        return try {
            var success = true
            val contexts = listOf(context, context.createDeviceProtectedStorageContext())
            for (storageContext in contexts) {
                val sharedPrefsDir = File(storageContext.dataDir, "shared_prefs")
                val files = sharedPrefsDir
                    .listFiles()
                    ?.filter { it.isFile && it.name.endsWith(".xml") }
                    .orEmpty()
                for (file in files) {
                    val name = file.name.removeSuffix(".xml")
                    val prefs = storageContext.getSharedPreferences(name, Context.MODE_PRIVATE)
                    if (!prefs.edit().clear().commit()) success = false
                }
                if (sharedPrefsDir.exists() && !sharedPrefsDir.deleteRecursively()) {
                    success = false
                }
                if (sharedPrefsDir.exists()) success = false
            }
            success
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Removes app-private scratch storage. User-selected exports are never stored here: the
     * document picker writes them wherever the user chose. Deleting by file extension would both
     * keep plaintext leftovers and delete legitimate scratch files for the wrong reason.
     */
    private fun clearPrivateDirectories(context: Context): Boolean {
        var success = true
        for (dir in listOf(context.cacheDir, context.filesDir)) {
            if (dir == null || !dir.exists()) continue
            if (!dir.deleteRecursively()) success = false
            if (!dir.exists() && !dir.mkdirs()) success = false
        }
        return success
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
            } catch (_: Exception) {
            }
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            restart.run()
        } else {
            Handler(Looper.getMainLooper()).post(restart)
        }
    }
}
