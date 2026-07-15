package com.clockweather.app.worker

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ConfiguredWeatherRefreshIntervalContractTest {

    @Test
    fun `automatic worker freshness uses configured refresh interval`() {
        val source = File("src/main/java/com/clockweather/app/worker/WeatherUpdateWorker.kt").readText()

        assertTrue(source.contains("prefs[SettingsViewModel.KEY_WEATHER_REFRESH_INTERVAL]"))
        assertTrue(source.contains("SettingsViewModel.normalizeWeatherRefreshInterval("))
        assertTrue(
            Regex(
                """ensureFreshWeatherData\(\s*refreshLocation,\s*forecastDays,\s*maxAgeMinutes\s*=\s*refreshIntervalMinutes\.toLong\(\)"""
            ).containsMatchIn(source)
        )
    }

    @Test
    fun `automatic widget freshness uses configured refresh interval`() {
        val source = File(
            "src/main/java/com/clockweather/app/presentation/widget/common/BaseWidgetUpdater.kt"
        ).readText()

        assertTrue(source.contains("prefs[SettingsViewModel.KEY_WEATHER_REFRESH_INTERVAL]"))
        assertTrue(source.contains("SettingsViewModel.normalizeWeatherRefreshInterval("))
        assertTrue(
            Regex(
                """isWeatherDataFresh\(\s*weather,\s*referenceDateTime,\s*requiredForecastDays,\s*maxAgeMinutes\s*=\s*refreshIntervalMinutes\.toLong\(\)"""
            ).containsMatchIn(source)
        )
    }
}
