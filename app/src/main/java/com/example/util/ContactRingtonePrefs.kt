/*
 * Copyright (C) 2026 Nova Dialer contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.example.util

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import androidx.core.content.edit

/**
 * Stores per-number ringtone + vibration pattern assignments as a JSON map in
 * SharedPreferences. The pattern names match the strings in
 * `pattern_default` .. `pattern_staccato`.
 */
object ContactRingtonePrefs {
    private const val FILE = "novadial_contact_ringtones"
    private const val KEY_MAP = "contact_ringtone_map"

    const val PATTERN_DEFAULT = "Default"
    const val PATTERN_NONE = "None"

    const val VIBRATION_SHORT = "Short"
    const val VIBRATION_LONG = "Long"
    const val VIBRATION_DOUBLE = "Double"
    const val VIBRATION_HEARTBEAT = "Heartbeat"
    const val VIBRATION_SOS = "SOS"
    const val VIBRATION_STACCATO = "Staccato"

    data class Assignment(val ringtoneUri: String, val pattern: String)

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun getMap(context: Context): Map<String, Assignment> {
        val raw = prefs(context).getString(KEY_MAP, "{}") ?: "{}"
        return try {
            val obj = JSONObject(raw)
            val out = HashMap<String, Assignment>()
            obj.keys().forEach { key ->
                val entry = obj.optJSONObject(key)
                if (entry != null) {
                    out[key] = Assignment(
                        ringtoneUri = entry.optString("r", ""),
                        pattern = entry.optString("p", PATTERN_DEFAULT)
                    )
                }
            }
            out
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun get(context: Context, number: String): Assignment? = getMap(context)[number.trim()]

    /**
     * Looks up an assignment for [number]. Exact keys win; otherwise the shared
     * [PhoneNumberCompat.numbersMatch] rule is used, which compares full digits (or the
     * significant 10-digit tail when both numbers carry a country code). Short suffix-only
     * matches are deliberately not accepted: they can apply one contact's ringtone to a
     * different subscriber.
     */
    fun find(context: Context, number: String): Assignment? {
        val map = getMap(context)
        map[number.trim()]?.let { return it }
        if (number.isBlank()) return null
        return map.entries
            .firstOrNull { numbersMatch(it.key, number) }
            ?.value
    }

    /** Number comparison shared with the rest of the dialer (formatted/country-code aware). */
    fun numbersMatch(a: String, b: String): Boolean = PhoneNumberCompat.numbersMatch(a, b)

    fun set(context: Context, number: String, ringtoneUri: String?, pattern: String) {
        val key = number.trim()
        if (key.isEmpty()) return
        val map = getMap(context).toMutableMap()
        val uri = ringtoneUri?.trim().orEmpty()
        if (uri.isEmpty() && pattern == PATTERN_DEFAULT) {
            map.remove(key)
        } else {
            map[key] = Assignment(uri, pattern)
        }
        prefs(context).edit { putString(KEY_MAP, toJson(map)) }
    }

    fun remove(context: Context, number: String) {
        val key = number.trim()
        if (key.isEmpty()) return
        val map = getMap(context).toMutableMap()
        map.remove(key)
        prefs(context).edit { putString(KEY_MAP, toJson(map)) }
    }

    private fun toJson(map: Map<String, Assignment>): String {
        val obj = JSONObject()
        map.forEach { (num, assignment) ->
            obj.put(num, JSONObject().put("r", assignment.ringtoneUri).put("p", assignment.pattern))
        }
        return obj.toString()
    }

    /** Ordered pattern tings for the Vibrator, or null for "Default" (system behaviour). */
    fun patternTimings(pattern: String): LongArray? = when (pattern) {
        PATTERN_NONE -> longArrayOf(0)
        VIBRATION_SHORT -> longArrayOf(0, 100)
        VIBRATION_LONG -> longArrayOf(0, 600)
        VIBRATION_DOUBLE -> longArrayOf(0, 120, 80, 120)
        VIBRATION_HEARTBEAT -> longArrayOf(0, 400, 120, 400)
        VIBRATION_SOS -> longArrayOf(0, 200, 100, 200, 100, 200, 100, 600)
        VIBRATION_STACCATO -> longArrayOf(0, 60, 40, 60)
        else -> null
    }

    fun patternNames(): List<String> = listOf(
        PATTERN_DEFAULT, PATTERN_NONE, VIBRATION_SHORT, VIBRATION_LONG,
        VIBRATION_DOUBLE, VIBRATION_HEARTBEAT, VIBRATION_SOS, VIBRATION_STACCATO
    )
}
