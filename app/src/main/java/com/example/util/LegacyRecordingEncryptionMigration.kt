/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.util

import com.example.data.DialerDao
import com.example.model.AppSetting
import java.io.File

/**
 * One-shot security migration for recordings that were written before at-rest encryption existed.
 *
 * This is deliberately *not* a recording recovery scan:
 * - it never lists or walks the legacy recording directory;
 * - it never inserts a recording row, so startup cannot resurrect a deleted call;
 * - it only inspects rows that already exist in `call_recordings`;
 * - it reuses [RecordingCrypto.encryptInPlace], which writes the encrypted copy first and deletes
 *   the plaintext only after the rename succeeds, so a failure keeps the original file;
 * - it records completion in `app_settings` and does no further work once every row is secured.
 *
 * A file that cannot be encrypted is left untouched and retried on the next startup instead of
 * being marked as complete. Playback UI and recording start paths remain absent from this release.
 */
object LegacyRecordingEncryptionMigration {

    const val SETTING_KEY = "legacy_recording_encryption_v1"
    const val STATE_DONE = "done"

    data class Result(
        val alreadyDone: Boolean = false,
        val migrated: Int = 0,
        val failed: Int = 0
    ) {
        val complete: Boolean get() = failed == 0
    }

    suspend fun run(dao: DialerDao): Result {
        if (!needsMigration(dao)) return Result(alreadyDone = true)

        val rows = try {
            dao.getAllCallRecordingsList()
        } catch (_: Exception) {
            return Result(failed = 1)
        }

        var migrated = 0
        var failed = 0
        for (row in rows) {
            val file = File(row.filePath)
            if (!file.exists() || RecordingCrypto.isEncrypted(file)) continue

            val encrypted = try {
                RecordingCrypto.encryptInPlace(file)
            } catch (_: Exception) {
                null
            }
            if (encrypted == null) {
                failed++
            } else {
                try {
                    dao.updateCallRecordingPath(row.id, encrypted.absolutePath)
                    migrated++
                } catch (_: Exception) {
                    // The file is already encrypted under the new name; only the row update failed.
                    // Leave the marker unset so the next start retries the metadata repair.
                    failed++
                }
            }
        }

        if (failed == 0) {
            try {
                dao.insertSetting(AppSetting(SETTING_KEY, STATE_DONE))
            } catch (_: Exception) {
                // No marker means one extra marker check next start; never a data loss.
            }
        }

        return Result(migrated = migrated, failed = failed)
    }

    /** A single settings lookup keeps the done state from rescanning recording rows on every start. */
    suspend fun needsMigration(dao: DialerDao): Boolean {
        return try {
            dao.getSetting(SETTING_KEY) != STATE_DONE
        } catch (_: Exception) {
            true
        }
    }
}
