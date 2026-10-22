/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * File-level recovery tests.
 *
 * These deliberately do not claim to exercise native SQLCipher. They verify the state machine
 * that decides whether a missing/empty file is a fresh install, an interrupted swap, or a
 * damaged install that must fail closed, plus every rename/rollback branch of the plaintext
 * migration coordinator using injectable readers/exporters.
 */
class DatabaseRecoveryStateTest {

    private fun directory(): File = Files.createTempDirectory("nova-db-recovery").toFile()

    private fun family(dir: File): DatabaseFileFamily =
        DatabaseFileFamily.from(File(dir, "dialer_database"))

    private fun write(file: File, content: String) {
        file.parentFile?.mkdirs()
        file.writeText(content)
    }

    private fun sourceSnapshot() = DatabaseSnapshot(
        integrityOk = true,
        userVersion = 9,
        tableCounts = linkedMapOf("contacts" to 2L, "call_history" to 3L)
    )

    @Test
    fun missingEverythingIsFreshInstallOnlyWithoutKeyMaterial() {
        val dir = directory()
        assertTrue(DatabaseFileGuard.plan(family(dir), hasPersistedKey = false) is DatabaseFilePlan.FreshInstall)
        val withKey = DatabaseFileGuard.plan(family(dir), hasPersistedKey = true)
        assertTrue(withKey is DatabaseFilePlan.FailClosed)
    }

    @Test
    fun missingActiveRestoresSwapAndPreservesOriginal() {
        val dir = directory()
        val files = family(dir)
        write(files.swapBackup, "original")

        val plan = DatabaseFileGuard.plan(files, hasPersistedKey = true)
        assertTrue(plan is DatabaseFilePlan.RestoreSwap)
        val restore = plan as DatabaseFilePlan.RestoreSwap
        assertFalse(restore.quarantineEmptyActive)

        DatabaseFileGuard.restoreInterruptedSwap(files, restore)
        assertEquals("original", files.active.readText())
        assertFalse(files.swapBackup.exists())
    }

    @Test
    fun zeroByteActiveIsQuarantinedBeforeSwapRestore() {
        val dir = directory()
        val files = family(dir)
        files.active.parentFile?.mkdirs()
        files.active.writeText("")
        write(files.swapBackup, "original")

        val plan = DatabaseFileGuard.plan(files, hasPersistedKey = true)
        assertTrue(plan is DatabaseFilePlan.RestoreSwap)
        DatabaseFileGuard.restoreInterruptedSwap(files, plan as DatabaseFilePlan.RestoreSwap)

        assertEquals("original", files.active.readText())
        val quarantine = File(dir, "dialer_database_empty_bak.db")
        assertTrue(quarantine.exists())
        assertEquals(0L, quarantine.length())
        assertFalse(files.swapBackup.exists())
    }

    @Test
    fun staleSidecarsAreQuarantinedBeforeRestoringMissingActive() {
        val dir = directory()
        val files = family(dir)
        write(files.swapBackup, "original")
        write(File(files.active.path + "-wal"), "stale-wal")
        write(File(files.active.path + "-shm"), "stale-shm")

        val plan = DatabaseFileGuard.plan(files, hasPersistedKey = true)
        assertTrue(plan is DatabaseFilePlan.RestoreSwap)
        DatabaseFileGuard.restoreInterruptedSwap(files, plan as DatabaseFilePlan.RestoreSwap)

        assertEquals("original", files.active.readText())
        assertFalse(File(files.active.path + "-wal").exists())
        assertFalse(File(files.active.path + "-shm").exists())
        val quarantine = File(dir, "dialer_database_empty_bak.db")
        assertEquals("stale-wal", File(quarantine.path + "-wal").readText())
        assertEquals("stale-shm", File(quarantine.path + "-shm").readText())
    }

    @Test
    fun zeroByteActiveWithoutRecoveryFailsClosed() {
        val dir = directory()
        val files = family(dir)
        files.active.parentFile?.mkdirs()
        files.active.writeText("")

        val plan = DatabaseFileGuard.plan(files, hasPersistedKey = false)
        assertTrue(plan is DatabaseFilePlan.FailClosed)
    }

