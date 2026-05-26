package com.clockweather.app.presentation.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.clockweather.app.presentation.detail.theme.WeatherDetailTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {

    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scrollToBattery = intent.getBooleanExtra(EXTRA_SCROLL_TO_BATTERY, false)
        setContent {
            WeatherDetailTheme {
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateBack = { finish() },
                    scrollToBattery = scrollToBattery
                )
            }
        }
    }

    companion object {
        const val EXTRA_SCROLL_TO_BATTERY = "extra_scroll_to_battery"

        fun buildScrollToBatteryIntent(context: Context): Intent =
            Intent(context, SettingsActivity::class.java).apply {
                putExtra(EXTRA_SCROLL_TO_BATTERY, true)
            }
    }
}

