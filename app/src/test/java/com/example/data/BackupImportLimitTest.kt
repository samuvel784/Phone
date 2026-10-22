/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStreamReader
import java.io.Reader
import java.io.StringReader
import java.nio.charset.StandardCharsets

class BackupImportLimitTest {

    @Test
    fun emptyReaderIsTypedEmpty() {
        assertEquals(
            BackupRestoreManager.TextReadResult.Empty,
            BackupRestoreManager.readTextFromReader(StringReader(""), 10)
        )
    }

    @Test
    fun normalAndExactlyAtLimitAreAccepted() {
        assertEquals(
            BackupRestoreManager.TextReadResult.Success("hello"),
            BackupRestoreManager.readTextFromReader(StringReader("hello"), 10)
        )
        assertEquals(
            BackupRestoreManager.TextReadResult.Success("hello"),
            BackupRestoreManager.readTextFromReader(StringReader("hello"), 5)
        )
    }

    @Test
    fun oneCharacterOverLimitIsRejectedWithoutReadingEverything() {
        val source = "x".repeat(100_000)
        val result = BackupRestoreManager.readTextFromReader(StringReader(source), 1000)
        assertEquals(BackupRestoreManager.TextReadResult.TooLarge, result)
    }

    @Test
    fun streamReaderUsesUtf8AndTheSameLimit() {
        val bytes = "héllo".toByteArray(StandardCharsets.UTF_8)
        val reader = InputStreamReader(bytes.inputStream(), StandardCharsets.UTF_8)
        assertEquals(
            BackupRestoreManager.TextReadResult.Success("héllo"),
            BackupRestoreManager.readTextFromReader(reader, 5)
        )
    }

    @Test
    fun throwingStreamBecomesTypedFailure() {
        val failing = object : Reader() {
            override fun read(cbuf: CharArray, off: Int, len: Int): Int = error("provider failed")
            override fun close() = Unit
        }
        assertEquals(
            BackupRestoreManager.TextReadResult.Failure,
            BackupRestoreManager.readTextFromReader(failing, 10)
        )
    }

    @Test
    fun limitsAreDistinctPerFormatAndBounded() {
        val limits = BackupRestoreManager.TextImportKind.entries.associate { it.name to it.maxChars }
        assertEquals(20_000_000, limits["BACKUP"])
        assertEquals(5_000_000, limits["CONTACTS_VCF"])
        assertEquals(2_000_000, limits["TEXT_LIST"])
        assertTrue(limits.values.all { it > 0 })
    }

    @Test
    fun everyConfiguredFormatRejectsOneCharacterPastItsLimit() {
        BackupRestoreManager.TextImportKind.entries.forEach { kind ->
            assertEquals(
                "limit for ${kind.name}",
                BackupRestoreManager.TextReadResult.TooLarge,
                BackupRestoreManager.readTextFromReader(
                    RepeatingReader(kind.maxChars + 1),
                    kind.maxChars
                )
            )
        }
    }

    @Test
    fun oversizedReaderStopsWithinOneCharacterOfTheLimit() {
        val reader = CountingReader(100_000)
        assertEquals(
            BackupRestoreManager.TextReadResult.TooLarge,
            BackupRestoreManager.readTextFromReader(reader, 1_000)
        )
        assertTrue(
            "bounded reader read ${reader.totalRead} characters for a 1,000-character limit",
            reader.totalRead <= 1_001
        )
    }

    private class RepeatingReader(private var remaining: Int) : Reader() {
        override fun read(cbuf: CharArray, off: Int, len: Int): Int {
            if (remaining <= 0) return -1
            val count = minOf(len, remaining)
            java.util.Arrays.fill(cbuf, off, off + count, 'x')
            remaining -= count
            return count
        }

        override fun close() = Unit
    }

    private class CountingReader(private var remaining: Int) : Reader() {
        var totalRead = 0
            private set

        override fun read(cbuf: CharArray, off: Int, len: Int): Int {
            if (remaining <= 0) return -1
            val count = minOf(len, remaining)
            java.util.Arrays.fill(cbuf, off, off + count, 'x')
            remaining -= count
            totalRead += count
            return count
        }

        override fun read(): Int {
            if (remaining <= 0) return -1
            remaining--
            totalRead++
            return 'x'.code
        }

        override fun close() = Unit
    }
}
