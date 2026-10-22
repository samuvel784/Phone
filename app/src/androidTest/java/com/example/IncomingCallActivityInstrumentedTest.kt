/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example

import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.view.WindowManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Contract check for the incoming-call activity's keyguard behavior. This does not simulate a
 * locked physical device, so an actual locked-device call remains NOT RUN.
 *
 * On API 27+ `Activity.setShowWhenLocked`/`setTurnScreenOn` are forwarded to ActivityClient and are
 * not reflected in `Window.getAttributes().flags` (verified against the API 37 framework source).
 * The manifest attributes are therefore checked from the installed package, while the legacy
 * window flags are asserted only where this app actually uses them (API < 27).
 */
@RunWith(AndroidJUnit4::class)
class IncomingCallActivityInstrumentedTest {

    @Test
    fun incomingCallActivityShowsWhenLockedButDoesNotDismissTheKeyguard() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext

        // ActivityInfo.FLAG_SHOW_WHEN_LOCKED and FLAG_TURN_SCREEN_ON are hidden framework
        // constants, but these are their stable manifest flag values.
        val activityInfo = context.packageManager.getActivityInfo(
            ComponentName(context, IncomingCallActivity::class.java),
            0
        )
        assertTrue(
            "The installed manifest must mark IncomingCallActivity showWhenLocked",
            (activityInfo.flags and 0x800000) != 0
        )
        assertTrue(
            "The installed manifest must mark IncomingCallActivity turnScreenOn",
            (activityInfo.flags and 0x1000000) != 0
        )

        val intent = Intent(context, IncomingCallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(IncomingCallActivity.EXTRA_CALLER_NAME, "Instrumentation Caller")
            putExtra(IncomingCallActivity.EXTRA_CALLER_NUMBER, "+15550100")
        }

        val activity = instrumentation.startActivitySync(intent)
        assertNotNull(activity)
        try {
            val flags = activity.window.attributes.flags
            assertEquals(
                "IncomingCallActivity must not request keyguard dismissal",
                0,
                flags and WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
                assertTrue(
                    "The legacy incoming-call window must be allowed over the keyguard",
                    (flags and WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED) != 0
                )
                assertTrue(
                    "The legacy incoming-call window must turn the screen on",
                    (flags and WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON) != 0
                )
            }
        } finally {
            activity.finish()
        }
    }
}

