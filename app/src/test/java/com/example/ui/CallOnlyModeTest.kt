/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.ui

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.example.ui.components.ActiveCallScreen
import com.example.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The lock-screen call surface reuses the real in-call dashboard, but it must not expose private
 * app destinations or an unlock shortcut.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class CallOnlyModeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun callOnlyModeKeepsAnswerAndDeclineButHidesPrivateDestinations() {
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = false) {
                ActiveCallScreen(
                    contactName = "Instrumentation Caller",
                    contactNumber = "+15550100",
                    preferredSim = "SIM 1",
                    quickResponses = listOf("Can't talk now"),
                    onHangUp = {},
                    onQuickDecline = {},
                    isIncoming = true,
                    isFake = true,
                    fakeState = "RINGING",
                    callOnlyMode = true
                )
            }
        }

        composeTestRule.onNodeWithTag("answer_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("hangup_button").assertIsDisplayed()

        listOf(
            R.string.btn_unlock_app,
            R.string.add_call,
            R.string.quick_responses,
            R.string.send_quick_response,
            R.string.record
        ).forEach { stringRes ->
            composeTestRule.onAllNodesWithText(context.getString(stringRes))
                .assertCountEquals(0)
        }
    }
}
