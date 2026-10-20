package com.example

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.ui.components.BulkRecordingExportButton
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BulkRecordingExportTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun tappingBulkExportOnlyShowsWarningAndCancelExportsNothing() {
        var exports = 0
        composeTestRule.setContent {
            MyApplicationTheme {
                BulkRecordingExportButton(onExportAll = { exports++ })
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.recording_export_all))
            .performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.recording_export_warning_title))
            .assertExists()
        composeTestRule.runOnIdle { assertEquals(0, exports) }

        composeTestRule.onNodeWithText(context.getString(R.string.btn_cancel))
            .performClick()
        composeTestRule.runOnIdle { assertEquals(0, exports) }
    }

    @Test
    fun confirmingBulkExportExportsOnlyOnce() {
        var exports = 0
        composeTestRule.setContent {
            MyApplicationTheme {
                BulkRecordingExportButton(onExportAll = { exports++ })
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.recording_export_all))
            .performClick()
        composeTestRule.runOnIdle { assertEquals(0, exports) }

        composeTestRule.onNodeWithText(context.getString(R.string.recording_export_warning_confirm))
            .performClick()
        composeTestRule.runOnIdle { assertEquals(1, exports) }
    }
}
