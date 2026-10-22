/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example

import android.content.Context
import android.content.Intent
import com.example.security.IntentGuard

/**
 * Central builder for every intent that presents the call-only activity.
 *
 * Keeping the target, exact call id, answer flag and guard token in one place makes it harder for
 * a notification path to fall back to MainActivity or to answer the wrong call.
 */
object CallOnlyIntents {

    fun callScreen(
        context: Context,
        callId: Long,
        callerName: String = "",
        callerNumber: String = "",
        guardToken: String? = null,
        action: String = MyInCallService.ACTION_INCOMING_CALL,
        answer: Boolean = false
    ): Intent = Intent(context, IncomingCallActivity::class.java).apply {
        this.action = action
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        putExtra(MyInCallService.EXTRA_SHOW_CALL, true)
        putExtra(MyInCallService.EXTRA_CALL_ID, callId)
        if (answer) putExtra(MyInCallService.EXTRA_ANSWER, true)
        putExtra(IncomingCallActivity.EXTRA_CALLER_NAME, callerName)
        putExtra(IncomingCallActivity.EXTRA_CALLER_NUMBER, callerNumber)
        if (guardToken != null) {
            putExtra(IntentGuard.EXTRA_GUARD_TOKEN, guardToken)
        }
    }
}
