package com.clockweather.app.presentation.widget.common

import android.graphics.Color
import com.clockweather.app.R

object WidgetThemeSelector {
    data class ThemeColors(
        val backgroundResId: Int,
        val textColor: Int,
        val colonColor: Int
    )

    fun getTheme(themeName: String?): ThemeColors {
        return if (themeName == "dark") {
            ThemeColors(
                backgroundResId = R.drawable.flip_digit_bg_light,
                textColor = Color.BLACK,
                colonColor = 0xCC000000.toInt()
            )
        } else {
            ThemeColors(
                backgroundResId = R.drawable.flip_digit_bg,
                textColor = Color.WHITE,
                colonColor = 0x80FFFFFF.toInt()
            )
        }
    }
}
