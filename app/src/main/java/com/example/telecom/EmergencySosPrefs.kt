/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.telecom

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Emergency SOS configuration.
 *
 * Everything is stored locally in a private SharedPreferences file and is only read when the user
 * triggers (or tests) the SOS action. No data ever leaves the device except the SMS the user sends
 * to their own chosen emergency contacts.
 */
object EmergencySosPrefs {
    private const val FILE = "novadial_emergency_sos"
    private const val KEY_CONTACTS = "emergency_contacts"
    private const val KEY_AUTO_ALERT = "auto_alert_enabled"
    private const val KEY_INCLUDE_LOCATION = "include_location"
    private const val KEY_CALL_NUMBER = "emergency_call_number"
    private const val KEY_CUSTOM_MESSAGE = "custom_message"
    private const val KEY_COUNTDOWN_SECONDS = "countdown_seconds"
    private const val KEY_SHAKE_ENABLED = "shake_to_trigger"
    private const val SEP = "|"

    /** A saved emergency contact. [name] falls back to the number when the user leaves it blank. */
    data class SosContact(val name: String, val number: String)

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // ---- Emergency contacts ---------------------------------------------------

    fun getContacts(context: Context): List<SosContact> =
        (prefs(context).getStringSet(KEY_CONTACTS, emptySet()) ?: emptySet())
            .mapNotNull { raw ->
                val idx = raw.indexOf(SEP)
                if (idx <= 0) {
                    null
                } else {
                    val name = raw.substring(0, idx)
                    val number = raw.substring(idx + 1)
                    if (number.isBlank()) null else SosContact(name.ifBlank { number }, number)
                }
            }
            .sortedBy { it.name.lowercase() }

    fun addContact(context: Context, name: String, number: String) {
        val cleanNumber = number.trim()
        if (cleanNumber.isBlank()) return
        val cleanName = name.trim().ifBlank { cleanNumber }
        // Replace any existing entry that points at the same number so a contact is never duplicated.
        val set = HashSet(prefs(context).getStringSet(KEY_CONTACTS, emptySet()) ?: emptySet())
        set.removeAll { it.substringAfter(SEP, "").trim() == cleanNumber }
        set.add("$cleanName$SEP$cleanNumber")
        prefs(context).edit { putStringSet(KEY_CONTACTS, set) }
    }

    fun removeContact(context: Context, contact: SosContact) {
        val set = HashSet(prefs(context).getStringSet(KEY_CONTACTS, emptySet()) ?: emptySet())
        set.remove("${contact.name}$SEP${contact.number}")
        // Defensive: also drop any raw entry that resolves to the same number.
        set.removeAll { it.substringAfter(SEP, "").trim() == contact.number }
        prefs(context).edit { putStringSet(KEY_CONTACTS, set) }
    }

    // ---- Behaviour toggles ----------------------------------------------------

    fun isAutoAlertEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_AUTO_ALERT, true)
    fun setAutoAlertEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_AUTO_ALERT, enabled) }
    }

    fun isIncludeLocation(context: Context): Boolean = prefs(context).getBoolean(KEY_INCLUDE_LOCATION, true)
    fun setIncludeLocation(context: Context, include: Boolean) {
        prefs(context).edit { putBoolean(KEY_INCLUDE_LOCATION, include) }
    }

    // ---- Number to call -------------------------------------------------------

    fun getEmergencyCallNumber(context: Context): String =
        prefs(context).getString(KEY_CALL_NUMBER, "") ?: ""

    fun setEmergencyCallNumber(context: Context, number: String) {
        prefs(context).edit { putString(KEY_CALL_NUMBER, number.trim()) }
    }

    // ---- Custom alert message -------------------------------------------------

    /** A user-authored alert body. Blank means the built-in default message is used. */
    fun getCustomMessage(context: Context): String =
        prefs(context).getString(KEY_CUSTOM_MESSAGE, "") ?: ""

    fun setCustomMessage(context: Context, message: String) {
        prefs(context).edit { putString(KEY_CUSTOM_MESSAGE, message.trim()) }
    }

    // ---- Countdown before sending ---------------------------------------------

    /** Seconds the user has to cancel after triggering SOS. 0 sends immediately. */
    fun getCountdownSeconds(context: Context): Int =
        prefs(context).getInt(KEY_COUNTDOWN_SECONDS, 5)

    fun setCountdownSeconds(context: Context, seconds: Int) {
        prefs(context).edit { putInt(KEY_COUNTDOWN_SECONDS, seconds.coerceIn(0, 30)) }
    }

    // ---- Shake-to-trigger -----------------------------------------------------

    fun isShakeToTriggerEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SHAKE_ENABLED, false)

    fun setShakeToTriggerEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_SHAKE_ENABLED, enabled) }
    }
}
