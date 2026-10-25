package com.example.data

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import androidx.core.content.edit

object DatabaseKeyManager {
    private const val KEY_ALIAS = "DialerDbMasterKey"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val PREFS_NAME = "dialer_secure_device_prefs"
    private const val ENCRYPTED_DB_KEY = "encrypted_db_key"
    private const val GCM_IV = "gcm_iv"
    private const val GCM_TAG_LENGTH = 128
    private const val DATABASE_NAME = "dialer_database"

    // In-memory key cache to eliminate 50ms Keystore IPC overhead on every DB query
    @Volatile
    private var cachedKey: ByteArray? = null

    /**
     * True when any database-key metadata exists. Used by the file-family preflight so a
     * missing database with surviving key material is treated as damaged, not as a fresh
     * installation that may mint a new key.
     */
    fun hasPersistedKey(context: Context): Boolean {
        val prefs = context.createDeviceProtectedStorageContext()
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.contains(ENCRYPTED_DB_KEY) || prefs.contains(GCM_IV)
    }

    /**
     * Status of an explicit key erasure. Both halves are verified instead of assumed.
     */
    data class KeyClearResult(
        val aliasRemoved: Boolean,
        val preferencesCleared: Boolean
    ) {
        val success: Boolean get() = aliasRemoved && preferencesCleared
    }

    /**
     * Deletes the KeyStore alias and the persisted key metadata, verifying both.
     *
     * The alias is removed first: if that fails, the key material is still intact and the caller
     * can abort the reset without leaving an unreadable database behind.
     */
    @Synchronized
    fun clearKeyDetailed(context: Context): KeyClearResult {
        cachedKey = null

        var aliasRemoved = true
        val keyStore = try {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        } catch (_: Exception) {
            // No AndroidKeyStore provider (for example a stripped test/emulator image): no alias
            // can exist, so there is nothing to erase.
            null
        }
        if (keyStore != null) {
            try {
                if (keyStore.containsAlias(KEY_ALIAS)) {
                    keyStore.deleteEntry(KEY_ALIAS)
                }
                aliasRemoved = !keyStore.containsAlias(KEY_ALIAS)
            } catch (_: Exception) {
                aliasRemoved = false
            }
        }

        if (!aliasRemoved) {
            return KeyClearResult(aliasRemoved = false, preferencesCleared = false)
        }

        var preferencesCleared = false
        try {
            val directBootContext = context.createDeviceProtectedStorageContext()
            val prefs = directBootContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit(commit = true) { clear() }
            preferencesCleared = !prefs.contains(ENCRYPTED_DB_KEY) && !prefs.contains(GCM_IV)
        } catch (_: Exception) {
            preferencesCleared = false
        }

        return KeyClearResult(aliasRemoved = true, preferencesCleared = preferencesCleared)
    }

    /** Boolean wrapper kept for callers that only need the verified success flag. */
    @Synchronized
    fun clearKey(context: Context): Boolean = clearKeyDetailed(context).success

    @Synchronized
    fun getDatabaseKey(context: Context): ByteArray {
        // 1. Fast-path: return cached key if already unlocked in memory
        cachedKey?.let {
            return it.copyOf()
        }

        // 2. Direct Boot safe context: ensures accessibility across reboots/incoming calls
        // minSdk 24 >= N (24); device-protected storage is always available.
        val directBootContext = context.createDeviceProtectedStorageContext()

        val prefs = directBootContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val encryptedKeyBase64 = prefs.getString(ENCRYPTED_DB_KEY, null)
        val ivBase64 = prefs.getString(GCM_IV, null)

        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

        // 3. Decrypt existing key. If the Keystore material was invalidated we must fail
        // closed: minting a new passphrase here would make the existing encrypted database
        // permanently unreadable and would invite the old code to delete it.
        if (encryptedKeyBase64 != null && ivBase64 != null) {
            try {
                val encryptedKey = Base64.decode(encryptedKeyBase64, Base64.NO_WRAP)
                val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
                val secretKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
                    ?: throw IllegalStateException("The Android Keystore master key is missing.")
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
                val decrypted = cipher.doFinal(encryptedKey)

                cachedKey = decrypted.copyOf()
                return decrypted.copyOf()
            } catch (e: Exception) {
                throw IllegalStateException(
                    "The existing dialer database key could not be decrypted. " +
                        "The database and its key material were left untouched.",
                    e
                )
            }
        }

        // A partial prefs record is corruption, not a reason to mint a new key.
        if (encryptedKeyBase64 != null || ivBase64 != null) {
            throw IllegalStateException(
                "The dialer database key metadata is incomplete. " +
                    "The database was left untouched."
            )
        }

        // 4. Any existing encrypted database file without a stored key cannot be opened.
        // A zero-byte file is still evidence of a previous database; never mint a fresh key
        // over it, because that would make a later recovery attempt ambiguous.
        val databaseFile = context.getDatabasePath(DATABASE_NAME)
        if (databaseFile.exists() && !isPlaintextDatabase(databaseFile)) {
            throw IllegalStateException(
                "An encrypted dialer database exists but its key is missing. " +
                    "The database was left untouched."
            )
        }

        // 5. Generate new 256-bit database passphrase (fresh install, or a legacy plaintext DB
        // that AppDatabase will migrate safely after the key exists).
        val secureRandom = SecureRandom()
        val rawDbKey = ByteArray(32)
        secureRandom.nextBytes(rawDbKey)

        try {
            val secretKey = getOrCreateMasterKey(keyStore)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val encryptedKey = cipher.doFinal(rawDbKey)

            prefs.edit(commit = true) {
                putString(ENCRYPTED_DB_KEY, Base64.encodeToString(encryptedKey, Base64.NO_WRAP))
                putString(GCM_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
            } // Commit immediately to ensure durability before opening DB

            cachedKey = rawDbKey.copyOf()
            return rawDbKey.copyOf()
        } catch (e: Exception) {
            Arrays.fill(rawDbKey, 0.toByte())
            throw IllegalStateException("Hardware-backed master key generation failed.", e)
        }
    }

    private fun isPlaintextDatabase(file: java.io.File): Boolean {
        if (!file.exists() || file.length() < 16L) return false
        return try {
            file.inputStream().use { stream ->
                val header = ByteArray(16)
                stream.read(header) == 16 &&
                    String(header, Charsets.US_ASCII).startsWith("SQLite format 3")
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun getOrCreateMasterKey(keyStore: KeyStore): SecretKey {
        if (keyStore.containsAlias(KEY_ALIAS)) {
            return keyStore.getKey(KEY_ALIAS, null) as SecretKey
        }

        // Try StrongBox HSM first, fallback to standard TEE if unavailable
        return try {
            generateKey(isStrongBox = true)
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && e is StrongBoxUnavailableException) {
                generateKey(isStrongBox = false)
            } else {
                generateKey(isStrongBox = false)
            }
        }
    }

    private fun generateKey(isStrongBox: Boolean): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && isStrongBox) {
            builder.setIsStrongBoxBacked(true)
        }

        keyGenerator.init(builder.build())
        return keyGenerator.generateKey()
    }
}