    @Test
    fun missingActiveWithOnlyTempSidecarOrLegacyMaterialFailsClosed() {
        val tempDir = directory()
        write(family(tempDir).encryptedTemp, "partial")
        assertTrue(DatabaseFileGuard.plan(family(tempDir), false) is DatabaseFilePlan.FailClosed)

        val sidecarDir = directory()
        write(File(sidecarDir, "dialer_database-wal"), "wal")
        assertTrue(DatabaseFileGuard.plan(family(sidecarDir), false) is DatabaseFilePlan.FailClosed)

        val legacyDir = directory()
        write(family(legacyDir).legacyPlaintextBackup, "legacy")
        assertTrue(DatabaseFileGuard.plan(family(legacyDir), false) is DatabaseFilePlan.FailClosed)
    }

    @Test
    fun nonEmptyActivePreservesSwapUntilValidation() {
        val dir = directory()
        val files = family(dir)
        write(files.active, "active")
        write(files.swapBackup, "original")

        val plan = DatabaseFileGuard.plan(files, hasPersistedKey = true)
        assertTrue(plan is DatabaseFilePlan.Active)
        assertEquals(listOf(files.swapBackup), (plan as DatabaseFilePlan.Active).preservedArtifacts)
        assertTrue(files.swapBackup.exists())
    }

    @Test
    fun failedSwapRestoreNeverDeletesTheOnlyRecoveryFile() {
        val dir = directory()
        val files = family(dir)
        val fake = FakeFileOps(mutableMapOf(files.swapBackup to 8L)) { from, _ ->
            from == files.swapBackup
        }

        val plan = DatabaseFilePlan.RestoreSwap(
            quarantineEmptyActive = false,
            preservedArtifacts = listOf(files.swapBackup)
        )
        assertThrows(DatabaseRecoveryRequiredException::class.java) {
            DatabaseFileGuard.restoreInterruptedSwap(files, plan, fake)
        }
        assertTrue(fake.exists(files.swapBackup))
    }

    @Test
    fun successfulMigrationSwapsValidatedRowCountsAndLeavesNoPlaintextBackup() {
        val dir = directory()
        val files = family(dir)
        write(files.active, "plain-source")
        val reader = FakeReader(sourceSnapshot(), sourceSnapshot())
        val coordinator = PlaintextMigrationCoordinator(
            files, ByteArray(32), reader, FakeExporter(), RealDatabaseFileOps
        )

        val result = coordinator.migrate()

        assertTrue(result is PlaintextMigrationCoordinator.Result.Migrated)
        assertTrue(files.active.readText().startsWith("encrypted:"))
        assertFalse(files.swapBackup.exists())
        assertFalse(files.encryptedTemp.exists())
        assertFalse(files.legacyPlaintextBackup.exists())
    }

    @Test
    fun existingIncompleteTempIsPreservedAndOriginalIsUntouched() {
        val dir = directory()
        val files = family(dir)
        write(files.active, "plain-source")
        write(files.encryptedTemp, "partial")
        val reader = object : DatabaseSnapshotReader {
            override fun readPlaintext(file: File): DatabaseSnapshot? = sourceSnapshot()
            override fun readEncrypted(file: File, key: ByteArray): DatabaseSnapshot? = null
        }
        val coordinator = PlaintextMigrationCoordinator(
            files, ByteArray(32), reader, FakeExporter(), RealDatabaseFileOps
        )

        val result = coordinator.migrate()

        assertTrue(result is PlaintextMigrationCoordinator.Result.Failed)
        assertEquals("plain-source", files.active.readText())
        assertEquals("partial", files.encryptedTemp.readText())
    }

    @Test
    fun incompleteExportIsNotInstalled() {
        val dir = directory()
        val files = family(dir)
        write(files.active, "plain-source")
        val wrong = DatabaseSnapshot(true, 9, linkedMapOf("contacts" to 1L, "call_history" to 1L))
        val reader = FakeReader(sourceSnapshot(), wrong)
        val coordinator = PlaintextMigrationCoordinator(
            files, ByteArray(32), reader, FakeExporter(), RealDatabaseFileOps
        )

        val result = coordinator.migrate()

        assertTrue(result is PlaintextMigrationCoordinator.Result.Failed)
        assertEquals("plain-source", files.active.readText())
        assertTrue(files.encryptedTemp.exists())
        assertFalse(files.swapBackup.exists())
    }

