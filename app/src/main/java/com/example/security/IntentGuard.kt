/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.security

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import java.security.SecureRandom

/**
 * A per-install secret used to authenticate intents that ask Nova Dialer to perform a
 * sensitive action (answer a call, open the answer screen).
 *
 * MainActivity is exported (it must be, to handle DIAL/VIEW tel: intents). That means
 * any installed app can send it explicit intents. The token is kept in a private
 * SharedPreferences file that only this app can read, so a third-party app cannot
 * forge it. Nova Dialer adds the token to its own notifications and pending intents.
 */
object IntentGuard {

    /** Extra that carries the token on internal intents. */
    const val EXTRA_GUARD_TOKEN = "novadial_guard_token"

    private const val PREFS = "novadial_intent_guard"
    private const val KEY_TOKEN = "token"
    private const val TOKEN_BYTES = 32

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Returns the install's token, creating and durably persisting it once on first use. */
    @SuppressLint("UseKtx") // commit() must be synchronous so first-use persistence is verified.
    @Synchronized
    fun token(context: Context): String {
        val p = prefs(context)
        p.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }?.let { return it }
        val bytes = ByteArray(TOKEN_BYTES).also { SecureRandom().nextBytes(it) }
        val created = bytes.joinToString("") { "%02x".format(it) }
        // The token is embedded in notifications/pending intents that may be used after the
        // process dies. apply() is asynchronous and can lose the first-use value on process
        // death, so commit synchronously and fail closed if it was not persisted.
        if (!p.edit().putString(KEY_TOKEN, created).commit()) {
            throw IllegalStateException("Unable to persist the Nova Dialer intent token")
        }
        val persisted = p.getString(KEY_TOKEN, null)
        if (persisted != created) {
            throw IllegalStateException("Nova Dialer intent token persistence could not be verified")
        }
        return created
    }

    /**
     * True when [candidate] equals the already-persisted install token. This never mints a
     * token as a side effect: an untrusted intent must not be able to initialise the secret.
     */
    fun isValid(context: Context, candidate: String?): Boolean {
        val expected = prefs(context).getString(KEY_TOKEN, null)
        return matches(expected, candidate)
    }

    /**
     * Constant-time-ish comparison of two tokens. Pure so it can be unit-tested.
     * Blank or null values never match.
     */
    fun matches(expected: String?, candidate: String?): Boolean {
        if (expected.isNullOrEmpty() || candidate.isNullOrEmpty()) return false
        if (expected.length != candidate.length) return false
        var diff = 0
        for (i in expected.indices) {
            diff = diff or (expected[i].code xor candidate[i].code)
        }
        return diff == 0
    }
}
