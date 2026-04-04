package com.clockweather.app.presentation.widget.common

import com.clockweather.app.R

object WidgetThemeSelector {
    data class ThemeColors(
        val backgroundResId: Int,
        val textColorResId: Int
    )

    fun getTheme(themeName: String?): ThemeColors {
        return if (themeName == "light") {
            ThemeColors(
                backgroundResId = R.drawable.flip_digit_bg_light,
                textColorResId = R.color.flip_digit_text_light
            )
        } else {
            ThemeColors(
                backgroundResId = R.drawable.flip_digit_bg,
                textColorResId = R.color.flip_digit_text_dark
            )
        }
    }
}
