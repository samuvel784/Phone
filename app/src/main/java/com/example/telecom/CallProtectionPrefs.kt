/*
 * Copyright (C) 2026 NovaDial contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.telecom

import android.content.Context
import android.content.SharedPreferences
import com.example.util.PhoneNumberCompat
import androidx.core.content.edit

/**
 * Settings for Focus mode, call protection, VIP numbers and the caller announcer.
 * Stored in a private SharedPreferences file; read by the screening service and the announcer.
 */
object CallProtectionPrefs {
    private const val FILE = "novadial_call_protection"

    private const val KEY_FOCUS = "focus_mode"
    private const val KEY_PROTECTION = "protection_level"
    private const val KEY_ALLOW_REPEAT = "allow_repeat_callers"
    private const val KEY_VIP = "vip_numbers"

    private const val KEY_ANN_ENABLED = "announcer_enabled"
    private const val KEY_ANN_TEMPLATE = "announcer_template"
    private const val KEY_ANN_USER = "announcer_user_name"
    private const val KEY_ANN_REPEAT = "announcer_repeat"
    private const val KEY_ANN_SPEED = "announcer_speed_percent"
    private const val KEY_ANN_PITCH = "announcer_pitch_percent"
    private const val KEY_ANN_VOLUME = "announcer_volume_percent"
    private const val KEY_ANN_HEADPHONES = "announcer_headphones_only"
    private const val KEY_ANN_UNKNOWN = "announcer_unknown_numbers"
    private const val KEY_ANN_VOICE = "announcer_voice"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // ---- Focus and protection -------------------------------------------------

    fun getFocusMode(context: Context): FocusMode =
        runCatching { FocusMode.valueOf(prefs(context).getString(KEY_FOCUS, FocusMode.OFF.name) ?: FocusMode.OFF.name) }
            .getOrDefault(FocusMode.OFF)

    fun setFocusMode(context: Context, mode: FocusMode) {
        prefs(context).edit { putString(KEY_FOCUS, mode.name) }
    }

    fun getProtectionLevel(context: Context): ProtectionLevel =
        runCatching { ProtectionLevel.valueOf(prefs(context).getString(KEY_PROTECTION, ProtectionLevel.STANDARD.name) ?: ProtectionLevel.STANDARD.name) }
            .getOrDefault(ProtectionLevel.STANDARD)

    fun setProtectionLevel(context: Context, level: ProtectionLevel) {
        prefs(context).edit { putString(KEY_PROTECTION, level.name) }
    }

    fun isRepeatCallerAllowed(context: Context): Boolean = prefs(context).getBoolean(KEY_ALLOW_REPEAT, true)

    fun setRepeatCallerAllowed(context: Context, allowed: Boolean) {
        prefs(context).edit { putBoolean(KEY_ALLOW_REPEAT, allowed) }
    }

    // ---- VIP numbers ----------------------------------------------------------

    fun getVipNumbers(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_VIP, emptySet())?.toSet() ?: emptySet()

    fun addVipNumber(context: Context, number: String) {
        val clean = number.trim()
        if (clean.isEmpty()) return
        prefs(context).edit { putStringSet(KEY_VIP, HashSet(getVipNumbers(context) + clean)) }
    }

    fun removeVipNumber(context: Context, number: String) {
        prefs(context).edit { putStringSet(KEY_VIP, HashSet(getVipNumbers(context) - number)) }
    }

    /** Compares with the phone's own number matching, so "+1 415 555 0100" equals "4155550100". */
    fun isVip(context: Context, number: String): Boolean {
        if (number.isBlank()) return false
        return getVipNumbers(context).any {
            PhoneNumberCompat.numbersMatch(it, number)
        }
    }

    // ---- Announcer ------------------------------------------------------------

    fun isAnnouncerEnabled(context: Context) = prefs(context).getBoolean(KEY_ANN_ENABLED, false)
    fun setAnnouncerEnabled(context: Context, v: Boolean) = prefs(context).edit { putBoolean(KEY_ANN_ENABLED, v) }

    fun getAnnouncerTemplate(context: Context): String =
        prefs(context).getString(KEY_ANN_TEMPLATE, AnnouncerScript.DEFAULT_TEMPLATE) ?: AnnouncerScript.DEFAULT_TEMPLATE
    fun setAnnouncerTemplate(context: Context, v: String) = prefs(context).edit { putString(KEY_ANN_TEMPLATE, v) }

    fun getAnnouncerUserName(context: Context): String = prefs(context).getString(KEY_ANN_USER, "") ?: ""
    fun setAnnouncerUserName(context: Context, v: String) =
        prefs(context).edit { putString(KEY_ANN_USER, v.take(AnnouncerScript.MAX_USER_NAME_LENGTH)) }

    fun getAnnouncerRepeat(context: Context): Int = prefs(context).getInt(KEY_ANN_REPEAT, 1).coerceIn(1, 3)
    fun setAnnouncerRepeat(context: Context, v: Int) = prefs(context).edit { putInt(KEY_ANN_REPEAT, v.coerceIn(1, 3)) }

    fun getAnnouncerSpeedPercent(context: Context): Int = prefs(context).getInt(KEY_ANN_SPEED, 100).coerceIn(50, 150)
    fun setAnnouncerSpeedPercent(context: Context, v: Int) = prefs(context).edit { putInt(KEY_ANN_SPEED, v.coerceIn(50, 150)) }

    fun getAnnouncerPitchPercent(context: Context): Int = prefs(context).getInt(KEY_ANN_PITCH, 100).coerceIn(50, 150)
    fun setAnnouncerPitchPercent(context: Context, v: Int) = prefs(context).edit { putInt(KEY_ANN_PITCH, v.coerceIn(50, 150)) }

    fun getAnnouncerVolumePercent(context: Context): Int = prefs(context).getInt(KEY_ANN_VOLUME, 70).coerceIn(10, 100)
    fun setAnnouncerVolumePercent(context: Context, v: Int) = prefs(context).edit { putInt(KEY_ANN_VOLUME, v.coerceIn(10, 100)) }

    fun isAnnouncerHeadphonesOnly(context: Context) = prefs(context).getBoolean(KEY_ANN_HEADPHONES, false)
    fun setAnnouncerHeadphonesOnly(context: Context, v: Boolean) = prefs(context).edit { putBoolean(KEY_ANN_HEADPHONES, v) }

    fun isAnnounceUnknown(context: Context) = prefs(context).getBoolean(KEY_ANN_UNKNOWN, true)
    fun setAnnounceUnknown(context: Context, v: Boolean) = prefs(context).edit { putBoolean(KEY_ANN_UNKNOWN, v) }

    /** Requested announcement voice. The manager prefers an installed gendered voice, else shifts pitch. */
    enum class AnnouncerVoice { DEFAULT, MALE, FEMALE }

    fun getAnnouncerVoice(context: Context): AnnouncerVoice =
        runCatching {
            AnnouncerVoice.valueOf(
                prefs(context).getString(KEY_ANN_VOICE, AnnouncerVoice.DEFAULT.name)
                    ?: AnnouncerVoice.DEFAULT.name
            )
        }.getOrDefault(AnnouncerVoice.DEFAULT)

    fun setAnnouncerVoice(context: Context, v: AnnouncerVoice) =
        prefs(context).edit { putString(KEY_ANN_VOICE, v.name) }
}
