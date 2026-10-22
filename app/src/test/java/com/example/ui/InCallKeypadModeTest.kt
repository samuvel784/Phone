/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.model.Contact
import com.example.model.LabeledNumber
import com.example.ui.components.InCallKeypad
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The in-call keypad must have two visibly separate modes: DTMF tones for the current call and an
 * Add Call contact search that never emits tones and only places a call after confirmation.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class InCallKeypadModeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val alice = Contact(
        id = 1L,
        number = "5550100",
        name = "Alice",
        label = "Mobile",
        avatarText = "A",
        avatarBgValue = 0L,
        avatarTextColorValue = 0L,
        numbers = listOf(LabeledNumber("5550100", "Mobile", true))
    )

    @Test
    fun keypadModeSendsDtmf() {
        val tones = mutableListOf<Char>()
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = false) {
                InCallKeypad(
                    onClose = {},
                    isAddCallEnabled = true,
                    contacts = listOf(alice),
                    onDtmf = { tones += it }
                )
            }
        }

        composeTestRule.onNodeWithTag("in_call_key_2").performClick()
        composeTestRule.waitForIdle()

        assertEquals(listOf('2'), tones)
    }

    @Test
    fun addCallModeNeverSendsDtmf() {
        val tones = mutableListOf<Char>()
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = false) {
                InCallKeypad(
                    onClose = {},
                    isAddCallEnabled = true,
                    contacts = listOf(alice),
                    onDtmf = { tones += it }
                )
            }
        }

        composeTestRule.onNodeWithTag("in_call_keypad_mode_add_call").performClick()
        composeTestRule.onNodeWithTag("in_call_key_2").performClick()
        composeTestRule.waitForIdle()

        assertTrue(tones.isEmpty())
    }

    @Test
    fun searchResultIsClickableAndConfirmsExactlyOnce() {
        val tones = mutableListOf<Char>()
        val addCalls = mutableListOf<Pair<String, String>>()
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = false) {
                InCallKeypad(
                    onClose = {},
                    isAddCallEnabled = true,
                    contacts = listOf(alice),
                    onDtmf = { tones += it },
                    onAddCall = { name, number ->
                        addCalls += name to number
                        true
                    }
                )
            }
        }

        composeTestRule.onNodeWithTag("in_call_keypad_mode_add_call").performClick()
        composeTestRule.onNodeWithTag("in_call_add_call_search").performTextInput("Alice")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("in_call_add_call_result_5550100").performClick()
        composeTestRule.onNodeWithTag("in_call_add_call_confirm").performClick()
        composeTestRule.waitForIdle()

        assertTrue(tones.isEmpty())
        assertEquals(listOf("Alice" to "5550100"), addCalls)
    }

    @Test
    fun restrictedCallOnlyKeypadHidesContactSearch() {
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = false) {
                InCallKeypad(
                    onClose = {},
                    isAddCallEnabled = false,
                    contacts = emptyList()
                )
            }
        }

        composeTestRule.onAllNodesWithTag("in_call_keypad_mode_add_call").assertCountEquals(0)
        composeTestRule.onNodeWithTag("in_call_keypad_mode_dtmf").assertIsDisplayed()
    }
}
