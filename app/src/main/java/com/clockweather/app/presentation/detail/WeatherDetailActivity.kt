package com.clockweather.app.presentation.detail

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.clockweather.app.ClockWeatherApplication
import com.clockweather.app.presentation.detail.screen.WeatherDetailScreen
import com.clockweather.app.presentation.detail.theme.WeatherDetailTheme
import com.clockweather.app.presentation.settings.SettingsActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

    /**
     * When the user leaves (back to home, task switch, etc.) instantly sync
     * the clock widget so it shows the correct time without animation delay,
     * and restart the alarm chain.
     */
    override fun onStop() {
        super.onStop()
        val app = applicationContext as? ClockWeatherApplication ?: return
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            app.syncClockNow(applicationContext)
        }
    }

    companion object {
        const val EXTRA_WIDGET_ID = "extra_widget_id"
    }
}
