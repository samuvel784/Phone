/*
 * Copyright (C) 2026 NovaDial contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.telecom

enum class FocusMode {
    OFF,
    SAVED_CONTACTS_ONLY,
    VIP_ONLY,
    SILENCE_ALL
}

enum class ProtectionLevel {
    STANDARD,
    STRICT,
    MAXIMUM
}

data class CallEvaluationResult(
    val shouldSilence: Boolean,
    val isVip: Boolean,
    val reason: String
)

object CallProtectionPolicy {

    fun evaluateCall(
        phoneNumber: String,
        callerName: String?,
        isSavedContact: Boolean,
        isVip: Boolean,
        isFavorite: Boolean,
        isRepeatCaller: Boolean,
        focusMode: FocusMode,
        protectionLevel: ProtectionLevel,
        isEmergencyNumber: Boolean = false
    ): CallEvaluationResult {
        // 1. Emergency Override (Emergency numbers are NEVER silenced or blocked).
        //    The caller passes what Android reports; the short list is only a fallback.
        val clean = phoneNumber.replace(Regex("[^0-9]"), "")
        val isEmergency = isEmergencyNumber || clean == "911" || clean == "112" || clean == "999" || clean == "108" || clean == "000" || clean == "110" || clean == "119"
        if (isEmergency) {
            return CallEvaluationResult(shouldSilence = false, isVip = true, reason = "Emergency call override")
        }

        // 2. Focus Mode Evaluation
        when (focusMode) {
            FocusMode.SILENCE_ALL -> {
                if (isVip || isFavorite) {
                    return CallEvaluationResult(shouldSilence = false, isVip = isVip, reason = "Allowed by VIP/Favorite override in Focus Mode")
                }
                return CallEvaluationResult(shouldSilence = true, isVip = false, reason = "Focus Mode: Silence All active")
            }
            FocusMode.VIP_ONLY -> {
                if (isVip) {
                    return CallEvaluationResult(shouldSilence = false, isVip = true, reason = "Allowed: VIP contact")
                }
                return CallEvaluationResult(shouldSilence = true, isVip = false, reason = "Focus Mode: VIP Only active")
            }
            FocusMode.SAVED_CONTACTS_ONLY -> {
                if (isSavedContact || isVip || isFavorite) {
                    return CallEvaluationResult(shouldSilence = false, isVip = isVip, reason = "Allowed: Saved contact")
                }
                return CallEvaluationResult(shouldSilence = true, isVip = false, reason = "Focus Mode: Saved contacts only active")
            }
            FocusMode.OFF -> {
                // Proceed to Protection Level evaluation
            }
        }

        // 3. Protection Level Evaluation
        return when (protectionLevel) {
            ProtectionLevel.STANDARD -> {
                CallEvaluationResult(shouldSilence = false, isVip = isVip, reason = "Standard protection: Call allowed")
            }
            ProtectionLevel.STRICT -> {
                if (isSavedContact || isVip || isFavorite || isRepeatCaller) {
                    CallEvaluationResult(shouldSilence = false, isVip = isVip, reason = "Strict protection: Allowed (Saved/VIP/Repeat caller)")
                } else {
                    CallEvaluationResult(shouldSilence = true, isVip = false, reason = "Strict protection: Unsaved number silenced")
                }
            }
            ProtectionLevel.MAXIMUM -> {
                if (isVip || isFavorite || isRepeatCaller) {
                    CallEvaluationResult(shouldSilence = false, isVip = isVip, reason = "Maximum protection: Allowed (VIP/Repeat caller)")
                } else {
                    CallEvaluationResult(shouldSilence = true, isVip = false, reason = "Maximum protection: Non-VIP number silenced")
                }
            }
        }
    }
}
