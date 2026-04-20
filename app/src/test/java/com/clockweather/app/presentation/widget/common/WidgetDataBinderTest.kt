package com.clockweather.app.presentation.widget.common

import android.content.Context
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.clockweather.app.R
import com.clockweather.app.domain.model.CurrentWeather
import com.clockweather.app.domain.model.DailyForecast
import com.clockweather.app.domain.model.Location
import com.clockweather.app.domain.model.TemperatureUnit
import com.clockweather.app.domain.model.WeatherCondition
import com.clockweather.app.domain.model.WeatherData
import com.clockweather.app.domain.model.WindDirection
import com.clockweather.app.util.DateFormatter
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
class WidgetDataBinderTest {

    private lateinit var context: Context
    private lateinit var views: RemoteViews

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0

        context = mockk()
        views = mockk(relaxed = true)

        every { context.getString(WeatherCondition.PARTLY_CLOUDY_DAY.labelResId) } returns "Partly cloudy"
        every { context.getString(R.string.label_today) } returns "Today"
        every { context.getString(R.string.unit_celsius, 11.0) } returns "11°"
        every { context.getString(R.string.unit_celsius, 12.0) } returns "12°"
        every { context.getString(R.string.unit_celsius, 13.0) } returns "13°"
        every { context.getString(R.string.unit_celsius, 14.0) } returns "14°"
        every { context.getString(R.string.unit_celsius, 15.0) } returns "15°"
        every { context.getString(R.string.unit_celsius, 20.0) } returns "20°"
        every { context.getString(R.string.unit_celsius, 21.0) } returns "21°"
        every { context.getString(R.string.unit_celsius, 22.0) } returns "22°"
        every { context.getString(R.string.unit_celsius, 23.0) } returns "23°"
        every { context.getString(R.string.unit_celsius, 24.0) } returns "24°"
        every { context.getString(R.string.unit_celsius, 25.0) } returns "25°"
        every { context.getString(R.string.unit_celsius, 26.0) } returns "26°"
        every { context.getString(R.string.unit_celsius, 27.0) } returns "27°"

        every { views.setTextViewText(any(), any()) } just Runs
        every { views.setImageViewResource(any(), any()) } just Runs
        every { views.setViewVisibility(any(), any()) } just Runs
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `bindSimpleClockViews sets four digits and clears ampm in 24h mode`() {
        WidgetDataBinder.bindSimpleClockViews(views, hour = 10, minute = 26, is24h = true)

        verify(exactly = 1) { views.setTextViewText(R.id.digit_h1, "1") }
        verify(exactly = 1) { views.setTextViewText(R.id.digit_h2, "0") }
        verify(exactly = 1) { views.setTextViewText(R.id.digit_m1, "2") }
        verify(exactly = 1) { views.setTextViewText(R.id.digit_m2, "6") }
        verify(exactly = 1) { views.setTextViewText(R.id.ampm, "") }
    }

    @Test
    fun `bindSimpleClockViews converts midnight for 12h mode`() {
        WidgetDataBinder.bindSimpleClockViews(views, hour = 0, minute = 5, is24h = false)

        verify(exactly = 1) { views.setTextViewText(R.id.digit_h1, "1") }
        verify(exactly = 1) { views.setTextViewText(R.id.digit_h2, "2") }
        verify(exactly = 1) { views.setTextViewText(R.id.digit_m1, "0") }
        verify(exactly = 1) { views.setTextViewText(R.id.digit_m2, "5") }
        verify(exactly = 1) { views.setTextViewText(R.id.ampm, "AM") }
    }

    @Test
    fun `bindWeatherViews populates weather card and makes it visible`() {
        val weatherData = sampleWeatherData()

        WidgetDataBinder.bindWeatherViews(context, views, weatherData, TemperatureUnit.CELSIUS)

        verify(exactly = 1) { views.setTextViewText(R.id.city_name, "London") }
        verify(exactly = 1) { views.setTextViewText(R.id.condition_text, "Partly cloudy") }
        verify(exactly = 1) { views.setImageViewResource(R.id.weather_icon, R.drawable.ic_widget_weather_partly_cloudy_day) }
        verify(exactly = 1) { views.setTextViewText(R.id.current_temp, "17°C") }
        verify(exactly = 1) { views.setTextViewText(R.id.high_low, "20°/11°") }
        verify(exactly = 1) { views.setViewVisibility(R.id.weather_card, View.VISIBLE) }
    }

    @Test
    fun `bindWeatherViews prefers location area over name for city_name`() {
        val weatherData = sampleWeatherData(locationName = "Fitzrovia", locationArea = "London")

        WidgetDataBinder.bindWeatherViews(context, views, weatherData, TemperatureUnit.CELSIUS)

        verify(exactly = 1) { views.setTextViewText(R.id.city_name, "London") }
        verify(exactly = 0) { views.setTextViewText(R.id.city_name, "Fitzrovia") }
    }

