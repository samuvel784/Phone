/*
 * Copyright (C) 2026 Nova Dialer contributors
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

    /**
     * Returns true when two numbers refer to the same subscriber.
     *
     * Matching is full identity only: the numbers are converted to E.164 with the device region
     * when possible, otherwise their exact digit sequences must match. There is intentionally no
     * suffix/tail comparison, so an unknown number that merely shares its final digits with a
     * saved number is never treated as the same person.
     */
    fun numbersMatch(a: String, b: String): Boolean = PhoneNumberNormalizer.sameNumber(a, b)

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
