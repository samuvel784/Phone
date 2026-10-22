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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.example.ui.components.AppLockedScreen
import com.example.ui.components.RestrictedCallSurface
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class RestrictedCallSurfaceTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun restrictedCallSurfaceShowsOnlyFallbackIdentityAndUnlock() {
        var unlocked = false
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = false) {
                RestrictedCallSurface(
                    onUnlock = { unlocked = true },
                    onExit = {},
                    fallbackName = "Instrumentation Caller",
                    fallbackNumber = "+15550100"
                )
            }
        }

        composeTestRule.onNodeWithText("Instrumentation Caller").assertIsDisplayed()
        composeTestRule.onNodeWithText("+15550100").assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.btn_unlock_app))
            .assertIsDisplayed()
            .performClick()
        assertTrue(unlocked)

        assertNoFullAppDestinations()
    }

    @Test
    fun appLockedScreenShowsNoContactsRecentsOrSettings() {
        var unlocked = false
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = false) {
                AppLockedScreen(onUnlock = { unlocked = true })
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.app_locked_title))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.btn_unlock_app))
            .assertIsDisplayed()
            .performClick()
        assertTrue(unlocked)

        assertNoFullAppDestinations()
    }

    private fun assertNoFullAppDestinations() {
        listOf(
            R.string.tab_contacts,
            R.string.tab_recents,
            R.string.tab_favorites,
            R.string.tab_dialpad,
            R.string.settings_title
        ).forEach { stringRes ->
            composeTestRule.onAllNodesWithText(context.getString(stringRes))
                .assertCountEquals(0)
        }
    }
}
