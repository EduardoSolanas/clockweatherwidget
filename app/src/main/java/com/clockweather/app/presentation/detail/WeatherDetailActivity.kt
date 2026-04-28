package com.clockweather.app.presentation.detail

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.clockweather.app.R
import com.clockweather.app.presentation.detail.screen.WeatherDetailScreen
import com.clockweather.app.presentation.detail.theme.WeatherDetailTheme
import com.clockweather.app.presentation.settings.SettingsActivity
import dagger.hilt.android.AndroidEntryPoint

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
        showFirstInstallDialogIfNeeded()
    }

    private fun showFirstInstallDialogIfNeeded() {
        val prefs = getSharedPreferences(FIRST_INSTALL_PREFS, MODE_PRIVATE)
        if (prefs.getBoolean(KEY_FIRST_INSTALL_DIALOG_SHOWN, false)) return

        prefs.edit().putBoolean(KEY_FIRST_INSTALL_DIALOG_SHOWN, true).apply()
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.first_install_dialog_title))
            .setMessage(getString(R.string.first_install_dialog_message))
            .setCancelable(false)
            .setPositiveButton(getString(R.string.first_install_dialog_continue)) { dialog, _ ->
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.first_install_dialog_open_settings)) { dialog, _ ->
                startActivity(Intent(this, SettingsActivity::class.java))
                dialog.dismiss()
            }
            .show()
    }

    // NOTE: Activity → home clock sync is handled globally by
    // ClockWeatherApplication.ActivityLifecycleCallbacks.onActivityStopped()
    // and ProcessLifecycleOwner.onStop(). No per-activity override needed.

    companion object {
        private const val FIRST_INSTALL_PREFS = "first_install_prefs"
        private const val KEY_FIRST_INSTALL_DIALOG_SHOWN = "first_install_dialog_shown"
        const val EXTRA_WIDGET_ID = "extra_widget_id"
    }
}
