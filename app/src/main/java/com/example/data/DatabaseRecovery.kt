/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.data

import java.io.File

/**
 * Files that belong to the dialer database.
 *
 * The active file is the only file Room opens. The remaining files are crash-recovery or
 * legacy-migration artifacts and are never treated as a fresh-install marker.
 */
internal data class DatabaseFileFamily(
    val active: File,
    val encryptedTemp: File,
    val swapBackup: File,
    val legacyPlaintextBackup: File,
    val unreadableBackup: File
) {
    val sidecars: List<File> = SIDECAR_SUFFIXES.map { File(active.path + it) }

    val recoveryArtifacts: List<File> = buildList {
        add(encryptedTemp)
        add(swapBackup)
        add(legacyPlaintextBackup)
        add(unreadableBackup)
        addAll(sidecars)
    }

    companion object {
        private val SIDECAR_SUFFIXES = listOf("-wal", "-shm", "-journal")

        fun from(databaseFile: File): DatabaseFileFamily {
            val parent = databaseFile.parentFile ?: File(".")
            val name = databaseFile.name
            return DatabaseFileFamily(
                active = databaseFile,
                encryptedTemp = File(parent, "${name}_encrypted_temp.db"),
                swapBackup = File(parent, "${name}_swap_original.db"),
                legacyPlaintextBackup = File(parent, "${name}_plaintext_bak.db"),
                unreadableBackup = File(parent, "${name}_unreadable_bak.db")
            )
        }
    }
}

/** File operations used by the recovery coordinator so failure paths can be tested. */
internal interface DatabaseFileOps {
    fun exists(file: File): Boolean
    fun length(file: File): Long
    fun rename(from: File, to: File): Boolean
    fun delete(file: File): Boolean
}

internal object RealDatabaseFileOps : DatabaseFileOps {
    override fun exists(file: File): Boolean = file.exists()
    override fun length(file: File): Long = if (file.exists()) file.length() else 0L
    override fun rename(from: File, to: File): Boolean = from.renameTo(to)
    override fun delete(file: File): Boolean = file.delete()
}

/** The safe next step after inspecting the database file family. */
internal sealed interface DatabaseFilePlan {
    /** No database, no key material and no recovery artifact: a genuine fresh install. */
    data object FreshInstall : DatabaseFilePlan

    /** A non-empty active database exists; validate it with SQLCipher before opening Room. */
    data class Active(val preservedArtifacts: List<File>) : DatabaseFilePlan

    /** The active file is missing/empty and a same-directory swap contains the original. */
    data class RestoreSwap(
        val quarantineEmptyActive: Boolean,
        val preservedArtifacts: List<File>
    ) : DatabaseFilePlan

    /** A previous database or recovery artifact exists but cannot be proven safe. */
    data class FailClosed(
        val reason: String,
        val preservedArtifacts: List<File>
    ) : DatabaseFilePlan
}

internal object DatabaseFileGuard {

    /**
     * Decides whether this is a fresh install, a recoverable interrupted swap, or a damaged
     * installation that must not be replaced with an empty database.
     *
     * A missing file is only a fresh install when there is no key material and no recovery file.
     * In particular, a missing database plus `_swap_original.db`, `_encrypted_temp.db`, a legacy
     * plaintext copy, or a `-wal`/`-shm`/`-journal` sidecar is never treated as new.
     */
    fun plan(
        family: DatabaseFileFamily,
        hasPersistedKey: Boolean,
        ops: DatabaseFileOps = RealDatabaseFileOps
    ): DatabaseFilePlan {
        val activeExists = ops.exists(family.active)
        val activeLength = if (activeExists) ops.length(family.active) else 0L
        val activeUsable = activeExists && activeLength > 0L
        val artifacts = family.recoveryArtifacts.filter { ops.exists(it) }

        if (activeUsable) {
            // A completed or interrupted migration may have left the original beside a valid
            // active file. Keep it; validation decides whether it is safe to clean up later.
            return DatabaseFilePlan.Active(artifacts)
        }

        if (ops.exists(family.swapBackup)) {
            return DatabaseFilePlan.RestoreSwap(
                quarantineEmptyActive = activeExists,
                preservedArtifacts = artifacts
            )
        }

        if (activeExists) {
            return DatabaseFilePlan.FailClosed(
                reason = "The dialer database file exists but is empty or unreadable. " +
                    "It was left untouched so it can be recovered.",
                preservedArtifacts = artifacts
            )
        }

        val parent = family.active.parentFile ?: File(".")
        val pendingRoot = File(parent, "reset_pending")
        val pendingExists = ops.exists(pendingRoot) && (pendingRoot.listFiles()?.isNotEmpty() == true)

        if (artifacts.isNotEmpty() || hasPersistedKey || pendingExists) {
            val allArtifacts = ArrayList(artifacts)
            if (pendingExists) allArtifacts.add(pendingRoot)
            return DatabaseFilePlan.FailClosed(
                reason = "The dialer database is missing, but previous database material or reset staging files still " +
                    "exist. It was left untouched so it can be recovered instead of replaced.",
                preservedArtifacts = allArtifacts
            )
        }

        return DatabaseFilePlan.FreshInstall
    }

