/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.telecom

import org.junit.Assert.assertEquals
import org.junit.Test

class CallProtectionPolicyTest {
    private fun silenced(
        saved: Boolean = false,
        vip: Boolean = false,
        favorite: Boolean = false,
        repeat: Boolean = false,
        focus: FocusMode = FocusMode.OFF,
        protection: ProtectionLevel = ProtectionLevel.STANDARD,
        emergency: Boolean = false,
        number: String = "+14155550100"
    ) = CallProtectionPolicy.evaluateCall(
        phoneNumber = number,
        callerName = null,
        isSavedContact = saved,
        isVip = vip,
        isFavorite = favorite,
        isRepeatCaller = repeat,
        focusMode = focus,
        protectionLevel = protection,
        isEmergencyNumber = emergency
    ).shouldSilence

    @Test fun standardAndOffRingsEveryone() = assertEquals(false, silenced())

    @Test fun emergencyIsNeverSilenced() {
        assertEquals(false, silenced(emergency = true, focus = FocusMode.SILENCE_ALL, protection = ProtectionLevel.MAXIMUM))
        assertEquals(false, silenced(number = "112", focus = FocusMode.SILENCE_ALL))
    }

    @Test fun silenceAllKeepsOnlyVipAndFavorites() {
        assertEquals(true, silenced(saved = true, focus = FocusMode.SILENCE_ALL))
        assertEquals(false, silenced(vip = true, focus = FocusMode.SILENCE_ALL))
        assertEquals(false, silenced(favorite = true, focus = FocusMode.SILENCE_ALL))
    }

    @Test fun vipOnlySilencesEveryoneElse() {
        assertEquals(true, silenced(saved = true, favorite = true, focus = FocusMode.VIP_ONLY))
        assertEquals(false, silenced(vip = true, focus = FocusMode.VIP_ONLY))
    }

    @Test fun savedContactsOnlySilencesUnknownNumbers() {
        assertEquals(true, silenced(focus = FocusMode.SAVED_CONTACTS_ONLY))
        assertEquals(false, silenced(saved = true, focus = FocusMode.SAVED_CONTACTS_ONLY))
    }

    @Test fun strictSilencesUnknownButLetsRepeatCallersThrough() {
        assertEquals(true, silenced(protection = ProtectionLevel.STRICT))
        assertEquals(false, silenced(saved = true, protection = ProtectionLevel.STRICT))
        assertEquals(false, silenced(repeat = true, protection = ProtectionLevel.STRICT))
    }

    @Test fun maximumLetsOnlyVipFavoriteAndRepeatCallersThrough() {
        assertEquals(true, silenced(saved = true, protection = ProtectionLevel.MAXIMUM))
        assertEquals(false, silenced(vip = true, protection = ProtectionLevel.MAXIMUM))
        assertEquals(false, silenced(favorite = true, protection = ProtectionLevel.MAXIMUM))
        assertEquals(false, silenced(repeat = true, protection = ProtectionLevel.MAXIMUM))
    }
}
