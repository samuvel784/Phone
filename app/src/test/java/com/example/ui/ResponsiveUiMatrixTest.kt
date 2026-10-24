/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.ui

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.example.ui.components.BottomNavBar
import com.example.ui.components.DialpadOverlay
import com.example.ui.components.InCallControlGrid
import com.example.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the highest-risk surfaces at the real window sizes a Play Store device matrix covers.
 * The assertions are deliberately about reachability: a control that exists off-screen in a
 * scroll container passes, a control that is clipped or missing fails.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ResponsiveUiMatrixTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun setContentAtLargeFont(content: @Composable () -> Unit) {
        composeTestRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = base.density, fontScale = 2f)
            ) {
                MyApplicationTheme { content() }
            }
        }
    }

    @Test
    @Config(qualifiers = "w320dp-h480dp")
    fun bottomNav_smallPhoneLargeFont_keepsEveryTabLabelVisible() {
        setContentAtLargeFont {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                BottomNavBar(
                    selectedTab = 0,
                    onTabSelected = {},
                    tabSlots = listOf("HOME", "RECENTS", "DIALPAD", "CONTACTS", "FAVORITES")
                )
            }
        }

        listOf(
            R.string.tab_home,
            R.string.tab_recents,
            R.string.tab_dialpad,
            R.string.tab_contacts,
            R.string.tab_favorites
        ).forEach { res ->
            composeTestRule.onNodeWithText(context.getString(res)).assertIsDisplayed()
        }
    }

    @Test
    @Config(qualifiers = "w320dp-h480dp")
    fun dialpad_smallPhoneLargeFont_everyKeyAndCallActionReachable() {
        setContentAtLargeFont {
            DialpadOverlay(
                inputValue = "",
                onValueChange = {},
                onClose = {},
                onCallClick = {},
                onSpeedDialCall = {},
                speedDialMap = emptyMap(),
                voicemailNumber = ""
            )
        }

        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#").forEach { key ->
            composeTestRule.onNodeWithTag("dialpad_key_$key")
                .performScrollTo()
                .assertIsDisplayed()
        }
        composeTestRule.onNodeWithTag("dialpad_call_button")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w640dp-h360dp")
    fun dialpad_landscapeEveryKeyAndCallActionReachable() {
        composeTestRule.setContent {
            MyApplicationTheme {
                DialpadOverlay(
                    inputValue = "12345",
                    onValueChange = {},
                    onClose = {},
                    onCallClick = {},
                    onSpeedDialCall = {},
                    speedDialMap = emptyMap(),
                    voicemailNumber = ""
                )
            }
        }

        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#").forEach { key ->
            composeTestRule.onNodeWithTag("dialpad_key_$key")
                .performScrollTo()
                .assertIsDisplayed()
        }
        composeTestRule.onNodeWithTag("dialpad_call_button")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun dialpad_tablet_isWidthCappedAndUsable() {
        composeTestRule.setContent {
            MyApplicationTheme {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    DialpadOverlay(
                        inputValue = "",
                        onValueChange = {},
                        onClose = {},
                        onCallClick = {},
                        onSpeedDialCall = {},
                        speedDialMap = emptyMap(),
                        voicemailNumber = ""
                    )
                }
            }
        }

        val sheetWidthPx = composeTestRule.onNodeWithTag("dialpad_sheet").fetchSemanticsNode().boundsInRoot.width
        val maxWidthPx = 480.dp.value * context.resources.displayMetrics.density
        org.junit.Assert.assertTrue(
            "dialpad sheet width ${sheetWidthPx}px exceeds $maxWidthPx px",
            sheetWidthPx <= maxWidthPx + 1f
        )
        composeTestRule.onNodeWithTag("dialpad_key_5").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dialpad_call_button")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w640dp-h360dp")
    fun inCallControls_landscape_allCoreActionsVisible() {
        composeTestRule.setContent {
            MyApplicationTheme {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    InCallControlGrid(
                        isInCallDialpadOpen = false,
                        onToggleDialpad = {},
                        isMuted = false,
                        onToggleMute = {},
                        isSpeakerOn = false,
                        onToggleSpeaker = {},
                        isOnHold = false,
                        onToggleHold = {},
                        isBluetoothOn = false,
                        onToggleBluetooth = {},
                        isAddCallDialogOpen = false,
                        onOpenAddCallDialog = {}
                    )
                }
            }
        }

        listOf(
            R.string.keypad,
            R.string.mute,
            R.string.speaker,
            R.string.hold,
            R.string.bluetooth,
            R.string.add_call
        ).forEach { res ->
            composeTestRule.onNodeWithText(context.getString(res)).assertIsDisplayed()
        }
    }

    @Test
    @Config(qualifiers = "w800dp-h1280dp")
    fun inCallControls_tabletPortrait_allCoreActionsVisibleAtLargeFont() {
        setContentAtLargeFont {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                InCallControlGrid(
                    isInCallDialpadOpen = false,
                    onToggleDialpad = {},
                    isMuted = false,
                    onToggleMute = {},
                    isSpeakerOn = false,
                    onToggleSpeaker = {},
                    isOnHold = false,
                    onToggleHold = {},
                    isBluetoothOn = false,
                    onToggleBluetooth = {},
                    isAddCallDialogOpen = false,
                    onOpenAddCallDialog = {}
                )
            }
        }

        listOf(
            R.string.keypad,
            R.string.mute,
            R.string.speaker,
            R.string.hold,
            R.string.bluetooth,
            R.string.add_call
        ).forEach { res ->
            composeTestRule.onNodeWithText(context.getString(res)).assertIsDisplayed()
        }
    }
}
