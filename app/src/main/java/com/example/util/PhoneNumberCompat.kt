/*
 * Copyright (C) 2026 NovaDial contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.util

import android.content.Context
import android.os.Build
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager

/**
 * Small, dependency-free phone-number helpers.
 *
 * These replace platform methods that recent Android versions deprecated
 * ([PhoneNumberUtils.compare] and [PhoneNumberUtils.isEmergencyNumber]) while
 * keeping support for minSdk 24.
 */
object PhoneNumberCompat {

    private const val MIN_MATCH_DIGITS = 7
    private const val MAX_MATCH_DIGITS = 10

    /**
     * Returns true when two numbers refer to the same subscriber.
     *
     * Only digits are compared, so "+1 415 555 0100" matches "4155550100". When the
     * lengths differ the significant tail (up to 10 digits) is compared, which covers
     * country-code / trunk-prefix differences.
     */
    fun numbersMatch(a: String, b: String): Boolean {
        val da = a.filter { it.isDigit() }
        val db = b.filter { it.isDigit() }
        if (da.isEmpty() || db.isEmpty()) return false
        if (da == db) return true
        if (da.length < MIN_MATCH_DIGITS || db.length < MIN_MATCH_DIGITS) return false
        val tail = minOf(da.length, db.length, MAX_MATCH_DIGITS)
        return da.takeLast(tail) == db.takeLast(tail)
    }

    /**
     * Emergency-number check.
     *
     * Uses [TelephonyManager.isEmergencyNumber] on API 29+ and the (now deprecated)
     * platform helper on older devices. Emergency numbers must never be blocked or
     * silenced, so callers should treat failures as "not emergency".
     */
    fun isEmergencyNumber(context: Context, number: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                (context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager)
                    ?.isEmergencyNumber(number) ?: false
            } else {
                @Suppress("DEPRECATION")
                PhoneNumberUtils.isEmergencyNumber(number)
            }
        } catch (_: Exception) {
            false
        }
    }
}
