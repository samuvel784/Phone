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
 * Transactional, idempotent, user-confirmed reset.
 *
 * This is intentionally destructive: a "fresh install" reset must not leave an encrypted archive
 * that can never be opened again, nor plaintext migration leftovers.
 *
 * All state transitions and staged file moves are durably recorded in [ResetTransaction].
 * User-selected external backup documents written via SAF/document picker live outside app-owned
 * directories and survive by construction.
 */
object AppResetManager {

    enum class ResetReason {
        /** The user explicitly pressed "Reset app" and confirmed it. */
        USER_CONFIRMED,

        /** The recovery screen offered reset as a last resort after a database failure. */
        RECOVERY_LAST_RESORT
    }

    enum class ResetStage {
        INITIATED,
        STAGING_IN_PROGRESS,
        STAGED_TO_PENDING,
        KEY_ERASURE_IN_PROGRESS,
        KEY_ERASED,
        CLEANUP_IN_PROGRESS,
        CLEANUP_VERIFIED,
        COMPLETED
    }

    data class ManifestEntry(
        val originalPath: String,
        val stagedPath: String
    )

    data class ResetTransaction(
        val stage: ResetStage,
        val timestampMs: Long,
        val manifest: List<ManifestEntry>
    )

    data class RestorePendingResult(
        val isSuccess: Boolean,
        val restoredFiles: List<File>,
        val failedFiles: List<File>,
        val errorReason: String? = null
    )

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

                    // Stage 1: INITIATED
                    var transaction = ResetTransaction(ResetStage.INITIATED, System.currentTimeMillis(), emptyList())
                    if (!writeTransaction(context, transaction)) {
                        return@withContext ResetResult(false, "Could not record reset transaction start.")
                    }

                    // Stage 2: STAGING_IN_PROGRESS
                    transaction = transaction.copy(stage = ResetStage.STAGING_IN_PROGRESS)
                    if (!writeTransaction(context, transaction)) {
                        return@withContext ResetResult(false, "Could not update transaction state to STAGING_IN_PROGRESS.")
                    }

                    val detached = moveDatabaseArtifactsToPending(context)
                        ?: return@withContext ResetResult(false, "Could not detach the database files.")
                    val pending = detached.pending
                    val moved = detached.moved
                    val manifest = moved.map { ManifestEntry(it.first.absolutePath, it.second.absolutePath) }

                    // Stage 3: STAGED_TO_PENDING
                    transaction = ResetTransaction(ResetStage.STAGED_TO_PENDING, System.currentTimeMillis(), manifest)
                    if (!writeTransaction(context, transaction)) {
                        restorePending(context, moved)
                        return@withContext ResetResult(false, "Could not record STAGED_TO_PENDING transaction.")
                    }

                    // Stage 4: KEY_ERASURE_IN_PROGRESS
                    transaction = transaction.copy(stage = ResetStage.KEY_ERASURE_IN_PROGRESS)
                    if (!writeTransaction(context, transaction)) {
                        restorePending(context, moved)
                        return@withContext ResetResult(false, "Could not record KEY_ERASURE_IN_PROGRESS transaction.")
                    }

                    val keyClear = DatabaseKeyManager.clearKeyDetailed(context)
                    if (!keyClear.success) {
                        if (keyClear.aliasRemoved && !DatabaseKeyManager.hasPersistedKey(context)) {
                            // Key cleared but detailed check reported false; treat as cleared safely
                        } else {
                            restorePending(context, moved)
                            return@withContext ResetResult(false, "Could not erase the database key.")
                        }
                    }

                    // Stage 5: KEY_ERASED
                    transaction = ResetTransaction(ResetStage.KEY_ERASED, System.currentTimeMillis(), manifest)
                    if (!writeTransaction(context, transaction)) {
                        // Key deleted but transaction write failed: fail closed, preserve files and transaction evidence
                        return@withContext ResetResult(false, "Key erased but failed to record KEY_ERASED transaction.")
                    }

                    // Stage 6: CLEANUP_IN_PROGRESS
                    transaction = transaction.copy(stage = ResetStage.CLEANUP_IN_PROGRESS)
                    writeTransaction(context, transaction)

                    if (!deletePending(pending)) {
                        return@withContext ResetResult(false, "Could not delete the database files.")
                    }

                    if (!clearPreferences(context)) {
                        return@withContext ResetResult(false, "Could not clear app preferences.")
                    }

                    if (!clearPrivateDirectories(context)) {
                        return@withContext ResetResult(false, "Could not clear private app storage.")
                    }

                    // Stage 7: CLEANUP_VERIFIED
                    transaction = transaction.copy(stage = ResetStage.CLEANUP_VERIFIED)
                    writeTransaction(context, transaction)

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

                    // Stage 8: COMPLETED
                    writeTransaction(context, transaction.copy(stage = ResetStage.COMPLETED))
                    deleteTransactionJournal(context)

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