    @Test
    fun `bindWeatherViews falls back to location name when area is null`() {
        val weatherData = sampleWeatherData(locationName = "London", locationArea = null)

        WidgetDataBinder.bindWeatherViews(context, views, weatherData, TemperatureUnit.CELSIUS)

        verify(exactly = 1) { views.setTextViewText(R.id.city_name, "London") }
    }

    @Test
    fun `bindWeeklyForecastRows skips current day and starts from tomorrow`() {
        val today = LocalDate.of(2026, 4, 3)
        mockkObject(DateFormatter)
        every { DateFormatter.formatDayName(LocalDate.of(2026, 4, 4)) } returns "Sat"
        every { DateFormatter.formatDayName(LocalDate.of(2026, 4, 8)) } returns "Wed"

        val weatherData = sampleWeatherData(
            dailyForecasts = (0..7).map { offset ->
                DailyForecast(
                    date = today.plusDays(offset.toLong()),
                    weatherCondition = WeatherCondition.CLEAR_DAY,
                    temperatureMax = 20.0 + offset,
                    temperatureMin = 10.0 + offset,
                    feelsLikeMax = 20.0 + offset,
                    feelsLikeMin = 10.0 + offset,
                    sunrise = LocalTime.of(6, 0),
                    sunset = LocalTime.of(19, 0),
                    daylightDurationSeconds = 36000.0,
                    precipitationSum = 0.0,
                    precipitationProbability = 0,
                    windSpeedMax = 10.0,
                    windDirectionDominant = WindDirection.N,
                    windDirectionDegrees = 0,
                    uvIndexMax = 5.0,
                    averageHumidity = 60,
                    averagePressure = 1012.0,
                )
            },
        )

        WidgetDataBinder.bindWeeklyForecastRows(
            context = context,
            views = views,
            weatherData = weatherData,
            temperatureUnit = TemperatureUnit.CELSIUS,
            today = today,
        )

        verify(exactly = 1) { views.setTextViewText(R.id.fday1_name, "Sat") }
        verify(exactly = 1) { views.setImageViewResource(R.id.fday1_icon, R.drawable.ic_widget_weather_clear_day) }
        verify(exactly = 1) { views.setTextViewText(R.id.fday1_high, "21°/11°") }
        verify(exactly = 1) { views.setTextViewText(R.id.fday5_name, "Wed") }
        verify(exactly = 1) { views.setTextViewText(R.id.fday5_high, "25°/15°") }
        verify(exactly = 1) { views.setViewVisibility(R.id.forecast_container, View.VISIBLE) }
    }

    private fun sampleWeatherData(
        locationName: String = "London",
        locationArea: String? = null,
        dailyForecasts: List<DailyForecast> = listOf(
            DailyForecast(
                date = LocalDate.of(2026, 4, 3),
                weatherCondition = WeatherCondition.PARTLY_CLOUDY_DAY,
                temperatureMax = 20.0,
                temperatureMin = 11.0,
                feelsLikeMax = 20.0,
                feelsLikeMin = 11.0,
                sunrise = LocalTime.of(6, 0),
                sunset = LocalTime.of(19, 0),
                daylightDurationSeconds = 36000.0,
                precipitationSum = 0.0,
                precipitationProbability = 0,
                windSpeedMax = 10.0,
                windDirectionDominant = WindDirection.N,
                windDirectionDegrees = 0,
                uvIndexMax = 5.0,
                averageHumidity = 60,
                averagePressure = 1012.0,
            ),
        ),
    ): WeatherData {
        return WeatherData(
            location = Location(
                id = 1L,
                name = locationName,
                country = "UK",
                latitude = 51.5072,
                longitude = -0.1276,
                area = locationArea,
            ),
            currentWeather = CurrentWeather(
                temperature = 17.0,
                feelsLikeTemperature = 17.0,
                humidity = 60,
                dewPoint = 9.0,
                precipitation = 0.0,
                precipitationProbability = 0,
                weatherCondition = WeatherCondition.PARTLY_CLOUDY_DAY,
                isDay = true,
                pressure = 1012.0,
                windSpeed = 10.0,
                windDirection = WindDirection.N,
                windDirectionDegrees = 0,
                windGusts = 12.0,
                visibility = 10.0,
                uvIndex = 5.0,
                cloudCover = 30,
                lastUpdated = LocalDateTime.of(2026, 4, 3, 10, 15),
            ),
            hourlyForecasts = emptyList(),
            dailyForecasts = dailyForecasts,
        )
    }
}
