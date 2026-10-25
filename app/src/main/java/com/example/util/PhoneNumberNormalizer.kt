/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.util

import android.content.Context
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import java.util.Locale

/**
 * Region-aware phone-number identity.
 *
 * The dialer must never guess that two numbers are the same subscriber just because their
 * digits end in the same sequence. Identity is derived from the E.164 form when the region is
 * known, otherwise from the exact digit sequence. There is deliberately no suffix matching.
 *
 * [android.telephony.PhoneNumberUtils.formatNumberToE164] is part of the platform, so this keeps
 * the app dependency-free while still understanding national trunk prefixes (for example an
 * Indian number written as `09876543210` and as `+919876543210`).
 */
object PhoneNumberNormalizer {

    /** Digits are only accepted as a full subscriber number inside this range. */
    private const val MIN_E164_DIGITS = 7
    private const val MAX_E164_DIGITS = 15

    @Volatile
    var defaultRegion: String = Locale.getDefault().country.orEmpty().uppercase(Locale.ROOT)
        private set

    fun setRegion(regionIso: String?) {
        val cleaned = regionIso.orEmpty().trim().uppercase(Locale.ROOT)
        if (cleaned.length == 2 && cleaned.all { it in 'A'..'Z' }) {
            defaultRegion = cleaned
        }
    }

    /** Restores the region derived from the device locale (used before any SIM-aware init). */
    fun resetToLocale() {
        defaultRegion = Locale.getDefault().country.orEmpty().uppercase(Locale.ROOT)
    }

    /**
     * Region used to interpret national numbers. The SIM country is the most reliable signal;
     * the network and finally the device locale are fallbacks.
     */
    fun regionFor(context: Context): String {
        val candidates = ArrayList<String?>(3)
        try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            if (tm != null) {
                @Suppress("DEPRECATION")
                candidates.add(tm.simCountryIso)
                candidates.add(tm.networkCountryIso)
            }
        } catch (_: Exception) {
        }
        candidates.add(Locale.getDefault().country)
        return candidates.firstOrNull { !it.isNullOrBlank() }
            ?.trim()
            ?.uppercase(Locale.ROOT)
            ?.takeIf { it.length == 2 }
            ?: defaultRegion
    }

    /** E.164 digits (without `+`) when the number can be resolved in [region], else null. */
    fun e164Digits(number: String, region: String = defaultRegion): String? {
        val trimmed = number.trim()
        if (trimmed.isEmpty()) return null
        val digits = trimmed.filter { it.isDigit() }
        if (digits.isEmpty()) return null

        if (trimmed.startsWith("+")) {
            return digits.takeIf { it.length in MIN_E164_DIGITS..MAX_E164_DIGITS }
        }
        if (trimmed.startsWith("00")) {
            val international = digits.drop(2)
            return international.takeIf { it.length in MIN_E164_DIGITS..MAX_E164_DIGITS }
        }

        val effectiveRegion = region.trim().uppercase(Locale.ROOT)
        if (effectiveRegion.length != 2) return null
        return try {
            PhoneNumberUtils.formatNumberToE164(trimmed, effectiveRegion)
                ?.filter { it.isDigit() }
                ?.takeIf { it.length in MIN_E164_DIGITS..MAX_E164_DIGITS }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Stable identity key. E.164 when possible; otherwise the exact digits. Never a suffix.
     */
    fun key(number: String, region: String = defaultRegion): String {
        val e164 = e164Digits(number, region)
        if (!e164.isNullOrBlank()) return e164
        return number.filter { it.isDigit() }
    }

    /** True only when both numbers resolve to the same full subscriber identity. */
    fun sameNumber(a: String, b: String, region: String = defaultRegion): Boolean {
        val keyA = key(a, region)
        val keyB = key(b, region)
        return keyA.isNotEmpty() && keyA == keyB
    }

    /** Candidate strings to try against the Contacts Provider, most specific first. */
    fun lookupVariants(number: String, region: String = defaultRegion): List<String> {
        val raw = number.trim()
        if (raw.isEmpty()) return emptyList()
        val e164 = e164Digits(raw, region)
        val variants = LinkedHashSet<String>(3)
        variants.add(raw)
        if (!e164.isNullOrBlank()) {
            variants.add("+$e164")
            variants.add(e164)
        }
        return variants.toList()
    }
}
