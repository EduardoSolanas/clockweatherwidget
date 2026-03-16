package com.clockweather.app.presentation.widget.common

import android.graphics.Color
import com.clockweather.app.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WidgetThemeSelectorTest {

    @Test
    fun `dark theme returns white cards with black text`() {
        val theme = WidgetThemeSelector.getTheme("dark")
        
        assertEquals(R.drawable.flip_digit_bg_light, theme.backgroundResId)
        assertEquals(Color.BLACK, theme.textColor)
    }

    @Test
    fun `light theme returns black cards with white text`() {
        val theme = WidgetThemeSelector.getTheme("light")
        
        assertEquals(R.drawable.flip_digit_bg, theme.backgroundResId)
        assertEquals(Color.WHITE, theme.textColor)
    }

    @Test
    fun `null or unknown theme defaults to light theme (black cards)`() {
        val theme = WidgetThemeSelector.getTheme(null)
        
        assertEquals(R.drawable.flip_digit_bg, theme.backgroundResId)
        assertEquals(Color.WHITE, theme.textColor)
    }
}
