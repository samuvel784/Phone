/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.telecom

import android.telecom.Call
import com.example.util.PhoneNumberNormalizer

/**
 * Decides whether an asynchronously resolved caller name may still be applied.
 *
 * Contact lookups finish on a background thread. By the time one returns, the call it was
 * started for may have ended and another call may own the on-screen identity. Applying the
 * late result then shows the previous caller's name on the new call (or on the notification),
 * so a result is only accepted for the same, still-live call and the same number.
 */
object CallerIdentityGuard {

    fun canApplyResolvedName(
        resolvedCallId: Long,
        currentCallId: Long?,
        currentCallState: Int,
        resolvedNumber: String = "",
        currentNumber: String = ""
    ): Boolean {
        if (currentCallId == null) return false
        if (resolvedCallId != currentCallId) return false
        if (currentCallState == Call.STATE_DISCONNECTED) return false
        if (resolvedNumber.isNotBlank() && currentNumber.isNotBlank() &&
            !PhoneNumberNormalizer.sameNumber(resolvedNumber, currentNumber)
        ) {
            return false
        }
        return true
    }
}
