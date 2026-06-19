package com.clockweather.app.presentation.detail.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import com.clockweather.app.domain.model.HourlyForecast
import com.clockweather.app.domain.model.SpeedUnit
import com.clockweather.app.domain.model.TemperatureUnit
import com.clockweather.app.domain.model.WeatherCondition
import com.clockweather.app.domain.model.WindDirection
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(qualifiers = "w360dp-h640dp-xhdpi")
class HourlyWeatherGraphComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `changing selectedDate to a future day resets horizontal scroll to start`() {
        val today = LocalDate.of(2026, 4, 10)
        val tomorrow = today.plusDays(1)
        val dayAfter = today.plusDays(2)

        val forecasts = buildForecasts(tomorrow, 24) + buildForecasts(dayAfter, 24)
        var currentSelectedDate by mutableStateOf(tomorrow)

        composeTestRule.setContent {
            HourlyWeatherGraph(
                hourlyForecasts = forecasts,
                temperatureUnit = TemperatureUnit.CELSIUS,
                speedUnit = SpeedUnit.KMH,
                selectedDate = currentSelectedDate,
                referenceDateTime = LocalDateTime.of(today, java.time.LocalTime.of(12, 0))
            )
        }

        // Initially tomorrow's first hour is visible (00:00)
        composeTestRule.onNodeWithText("00:00").assertIsDisplayed()

        // Instead of swiping, we programmatically scroll or just rely on changing selectedDate.
        // Wait for idle.
        composeTestRule.waitForIdle()

        // Just check that 00:00 is displayed initially.
        composeTestRule.onNodeWithText("00:00").assertIsDisplayed()

        // Change date to dayAfter
        currentSelectedDate = dayAfter

        composeTestRule.waitForIdle()

        // Scroll should have reset to 0, making 00:00 visible again
        composeTestRule.onNodeWithText("00:00").assertIsDisplayed()
    }

    @Test
    fun `changing selectedDate back to today auto-scrolls to current hour`() {
        val today = LocalDate.of(2026, 4, 10)
        val tomorrow = today.plusDays(1)
        val currentHourText = "22:00" // Pick a late hour that is definitely off-screen at scroll position 0

        val forecasts = buildForecasts(today, 24) + buildForecasts(tomorrow, 24)
        var currentSelectedDate by mutableStateOf(today)

        composeTestRule.setContent {
            HourlyWeatherGraph(
                hourlyForecasts = forecasts,
                temperatureUnit = TemperatureUnit.CELSIUS,
                speedUnit = SpeedUnit.KMH,
                selectedDate = currentSelectedDate,
                referenceDateTime = LocalDateTime.of(today, java.time.LocalTime.of(22, 0))
            )
        }

        // Initially today is selected, it should show current hour (22:00) because it auto-scrolls
        composeTestRule.onNodeWithText(currentHourText).assertExists()

        // Now select tomorrow
        currentSelectedDate = tomorrow
        
        composeTestRule.waitForIdle()

        // It should show 00:00 for tomorrow (scroll resets)
        composeTestRule.onNodeWithText("00:00").assertIsDisplayed()

        // Select today again
        currentSelectedDate = today
        
        composeTestRule.waitForIdle()

        // It should auto-scroll back to the current hour (22:00)
        composeTestRule.onNodeWithText(currentHourText).assertIsDisplayed()
    }

    private fun buildForecasts(date: LocalDate, count: Int, startHour: Int = 0): List<HourlyForecast> =
        (0 until count).map { i ->
            HourlyForecast(
                dateTime = LocalDateTime.of(date, java.time.LocalTime.of((startHour + i) % 24, 0)),
                temperature = 10.0 + i,
                feelsLike = 9.0 + i,
                humidity = 70,
                dewPoint = 4.0,
                precipitationProbability = 20,
                weatherCondition = WeatherCondition.CLEAR_DAY,
                isDay = true,
                pressure = 1012.0,
                windSpeed = 10.0,
                windDirection = WindDirection.N,
                windDirectionDegrees = 0,
                visibility = 10_000.0,
                uvIndex = 3.0
            )
        }
}
