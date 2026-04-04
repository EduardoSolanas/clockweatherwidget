package com.clockweather.app.presentation.widget.common

import com.clockweather.app.R
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetThemeSelectorTest {

    @Test
    fun `light theme key uses light tile style`() {
        val theme = WidgetThemeSelector.getTheme("light")
        assertEquals(R.drawable.flip_digit_bg_light, theme.backgroundResId)
        assertEquals(R.color.flip_digit_text_light, theme.textColorResId)
    }

    @Test
    fun `dark theme key uses dark tile style`() {
        val theme = WidgetThemeSelector.getTheme("dark")
        assertEquals(R.drawable.flip_digit_bg, theme.backgroundResId)
        assertEquals(R.color.flip_digit_text_dark, theme.textColorResId)
    }

    @Test
    fun `null theme falls back to dark tile style`() {
        val theme = WidgetThemeSelector.getTheme(null)
        assertEquals(R.drawable.flip_digit_bg, theme.backgroundResId)
        assertEquals(R.color.flip_digit_text_dark, theme.textColorResId)
    }

    @Test
    fun `empty theme falls back to dark tile style`() {
        val theme = WidgetThemeSelector.getTheme("")
        assertEquals(R.drawable.flip_digit_bg, theme.backgroundResId)
        assertEquals(R.color.flip_digit_text_dark, theme.textColorResId)
    }
}
