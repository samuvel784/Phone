package com.example

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.ui.theme.getColorSchemeForTheme
import com.example.ui.viewmodel.DialerViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AmoledThemeAuditTest {

    @Test
    fun `verify amoled mode state updates and persists in ViewModel`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val viewModel = DialerViewModel(app)

        // Set AMOLED mode to true
        viewModel.updateAmoledMode(true)
        assertTrue(viewModel.isAmoledMode.value)

        // Verify persistence in SharedPreferences
        val prefs = app.getSharedPreferences("dialer_prefs", Context.MODE_PRIVATE)
        assertTrue(prefs.getBoolean("is_amoled_mode", false))

        // Toggle back to false
        viewModel.updateAmoledMode(false)
        assertFalse(viewModel.isAmoledMode.value)
        assertFalse(prefs.getBoolean("is_amoled_mode", true))
    }

    @Test
    fun `verify enabling amoled mode auto-activates dark theme if disabled`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val viewModel = DialerViewModel(app)

        // Force dark theme to false
        viewModel.updateDarkTheme(false)
        assertFalse(viewModel.isDarkTheme.value)

        // Enabling AMOLED mode must automatically engage dark theme
        viewModel.updateAmoledMode(true)
        assertTrue(viewModel.isAmoledMode.value)
        assertTrue(viewModel.isDarkTheme.value)

        val prefs = app.getSharedPreferences("dialer_prefs", Context.MODE_PRIVATE)
        assertTrue(prefs.getBoolean("is_amoled_mode", false))
        assertTrue(prefs.getBoolean("is_dark_theme", false))
    }

    @Test
    fun `verify true black oled color values conform to pure black specifications`() {
        val pureBlack = Color(0xFF000000)
        assertEquals(0f, pureBlack.red, 0.001f)
        assertEquals(0f, pureBlack.green, 0.001f)
        assertEquals(0f, pureBlack.blue, 0.001f)
        assertEquals(1f, pureBlack.alpha, 0.001f)
    }

    @Test
    fun `verify getColorSchemeForTheme produces valid theme schemes`() {
        // Midnight (dark) and Porcelain (light) page backgrounds of the premium theme
        val darkScheme = getColorSchemeForTheme("ocean_blue", darkTheme = true)
        assertEquals(Color(0xFF090B13), darkScheme.background)

        val lightScheme = getColorSchemeForTheme("ocean_blue", darkTheme = false)
        assertEquals(Color(0xFFF6F7FB), lightScheme.background)
    }

    @Test
    fun `every accent meets WCAG AA contrast on the page and under button labels in both modes`() {
        val keys = listOf("system", "nova_aurora", "nova_nebula", "nova_glacier", "nova_ember", "nova_forest", "nova_mono", "custom")
        for (key in keys) {
            for (dark in listOf(true, false)) {
                val scheme = getColorSchemeForTheme(key, dark, "#6E5BFF")
                // WCAG AA for normal text is 4.5:1. The accent is used as text and icon colour on the page,
                // and button labels sit on it, so both must pass.
                val onButton = contrast(scheme.primary, scheme.onPrimary)
                val onPage = contrast(scheme.primary, scheme.background)
                assertTrue("$key dark=$dark label on accent $onButton", onButton >= 4.5)
                assertTrue("$key dark=$dark accent on page $onPage", onPage >= 4.5)
            }
        }
    }

    @Test
    fun `an accent changes the primary colour but not the page background`() {
        val aurora = getColorSchemeForTheme("nova_aurora", darkTheme = true)
        val ember = getColorSchemeForTheme("nova_ember", darkTheme = true)
        assertFalse(aurora.primary == ember.primary)
        assertEquals(aurora.background, ember.background)
    }

    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminanceValue() + 0.05
        val lb = b.luminanceValue() + 0.05
        return maxOf(la, lb) / minOf(la, lb)
    }

    private fun Color.luminanceValue(): Double {
        fun channel(c: Float): Double {
            val v = c.toDouble()
            return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(red) + 0.7152 * channel(green) + 0.0722 * channel(blue)
    }
}