    @Test
    fun failedInstallRestoresOriginalAndPreservesEncryptedTemp() {
        val dir = directory()
        val files = family(dir)
        write(files.active, "plain-source")
        val ops = FailRenameOps { from, _ -> from == files.encryptedTemp }
        val coordinator = PlaintextMigrationCoordinator(
            files, ByteArray(32), FakeReader(sourceSnapshot(), sourceSnapshot()), FakeExporter(), ops
        )

        val result = coordinator.migrate()

        assertTrue(result is PlaintextMigrationCoordinator.Result.Failed)
        assertEquals("plain-source", files.active.readText())
        assertTrue(files.encryptedTemp.exists())
        assertFalse(files.swapBackup.exists())
    }

    @Test
    fun failedInstallAndFailedRestoreThrowsWithBothFilesPreserved() {
        val dir = directory()
        val files = family(dir)
        write(files.active, "plain-source")
        val ops = FailRenameOps { from, _ ->
            from == files.encryptedTemp || from == files.swapBackup
        }
        val coordinator = PlaintextMigrationCoordinator(
            files, ByteArray(32), FakeReader(sourceSnapshot(), sourceSnapshot()), FakeExporter(), ops
        )

        assertThrows(DatabaseRecoveryRequiredException::class.java) {
            coordinator.migrate()
        }
        assertFalse(files.active.exists())
        assertEquals("plain-source", files.swapBackup.readText())
        assertTrue(files.encryptedTemp.exists())
    }

    @Test
    fun failedOriginalRenameLeavesOriginalInPlace() {
        val dir = directory()
        val files = family(dir)
        write(files.active, "plain-source")
        val ops = FailRenameOps { from, _ -> from == files.active }
        val coordinator = PlaintextMigrationCoordinator(
            files, ByteArray(32), FakeReader(sourceSnapshot(), sourceSnapshot()), FakeExporter(), ops
        )

        val result = coordinator.migrate()

        assertTrue(result is PlaintextMigrationCoordinator.Result.Failed)
        assertEquals("plain-source", files.active.readText())
        assertFalse(files.swapBackup.exists())
    }

    private class FakeReader(
        private val plaintext: DatabaseSnapshot?,
        private val encrypted: DatabaseSnapshot?
    ) : DatabaseSnapshotReader {
        override fun readPlaintext(file: File): DatabaseSnapshot? =
            plaintext.takeIf { file.exists() }

        override fun readEncrypted(file: File, key: ByteArray): DatabaseSnapshot? =
            encrypted.takeIf { file.exists() }
    }

    private class FakeExporter : DatabaseEncryptionExporter {
        override fun export(plaintext: File, encrypted: File, key: ByteArray): Boolean {
            encrypted.writeText("encrypted:${plaintext.readText()}")
            return true
        }
    }

    private class FailRenameOps(
        private val fail: (File, File) -> Boolean
    ) : DatabaseFileOps {
        private val delegate = RealDatabaseFileOps
        override fun exists(file: File): Boolean = delegate.exists(file)
        override fun length(file: File): Long = delegate.length(file)
        override fun rename(from: File, to: File): Boolean =
            if (fail(from, to)) false else delegate.rename(from, to)
        override fun delete(file: File): Boolean = delegate.delete(file)
    }

    private class FakeFileOps(
        private val lengths: MutableMap<File, Long>,
        private val failRename: (File, File) -> Boolean
    ) : DatabaseFileOps {
        override fun exists(file: File): Boolean = lengths.containsKey(file)
        override fun length(file: File): Long = lengths[file] ?: 0L
        override fun rename(from: File, to: File): Boolean {
            if (failRename(from, to)) return false
            val length = lengths.remove(from) ?: return false
            lengths[to] = length
            return true
        }
        override fun delete(file: File): Boolean = lengths.remove(file) != null
    }
}
