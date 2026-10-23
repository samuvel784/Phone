/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example

import android.content.Context
import android.provider.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.ui.components.NovaContactsSkeleton
import com.example.ui.components.NovaErrorState
import com.example.ui.components.NovaLoadingOverlay
import com.example.ui.components.NovaOrbitIndicator
import com.example.ui.components.NovaRecentsSkeleton
import com.example.ui.components.NovaRefreshingBar
import com.example.ui.theme.MyApplicationTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Guards the three states the Nova loading system must never blur together:
 * loading, offline/error-with-retry, and a quiet refresh over existing content.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NovaLoadingTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /**
     * Screenshot tests intentionally stop the Compose clock to capture mid-transition frames.
     * Reset it here so loading tests never inherit a frozen clock from another test class.
     */
    @Before
    fun resumeComposeClock() {
        composeTestRule.mainClock.autoAdvance = true
    }

    @Test
    fun orbit_is_exposed_as_a_loading_status() {
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = false) {
                NovaOrbitIndicator()
            }
        }

        composeTestRule.onNodeWithTag("nova_orbit_indicator").assertExists()
    }

    @Test
    fun contacts_and_recents_loading_use_distinct_accessible_skeletons() {
        var showContacts by mutableStateOf(true)
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = false) {
                if (showContacts) {
                    NovaContactsSkeleton()
                } else {
                    NovaRecentsSkeleton()
                }
            }
        }

        composeTestRule.onNodeWithTag("nova_contacts_skeleton").assertExists()
        composeTestRule.onNodeWithTag("nova_recents_skeleton").assertDoesNotExist()

        composeTestRule.runOnIdle { showContacts = false }

        composeTestRule.onNodeWithTag("nova_recents_skeleton").assertExists()
        composeTestRule.onNodeWithTag("nova_contacts_skeleton").assertDoesNotExist()
    }

    @Test
    fun error_state_is_not_a_spinner_and_retry_is_clickable() {
        var retries by mutableIntStateOf(0)
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true) {
                NovaErrorState(onRetry = { retries++ })
            }
        }

        composeTestRule.onNodeWithTag("nova_error_state").assertExists()
        composeTestRule.onNodeWithTag("nova_orbit_indicator").assertDoesNotExist()
        composeTestRule.onNodeWithText("Retry").performClick()

        composeTestRule.runOnIdle {
            assert(retries == 1) { "Retry must call the page callback exactly once" }
        }
    }

    @Test
    fun refreshing_bar_is_visible_over_existing_content() {
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = false) {
                Text("Existing content")
                NovaRefreshingBar()
            }
        }

        composeTestRule.onNodeWithText("Existing content").assertExists()
        composeTestRule.onNodeWithTag("nova_refreshing_bar").assertExists()
    }

    @Test
    fun loading_overlay_explains_long_running_work() {
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true) {
                NovaLoadingOverlay(message = "Exporting backup…")
            }
        }

        composeTestRule.onNodeWithTag("nova_loading_overlay").assertExists()
        composeTestRule.onNodeWithText("Exporting backup…").assertExists()
    }

    @Test
    fun orbit_renders_with_system_animations_disabled() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val resolver = context.contentResolver
        val previous = Settings.Global.getFloat(
            resolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        )
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        try {
            composeTestRule.setContent {
                MyApplicationTheme(darkTheme = false) {
                    NovaOrbitIndicator()
                }
            }
            composeTestRule.onNodeWithTag("nova_orbit_indicator").assertExists()
        } finally {
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, previous)
        }
    }
}
