/*
 * Copyright (C) 2026 NovaDial contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.util

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM protection for call recordings while they are at rest.
 *
 * The key lives in the Android Keystore and never leaves it. Recordings are encrypted in place
 * as soon as a call ends. The only plaintext copies are short-lived cache files created for
 * playback, sharing or export, and those stay inside app-private cache storage.
 */
object RecordingCrypto {

    private const val KEY_ALIAS = "NovaDialRecordingKey"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val IV_LENGTH = 12
    private val MAGIC = byteArrayOf(0x4E, 0x56, 0x52, 0x01) // "NVR" + version 1
    private const val ENCRYPTED_SUFFIX = ".enc"

    fun isEncrypted(file: File): Boolean {
        if (!file.exists() || file.length() < (MAGIC.size + IV_LENGTH).toLong()) return false
        if (!file.name.endsWith(ENCRYPTED_SUFFIX)) return false
        return try {
            FileInputStream(file).use { input ->
                val header = ByteArray(MAGIC.size)
                readFully(input, header)
                header.contentEquals(MAGIC)
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Encrypts [file] next to itself and deletes the plaintext only after the encrypted copy
     * has been fully written. Returns the encrypted file, or null when encryption failed (the
     * original file is left untouched in that case).
     */
    fun encryptInPlace(file: File): File? {
        if (!file.exists() || file.length() <= 0L) return null
        if (isEncrypted(file)) return file

        val target = File(file.parentFile, "${file.name}$ENCRYPTED_SUFFIX")
        val temp = File(file.parentFile, "${file.name}$ENCRYPTED_SUFFIX.tmp")
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, recordingKey())
            val iv = cipher.iv

            FileOutputStream(temp).use { fileOut ->
                fileOut.write(MAGIC)
                fileOut.write(iv)
                CipherOutputStream(fileOut, cipher).use { cipherOut ->
                    FileInputStream(file).use { input -> input.copyTo(cipherOut) }
                }
            }

            if (!temp.renameTo(target)) {
                temp.delete()
                return null
            }
            file.delete()
            target
        } catch (_: Exception) {
            try { temp.delete() } catch (_: Exception) {}
            null
        }
    }

    /**
     * Returns a plaintext file suitable for MediaPlayer/FileProvider. Plaintext legacy files
     * are returned unchanged; encrypted files are decrypted into the private cache directory.
     */
    fun playableFile(context: Context, source: File): File? {
        if (!source.exists()) return null
        if (!isEncrypted(source)) return source

        val cacheDir = File(context.cacheDir, "CallRecordings").apply { mkdirs() }
        val target = File(cacheDir, source.name.removeSuffix(ENCRYPTED_SUFFIX))
        if (target.exists() && target.length() > 0L) return target

        return try {
            FileInputStream(source).use { input ->
                val magic = ByteArray(MAGIC.size)
                readFully(input, magic)
                if (!magic.contentEquals(MAGIC)) return null
                val iv = ByteArray(IV_LENGTH)
                readFully(input, iv)

                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, recordingKey(), GCMParameterSpec(GCM_TAG_LENGTH, iv))
                CipherInputStream(input, cipher).use { cipherIn ->
                    FileOutputStream(target).use { output -> cipherIn.copyTo(output) }
                }
            }
            target
        } catch (_: Exception) {
            try { target.delete() } catch (_: Exception) {}
            null
        }
    }

    fun clearPlaybackCache(context: Context) {
        try {
            File(context.cacheDir, "CallRecordings").listFiles()?.forEach { it.delete() }
        } catch (_: Exception) {
        }
    }

    private fun recordingKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun readFully(input: FileInputStream, buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val read = input.read(buffer, offset, buffer.size - offset)
            if (read <= 0) throw IllegalStateException("Unexpected end of recording")
            offset += read
        }
    }
}
