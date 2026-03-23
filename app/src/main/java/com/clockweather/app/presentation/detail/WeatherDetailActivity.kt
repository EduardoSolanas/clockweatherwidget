package com.clockweather.app.presentation.detail

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.clockweather.app.ClockWeatherApplication
import com.clockweather.app.presentation.detail.screen.WeatherDetailScreen
import com.clockweather.app.presentation.detail.theme.WeatherDetailTheme
import com.clockweather.app.presentation.settings.SettingsActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class WeatherDetailActivity : AppCompatActivity() {

    private val viewModel: WeatherDetailViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WeatherDetailTheme {
                WeatherDetailScreen(
                    viewModel = viewModel,
                    onNavigateBack = { finish() },
                    onNavigateToSettings = {
                        startActivity(Intent(this, SettingsActivity::class.java))
                    }
                )
            }
        }
    }

    override fun onStop() {
        super.onStop()
        val app = applicationContext as? ClockWeatherApplication ?: return
        lifecycleScope.launch {
            // Use the exact same lockscreen -> home convergence path.
            app.syncClockNow(
                applicationContext,
                suppressAnimation = true,
                reassertAfterReschedule = false
            )
        }
    }

    companion object {
        const val EXTRA_WIDGET_ID = "extra_widget_id"
    }
}