    private fun moveDatabaseArtifactsToPending(context: Context): DetachedArtifacts? {
        val dbFile = context.getDatabasePath(AppDatabase.DATABASE_NAME)
        val parent = dbFile.parentFile ?: return null
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
                Thread.sleep(50L)
                if (!pending.deleteRecursively()) return false
            }
        }
        val parent = pending.parentFile
        if (parent != null && parent.name == "reset_pending" && parent.isDirectory) {
            parent.delete()
        }
        return true
    }

    fun restorePending(context: Context, moved: List<Pair<File, File>>): RestorePendingResult {
        val restored = ArrayList<File>()
        val failed = ArrayList<File>()

        for ((original, archived) in moved.asReversed()) {
            if (!original.exists() && archived.exists()) {
                val parent = original.parentFile
                if (parent != null && !parent.exists()) parent.mkdirs()
                var ok = false
                try {
                    if (archived.renameTo(original)) {
                        ok = true
                    } else {
                        archived.copyTo(original, overwrite = true)
                        if (archived.delete()) {
                            ok = true
                        }
                    }
                } catch (_: Exception) {
                    ok = false
                }
                if (ok) {
                    restored.add(original)
                } else {
                    failed.add(archived)
                }
            }
        }

        val success = failed.isEmpty()
        return RestorePendingResult(
            isSuccess = success,
            restoredFiles = restored,
            failedFiles = failed,
            errorReason = if (success) null else "Could not restore ${failed.size} file(s)."
        )
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
     * Cleans app-owned private directories safely using trusted system Context roots and
     * canonical path boundary checks. Preserves [reset_transaction.json] during cleanup.
     */
    private fun clearPrivateDirectories(context: Context): Boolean {
        var success = true
        val journalFile = getTransactionJournalFile(context).canonicalFile

        val trustedRoots = LinkedHashSet<File>()
        context.filesDir?.let { trustedRoots.add(it) }
        context.cacheDir?.let { trustedRoots.add(it) }

        try {
            context.getExternalFilesDir(null)?.let { trustedRoots.add(it) }
            context.externalCacheDir?.let { trustedRoots.add(it) }
            context.getExternalFilesDirs(null)?.filterNotNull()?.forEach { trustedRoots.add(it) }
            context.getExternalCacheDirs()?.filterNotNull()?.forEach { trustedRoots.add(it) }
        } catch (_: Exception) {}

        val canonicalRoots = trustedRoots.mapNotNull {
            try {
                if (it.exists() || it.mkdirs()) it.canonicalFile else null
            } catch (_: Exception) {
                null
            }
        }.filter { root ->
            root.path.contains(context.packageName) &&
                (!root.path.startsWith("/storage") || android.os.Environment.getExternalStorageState(root) == android.os.Environment.MEDIA_MOUNTED)
        }.distinctBy { it.path }

        for (trustedRoot in canonicalRoots) {
            val files = trustedRoot.listFiles() ?: continue
            for (file in files) {
                try {
                    val canonicalChild = file.canonicalFile
                    if (canonicalChild.path == journalFile.path) continue

                    val isWithinRoot = canonicalChild.path == trustedRoot.path ||
                            canonicalChild.path.startsWith(trustedRoot.path + File.separator)

                    if (isWithinRoot) {
                        if (!canonicalChild.deleteRecursively()) {
                            success = false
                        }
                    }
                } catch (_: Exception) {
                    success = false
                }
            }
        }
        return success
    }

    private fun getTransactionJournalFile(context: Context): File {
        val dir = context.createDeviceProtectedStorageContext().filesDir
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "reset_transaction.json")
    }

    fun writeTransaction(context: Context, transaction: ResetTransaction): Boolean {
        return try {
            val file = getTransactionJournalFile(context)
            val json = buildString {
                append("{\n")
                append("  \"stage\": \"${transaction.stage.name}\",\n")
                append("  \"timestampMs\": ${transaction.timestampMs},\n")
                append("  \"manifest\": [\n")
                transaction.manifest.forEachIndexed { i, entry ->
                    append("    {\"original\": \"${escapeJson(entry.originalPath)}\", \"staged\": \"${escapeJson(entry.stagedPath)}\"}")
                    if (i < transaction.manifest.size - 1) append(",")
                    append("\n")
                }
                append("  ]\n")
                append("}\n")
            }
            file.writeText(json)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun readTransaction(context: Context): ResetTransaction? {
        return try {
            val file = getTransactionJournalFile(context)
            if (!file.exists()) return null
            val text = file.readText()
            val stageName = regexValue(text, "stage") ?: return null
            val stage = try { ResetStage.valueOf(stageName) } catch (_: Exception) { return null }
            val timestampMs = regexValue(text, "timestampMs")?.toLongOrNull() ?: System.currentTimeMillis()
            val entries = parseManifestEntries(text)
            ResetTransaction(stage, timestampMs, entries)
        } catch (_: Exception) {
            null
        }
    }

    fun deleteTransactionJournal(context: Context): Boolean {
        val file = getTransactionJournalFile(context)
        if (!file.exists()) return true
        return file.delete()
    }

    private fun escapeJson(str: String): String =
        str.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun regexValue(text: String, key: String): String? {
        val pattern = Regex("\"$key\"\\s*:\\s*\"?([^\",\\n]+)\"?")
        return pattern.find(text)?.groupValues?.get(1)?.trim()
    }

    private fun parseManifestEntries(text: String): List<ManifestEntry> {
        val list = ArrayList<ManifestEntry>()
        val pattern = Regex("\\{\\s*\"original\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"staged\"\\s*:\\s*\"([^\"]+)\"\\s*\\}")
        for (match in pattern.findAll(text)) {
            val orig = match.groupValues[1]
            val staged = match.groupValues[2]
            list.add(ManifestEntry(orig, staged))
        }
        return list
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
