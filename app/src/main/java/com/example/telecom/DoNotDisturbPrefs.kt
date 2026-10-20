/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.telecom

import android.content.Context
import android.content.SharedPreferences
import java.util.Calendar
import androidx.core.content.edit

/**
 * Do Not Disturb schedule: a repeating daily window during which the call
 * screening service silences calls from numbers the user has not saved.
 *
 * Day bits use Mon=bit0 ... Sun=bit6 so the mask is friendly to store as an Int.
 */
object DoNotDisturbPrefs {
    private const val FILE = "novadial_call_protection"

    private const val KEY_ENABLED = "dnd_enabled"
    private const val KEY_START_MIN = "dnd_start_min"
    private const val KEY_END_MIN = "dnd_end_min"
    private const val KEY_DAYS = "dnd_days"

    /** Bit index for a java.util.Calendar DAY_OF_WEEK value. */
    fun dayBit(calendarDayOfWeek: Int): Int = (calendarDayOfWeek + 5) % 7

    const val MON = 0
    const val TUE = 1
    const val WED = 2
    const val THU = 3
    const val FRI = 4
    const val SAT = 5
    const val SUN = 6

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun getStartMin(context: Context): Int = prefs(context).getInt(KEY_START_MIN, 22 * 60)

    fun getEndMin(context: Context): Int = prefs(context).getInt(KEY_END_MIN, 7 * 60)

    /** Day-of-week mask, Mon=bit0 ... Sun=bit6. Defaults to every day. */
    fun getDays(context: Context): Int = prefs(context).getInt(KEY_DAYS, 0b1111111)

    fun setAll(context: Context, enabled: Boolean, startMin: Int, endMin: Int, days: Int) {
        prefs(context).edit {
            putBoolean(KEY_ENABLED, enabled)
            putInt(KEY_START_MIN, startMin)
            putInt(KEY_END_MIN, endMin)
            putInt(KEY_DAYS, days)
        }
    }

    /**
     * Pure schedule check. `dayBit` uses the Mon=0 mapping; `minute` is minutes
     * since midnight. Ranges may wrap past midnight (start > end).
     */
    fun isQuietHours(minute: Int, dayBit: Int, startMin: Int, endMin: Int, days: Int): Boolean {
        if (days == 0) return false
        if ((days and (1 shl dayBit)) == 0) return false
        return if (startMin == endMin) {
            true // start == end means the whole day by convention
        } else if (startMin < endMin) {
            minute in startMin until endMin
        } else {
            minute !in endMin until startMin
        }
    }

    fun isQuietHoursNow(context: Context): Boolean {
        if (!isEnabled(context)) return false
        val now = Calendar.getInstance()
        val minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        return isQuietHours(minute, dayBit(now.get(Calendar.DAY_OF_WEEK)), getStartMin(context), getEndMin(context), getDays(context))
    }
}
