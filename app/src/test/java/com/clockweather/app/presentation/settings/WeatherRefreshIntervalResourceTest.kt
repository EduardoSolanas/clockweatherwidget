package com.clockweather.app.presentation.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WeatherRefreshIntervalResourceTest {

    @Test
    fun `refresh interval policy normalizes legacy stored values and preserves default`() {
        assertEquals(30, SettingsViewModel.normalizeWeatherRefreshInterval(null))
        assertEquals(15, SettingsViewModel.normalizeWeatherRefreshInterval(5))
        assertEquals(60, SettingsViewModel.normalizeWeatherRefreshInterval(60))
        assertEquals(1440, SettingsViewModel.normalizeWeatherRefreshInterval(2000))
    }

    @Test
    fun `all settings translations advertise the 15 minute scheduler minimum`() {
        val resDir = File("src/main/res")
        val stringFiles = resDir.listFiles { file ->
            file.isDirectory && (file.name == "values" || file.name.startsWith("values-"))
        }.orEmpty().map { valuesDir -> File(valuesDir, "strings.xml") }
            .filter(File::isFile)

        assertTrue("No strings.xml resources found under ${resDir.absolutePath}", stringFiles.isNotEmpty())

        stringFiles.forEach { file ->
            val refreshStrings = file.readLines().filter { line ->
                line.contains("settings_weather_refresh_interval_desc") ||
                    line.contains("settings_weather_refresh_minutes")
            }
            if (refreshStrings.isNotEmpty()) {
                assertTrue("${file.path}: refresh interval strings must advertise 15–1440", refreshStrings.all { "15–1440" in it })
                assertFalse("${file.path}: refresh interval strings must not advertise 5–1440", refreshStrings.any { "(5–1440)" in it })
            }
        }
    }
}
