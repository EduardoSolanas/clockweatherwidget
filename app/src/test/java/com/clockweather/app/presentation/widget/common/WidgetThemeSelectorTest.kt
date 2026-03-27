package com.clockweather.app.presentation.widget.common

import android.graphics.Color
import com.clockweather.app.R
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetThemeSelectorTest {

    @Test
    fun `dark theme key uses light tile style`() {
        val theme = WidgetThemeSelector.getTheme("dark")
        assertEquals(R.drawable.flip_digit_bg_light, theme.backgroundResId)
        assertEquals(Color.BLACK, theme.textColor)
    }

    @Test
    fun `light theme key falls back to dark tile style`() {
        val theme = WidgetThemeSelector.getTheme("light")
        assertEquals(R.drawable.flip_digit_bg, theme.backgroundResId)
        assertEquals(Color.WHITE, theme.textColor)
    }

    @Test
    fun `null theme falls back to dark tile style`() {
        val theme = WidgetThemeSelector.getTheme(null)
        assertEquals(R.drawable.flip_digit_bg, theme.backgroundResId)
        assertEquals(Color.WHITE, theme.textColor)
    }

    @Test
    fun `empty theme falls back to dark tile style`() {
        val theme = WidgetThemeSelector.getTheme("")
        assertEquals(R.drawable.flip_digit_bg, theme.backgroundResId)
        assertEquals(Color.WHITE, theme.textColor)
    }
}
