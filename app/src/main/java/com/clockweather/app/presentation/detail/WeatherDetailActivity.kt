package com.clockweather.app.presentation.detail

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clockweather.app.data.ads.AdManager
import com.clockweather.app.presentation.detail.screen.WeatherDetailScreen
import com.clockweather.app.presentation.detail.theme.WeatherDetailTheme
import com.clockweather.app.presentation.onboarding.OnboardingScreen
import com.clockweather.app.presentation.settings.SettingsActivity
import com.clockweather.app.presentation.settings.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class WeatherDetailActivity : AppCompatActivity() {

    private val viewModel: WeatherDetailViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AdManager.initialize(this)

        val prefs = getSharedPreferences(FIRST_INSTALL_PREFS, MODE_PRIVATE)
        val initialOnboardingCompleted = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)

        setContent {
            WeatherDetailTheme {
                var onboardingCompleted by remember { mutableStateOf(initialOnboardingCompleted) }
                val isTester by settingsViewModel.isTesterMode.collectAsStateWithLifecycle()

                if (!onboardingCompleted) {
                    OnboardingScreen(
                        settingsViewModel = settingsViewModel,
                        onComplete = {
                            prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, true).apply()
                            onboardingCompleted = true
                            viewModel.refresh()
                            AdManager.preloadInterstitial(this@WeatherDetailActivity)
                        }
                    )
                } else {
                    LaunchedEffect(Unit) {
                        AdManager.showInterstitialAd(
                            activity = this@WeatherDetailActivity,
                            isTester = isTester
                        )
                    }

                    WeatherDetailScreen(
                        viewModel = viewModel,
                        onNavigateBack = { finish() },
                        onNavigateToSettings = {
                            startActivity(Intent(this, SettingsActivity::class.java))
                        },
                        onFixBatteryClick = {
                            startActivity(SettingsActivity.buildScrollToBatteryIntent(this))
                        }
                    )
                }
            }
        }
    }

    companion object {
        private const val FIRST_INSTALL_PREFS = "first_install_prefs"
        const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
    }
}