    /**
     * Restores the original file after an interrupted plaintext-to-encrypted swap.
     *
     * Any empty active file is moved aside first, never deleted. If either rename fails, the
     * swap is preserved and an explicit recovery exception is raised before a key can be minted
     * or Room can create a new database.
     */
    fun restoreInterruptedSwap(
        family: DatabaseFileFamily,
        plan: DatabaseFilePlan.RestoreSwap,
        ops: DatabaseFileOps = RealDatabaseFileOps
    ) {
        if (plan.quarantineEmptyActive) {
            val quarantine = uniqueQuarantineFile(family.active, ops)
            if (!ops.rename(family.active, quarantine)) {
                throw DatabaseRecoveryRequiredException(
                    "An interrupted database swap could not be recovered because the empty " +
                        "active database could not be moved aside. All recovery files were preserved."
                )
            }
            moveSidecars(family.active, quarantine, ops)
        } else {
            // The active base file is missing, but a crashed process may have left sidecars
            // that do not belong to the swap backup. Quarantine them instead of mixing them
            // into the restored database.
            val staleSidecars = listOf("-wal", "-shm", "-journal").any {
                ops.exists(File(family.active.path + it))
            }
            if (staleSidecars) {
                moveSidecars(family.active, uniqueQuarantineFile(family.active, ops), ops)
            }
        }

        if (!ops.rename(family.swapBackup, family.active)) {
            throw DatabaseRecoveryRequiredException(
                "An interrupted database swap could not be restored. The original database " +
                    "backup was preserved; Nova Dialer will not create an empty replacement."
            )
        }
        moveSidecars(family.swapBackup, family.active, ops)
    }

    private fun uniqueQuarantineFile(active: File, ops: DatabaseFileOps): File {
        val parent = active.parentFile ?: File(".")
        var candidate = File(parent, "${active.name}_empty_bak.db")
        var suffix = 1
        while (ops.exists(candidate) || listOf("-wal", "-shm", "-journal").any {
                ops.exists(File(candidate.path + it))
            }) {
            candidate = File(parent, "${active.name}_empty_bak_$suffix.db")
            suffix++
        }
        return candidate
    }

    private fun moveSidecars(fromBase: File, toBase: File, ops: DatabaseFileOps) {
        for (suffix in listOf("-wal", "-shm", "-journal")) {
            val from = File(fromBase.path + suffix)
            if (!ops.exists(from)) continue
            val to = File(toBase.path + suffix)
            if (ops.exists(to)) continue
            ops.rename(from, to)
        }
    }
}

/** A validated snapshot of the tables that must survive migration. */
internal data class DatabaseSnapshot(
    val integrityOk: Boolean,
    val userVersion: Int,
    val tableCounts: Map<String, Long>
) {
    val hasCoreTables: Boolean
        get() = tableCounts.keys.containsAll(CORE_TABLES)

    companion object {
        val CORE_TABLES = setOf("contacts", "call_history")
    }
}

internal interface DatabaseSnapshotReader {
    fun readPlaintext(file: File): DatabaseSnapshot?
    fun readEncrypted(file: File, key: ByteArray): DatabaseSnapshot?
}

internal interface DatabaseEncryptionExporter {
    fun export(plaintext: File, encrypted: File, key: ByteArray): Boolean
}

/**
 * Crash-safe plaintext-to-SQLCipher migration.
 *
 * The original file is renamed to a same-directory swap file, the already-validated encrypted
 * export is renamed into place, and the active file is validated again before the plaintext swap
 * is removed. No permanent `_plaintext_bak.db` copy is created by this coordinator.
 */
