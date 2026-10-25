/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.ui.components

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class HeaderSearchBarTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `settings menu is available and clickable when enabled`() {
        var clicks = 0
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = false) {
                HeaderSearchBar(
                    searchQuery = "",
                    onQueryChange = {},
                    menuVisible = true,
                    onMenuClick = { clicks++ }
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.settings_title))
            .assertIsDisplayed()
            .performClick()
        assertEquals(1, clicks)
        composeTestRule
            .onNodeWithText(context.getString(R.string.search_placeholder))
            .assertIsDisplayed()
    }

    @Test
    fun `settings menu is absent while the search field remains`() {
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = false) {
                HeaderSearchBar(
                    searchQuery = "",
                    onQueryChange = {},
                    menuVisible = false,
                    onMenuClick = {}
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.settings_title))
            .assertDoesNotExist()
        composeTestRule
            .onNodeWithText(context.getString(R.string.search_placeholder))
            .assertIsDisplayed()
    }

    @Test
    fun `hiding the menu does not shift the search field`() {
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = false) {
                Column {
                    HeaderSearchBar(
                        searchQuery = "",
                        onQueryChange = {},
                        menuVisible = true,
                        onMenuClick = {}
                    )
                    HeaderSearchBar(
                        searchQuery = "",
                        onQueryChange = {},
                        menuVisible = false,
                        onMenuClick = {}
                    )
                }
            }
        }

        val searchIcons = composeTestRule.onAllNodesWithContentDescription(
            context.getString(R.string.search_placeholder)
        )
        searchIcons.assertCountEquals(2)
        val leftWithMenu = searchIcons[0].fetchSemanticsNode().boundsInRoot.left
        val leftWithoutMenu = searchIcons[1].fetchSemanticsNode().boundsInRoot.left
        assertEquals(leftWithMenu, leftWithoutMenu, 0.5f)
    }
}
