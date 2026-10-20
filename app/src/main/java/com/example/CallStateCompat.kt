/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example

import android.os.Build
import android.telecom.Call

/**
 * Reads a [Call]'s state without the deprecated [Call.getState] call.
 *
 * [Call.getState] was deprecated in API 31 in favour of [Call.Details.getState],
 * which itself only exists from API 31. This accessor keeps minSdk 24 working while
 * staying warning-free on newer SDKs.
 */
@Suppress("DEPRECATION")
val Call.safeState: Int
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        details?.state ?: Call.STATE_DISCONNECTED
    } else {
        state
    }