internal class PlaintextMigrationCoordinator(
    private val family: DatabaseFileFamily,
    private val key: ByteArray,
    private val reader: DatabaseSnapshotReader,
    private val exporter: DatabaseEncryptionExporter,
    private val ops: DatabaseFileOps = RealDatabaseFileOps
) {
    sealed interface Result {
        data object Migrated : Result
        data class Failed(val reason: String) : Result
    }

    fun migrate(): Result {
        val source = reader.readPlaintext(family.active)
            ?: return Result.Failed("The plaintext database could not be read.")

        if (!source.integrityOk || !source.hasCoreTables) {
            return Result.Failed(
                "The plaintext database failed integrity or schema validation; it was not replaced."
            )
        }

        if (ops.exists(family.swapBackup)) {
            return Result.Failed(
                "A previous migration swap file already exists. It was preserved for recovery."
            )
        }

        val existingTemp = ops.exists(family.encryptedTemp)
        val temp = if (existingTemp) {
            reader.readEncrypted(family.encryptedTemp, key)
        } else {
            null
        }

        if (temp != null && !isCompleteEncryptedCopy(temp, source)) {
            // Do not delete unvalidated work from a previous interrupted attempt.
            return Result.Failed(
                "An incomplete encrypted migration file exists and was preserved. " +
                    "The original database was not changed."
            )
        }
        if (temp == null) {
            if (existingTemp) {
                return Result.Failed(
                    "An unreadable encrypted migration file exists and was preserved. " +
                        "The original database was not changed."
                )
            }
            if (!exporter.export(family.active, family.encryptedTemp, key)) {
                return Result.Failed("The encrypted database export failed.")
            }
            val exported = reader.readEncrypted(family.encryptedTemp, key)
                ?: return Result.Failed(
                    "The encrypted database export could not be opened; the original was kept."
                )
            if (!isCompleteEncryptedCopy(exported, source)) {
                return Result.Failed(
                    "The encrypted database export was incomplete or failed validation; " +
                        "the original was kept."
                )
            }
        }

        // The encrypted copy is complete. Move the original aside, then install the copy.
        if (!moveFamily(family.active, family.swapBackup)) {
            return Result.Failed(
                "The original database could not be moved aside; the original and the encrypted " +
                    "copy were preserved."
            )
        }

        if (!moveFamily(family.encryptedTemp, family.active)) {
            if (!moveFamily(family.swapBackup, family.active)) {
                throw DatabaseRecoveryRequiredException(
                    "The encrypted database could not be installed and the original could not be " +
                        "restored automatically. Both files were preserved for recovery."
                )
            }
            return Result.Failed(
                "The encrypted database could not be installed; the original was restored."
            )
        }

        val installed = reader.readEncrypted(family.active, key)
        if (installed == null || !isCompleteEncryptedCopy(installed, source)) {
            // Roll back to the plaintext original rather than leaving an unvalidated database.
            if (!moveFamily(family.active, family.encryptedTemp)) {
                throw DatabaseRecoveryRequiredException(
                    "The installed encrypted database failed validation and could not be moved " +
                        "aside. The original plaintext swap was preserved for recovery."
                )
            }
            if (!moveFamily(family.swapBackup, family.active)) {
                throw DatabaseRecoveryRequiredException(
                    "The installed encrypted database failed validation and the original could " +
                        "not be restored automatically. Both files were preserved for recovery."
                )
            }
            return Result.Failed(
                "The installed encrypted database failed validation; the original was restored."
            )
        }

        // Only remove the plaintext swap after the installed copy has been validated. If cleanup
        // fails, leave it for the next launch instead of risking data loss.
        if (!deleteFamily(family.swapBackup)) {
            java.util.logging.Logger.getLogger("NovaDialerDatabase").warning(
                "Encrypted migration succeeded but the plaintext swap could not be removed; " +
                    "it was left in place for a later safe cleanup."
            )
        }
        return Result.Migrated
    }

    private fun isCompleteEncryptedCopy(candidate: DatabaseSnapshot, source: DatabaseSnapshot): Boolean {
        return candidate.integrityOk &&
            candidate.userVersion <= AppDatabase.DATABASE_VERSION &&
            candidate.tableCounts == source.tableCounts
    }

    private fun moveFamily(fromBase: File, toBase: File): Boolean {
        if (!ops.exists(fromBase)) return false
        if (ops.exists(toBase)) return false
        val suffixes = listOf("-wal", "-shm", "-journal")
        for (suffix in suffixes) {
            if (ops.exists(File(toBase.path + suffix))) return false
        }
        if (!ops.rename(fromBase, toBase)) return false

        val moved = mutableListOf<String>()
        for (suffix in suffixes) {
            val from = File(fromBase.path + suffix)
            if (!ops.exists(from)) continue
            val to = File(toBase.path + suffix)
            if (!ops.rename(from, to)) {
                // The main file moved, so roll back every sidecar and the main file.
                for (movedSuffix in moved.asReversed()) {
                    ops.rename(File(toBase.path + movedSuffix), File(fromBase.path + movedSuffix))
                }
                ops.rename(toBase, fromBase)
                return false
            }
            moved.add(suffix)
        }
        return true
    }

    private fun deleteFamily(base: File): Boolean {
        var ok = true
        if (ops.exists(base) && !ops.delete(base)) ok = false
        for (suffix in listOf("-wal", "-shm", "-journal")) {
            val file = File(base.path + suffix)
            if (ops.exists(file) && !ops.delete(file)) ok = false
        }
        return ok
    }
}
