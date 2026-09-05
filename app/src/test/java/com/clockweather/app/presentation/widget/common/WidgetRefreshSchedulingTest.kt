package com.clockweather.app.presentation.widget.common

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.clockweather.app.domain.model.CurrentWeather
import com.clockweather.app.domain.model.DailyForecast
import com.clockweather.app.domain.model.HourlyForecast
import com.clockweather.app.domain.model.Location
import com.clockweather.app.domain.model.WeatherCondition
import com.clockweather.app.domain.model.WeatherData
import com.clockweather.app.domain.model.WindDirection
import com.clockweather.app.data.provider.WeatherProviderPreferences
import com.clockweather.app.domain.model.WeatherProviderType
import com.clockweather.app.presentation.settings.SettingsViewModel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Guards the freshness-watchdog contract.
 *
 * `updateWidget` only performs its own staleness check when it builds its own snapshot.
 * Every caller that passes a shared [WidgetRenderSnapshot] therefore owns the check itself.
 * If one of those callers forgets, the widget still redraws but never enqueues a refresh —
 * which is how commit d252045's "frozen while the screen stays on" regression comes back
 * without `updatePeriodMillis` ever changing.
 */
class WidgetRefreshSchedulingTest {

    private val referenceDateTime: LocalDateTime = LocalDateTime.of(2026, 4, 3, 10, 42)
    private val referenceInstant: Instant =
        referenceDateTime.atZone(ZoneId.systemDefault()).toInstant()

    @Test
    fun `refresh is scheduled when the cache holds no weather at all`() {
        val snapshot = snapshot(weather = null)

        assertTrue(
            BaseWidgetUpdater.shouldScheduleRefresh(
                snapshot = snapshot,
                minimumFutureForecastDaysRequired = 0,
                currentInstant = referenceInstant,
            )
        )
    }

    @Test
    fun `refresh is not scheduled when cached weather is inside the configured interval`() {
        val snapshot = snapshot(
            weather = sampleWeatherData(lastUpdated = referenceDateTime.minusMinutes(5)),
            refreshIntervalMinutes = 30,
        )

        assertFalse(
            BaseWidgetUpdater.shouldScheduleRefresh(
                snapshot = snapshot,
                minimumFutureForecastDaysRequired = 0,
                currentInstant = referenceInstant,
            )
        )
    }

    @Test
    fun `refresh is scheduled when cached weather is older than the configured interval`() {
        val snapshot = snapshot(
            weather = sampleWeatherData(lastUpdated = referenceDateTime.minusMinutes(31)),
            refreshIntervalMinutes = 30,
        )

        assertTrue(
            BaseWidgetUpdater.shouldScheduleRefresh(
                snapshot = snapshot,
                minimumFutureForecastDaysRequired = 0,
                currentInstant = referenceInstant,
            )
        )
    }

    @Test
    fun `refresh is scheduled when forecast coverage is shorter than the widget requires`() {
        val snapshot = snapshot(
            weather = sampleWeatherData(
                lastUpdated = referenceDateTime.minusMinutes(1),
                dailyForecasts = dailyForecastsFrom(referenceDateTime.toLocalDate(), count = 2),
            ),
            refreshIntervalMinutes = 30,
        )

        assertTrue(
            "a forecast widget needing 5 future days must refresh when only 2 are cached",
            BaseWidgetUpdater.shouldScheduleRefresh(
                snapshot = snapshot,
                minimumFutureForecastDaysRequired = 5,
                currentInstant = referenceInstant,
            )
        )
    }

    /**
     * The forecast widget renders a fixed number of rows, and the shortest forecast a user
     * can select is seven days. If its declared minimum ever exceeds what that selection
     * delivers, the widget reports stale on every callback and enqueues work forever.
     */
    @Test
    fun `the forecast widget cannot need more coverage than the shortest selectable forecast`() {
        val shortestSelectableForecastDays = 7
        val required = requiredForecastDaysForRefresh(
            requestedForecastDays = shortestSelectableForecastDays,
            minimumFutureForecastDaysRequired = FORECAST_WIDGET_ROW_COUNT - 1,
        )

        assertTrue(
            "the forecast widget renders $FORECAST_WIDGET_ROW_COUNT rows, so it must be " +
                "satisfiable by a $shortestSelectableForecastDays-day fetch, but it needs $required days",
            required <= shortestSelectableForecastDays
        )
    }

    @Test
    fun `a seven day cache satisfies the forecast widget`() {
        val snapshot = snapshot(
            weather = sampleWeatherData(
                lastUpdated = referenceDateTime.minusMinutes(1),
                dailyForecasts = dailyForecastsFrom(referenceDateTime.toLocalDate(), count = 7),
            ),
            refreshIntervalMinutes = 30,
            forecastDays = 7,
        )

        assertFalse(
            "a seven-day fetch is what the default setting asks for, so it must count as fresh",
            BaseWidgetUpdater.shouldScheduleRefresh(
                snapshot = snapshot,
                minimumFutureForecastDaysRequired = FORECAST_WIDGET_ROW_COUNT - 1,
                currentInstant = referenceInstant,
            )
        )
    }

    /**
     * The worker fetches the saved forecast length, so the widget's freshness target has to
     * track the same setting rather than a hardcoded one. Open-Meteo is pinned because the
     * selectable lengths are per-provider, and 14 is its longer option.
     */
    @Test
    fun `the freshness target follows the saved forecast length`() {
        val longSelection = snapshot(
            weather = sampleWeatherData(
                lastUpdated = referenceDateTime.minusMinutes(1),
                dailyForecasts = dailyForecastsFrom(referenceDateTime.toLocalDate(), count = 7),
            ),
            refreshIntervalMinutes = 30,
            forecastDays = 14,
            provider = WeatherProviderType.OPEN_METEO,
        )

        assertTrue(
            "seven cached days cannot satisfy a fourteen-day selection",
            BaseWidgetUpdater.shouldScheduleRefresh(
                snapshot = longSelection,
                minimumFutureForecastDaysRequired = 0,
                currentInstant = referenceInstant,
            )
        )

        val covered = snapshot(
            weather = sampleWeatherData(
                lastUpdated = referenceDateTime.minusMinutes(1),
                dailyForecasts = dailyForecastsFrom(referenceDateTime.toLocalDate(), count = 14),
            ),
            refreshIntervalMinutes = 30,
            forecastDays = 14,
            provider = WeatherProviderType.OPEN_METEO,
        )

        assertFalse(
            "fourteen cached days satisfy a fourteen-day selection",
            BaseWidgetUpdater.shouldScheduleRefresh(
                snapshot = covered,
                minimumFutureForecastDaysRequired = 0,
                currentInstant = referenceInstant,
            )
        )
    }

    /**
     * The regression guard. Both snapshot-passing call sites must delegate to the shared
     * helper; neither may inline its own copy of the decision, and neither may skip it.
     */
    @Test
    fun `every caller that passes a shared snapshot also schedules a stale refresh`() {
        val callSites = mapOf(
            "BaseWidgetProvider.onUpdate" to
                File("src/main/java/com/clockweather/app/presentation/widget/common/BaseWidgetProvider.kt"),
            "ClockWeatherApplication.refreshAllWidgets" to
                File("src/main/java/com/clockweather/app/ClockWeatherApplication.kt"),
        )

        callSites.forEach { (name, file) ->
            val source = file.readText()
            assertTrue("${file.path} should exist", file.exists())
            assertTrue(
                "$name passes a shared render snapshot to updateWidget, so it must also call " +
                    "scheduleRefreshIfStale — otherwise a stale widget redraws but never refreshes",
                source.contains("scheduleRefreshIfStale")
            )
        }
    }

    private fun snapshot(
        weather: WeatherData?,
        refreshIntervalMinutes: Int? = null,
        forecastDays: Int? = null,
        provider: WeatherProviderType? = null,
    ): WidgetRenderSnapshot {
        val prefs = mutablePreferencesOf().apply {
            refreshIntervalMinutes?.let { this[SettingsViewModel.KEY_WEATHER_REFRESH_INTERVAL] = it }
            forecastDays?.let { this[SettingsViewModel.KEY_FORECAST_DAYS] = it }
            provider?.let { this[WeatherProviderPreferences.KEY_WEATHER_PROVIDER] = it.storageValue }
        }
        return WidgetRenderSnapshot(
            prefs = prefs,
            location = london,
            weather = weather,
        )
    }

    private val london = Location(
        id = 1L,
        name = "London",
        country = "UK",
        latitude = 51.5072,
        longitude = -0.1276,
        timezone = "auto",
    )

    private fun sampleWeatherData(
        lastUpdated: LocalDateTime,
        hourlyForecasts: List<HourlyForecast> = hourlyForecastsFrom(referenceDateTime, count = 24),
        dailyForecasts: List<DailyForecast> = dailyForecastsFrom(referenceDateTime.toLocalDate(), count = 7),
    ) = WeatherData(
        location = london,
        currentWeather = CurrentWeather(
            temperature = 15.0,
            feelsLikeTemperature = 15.0,
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
            visibility = 10_000.0,
            uvIndex = 5.0,
            cloudCover = 30,
            lastUpdated = lastUpdated,
        ),
        hourlyForecasts = hourlyForecasts,
        dailyForecasts = dailyForecasts,
    )

    private fun hourlyForecastsFrom(start: LocalDateTime, count: Int): List<HourlyForecast> {
        val firstHour = start.withMinute(0).withSecond(0).withNano(0)
        return (0 until count).map { offset ->
            HourlyForecast(
                dateTime = firstHour.plusHours(offset.toLong()),
                temperature = 15.0 + offset,
                feelsLike = 15.0 + offset,
                humidity = 60,
                dewPoint = 9.0,
                precipitationProbability = 0,
                weatherCondition = WeatherCondition.PARTLY_CLOUDY_DAY,
                isDay = true,
                pressure = 1012.0,
                windSpeed = 10.0,
                windDirection = WindDirection.N,
                windDirectionDegrees = 0,
                visibility = 10_000.0,
                uvIndex = 5.0,
            )
        }
    }

    private fun dailyForecastsFrom(start: LocalDate, count: Int): List<DailyForecast> =
        (0 until count).map { offset ->
            DailyForecast(
                date = start.plusDays(offset.toLong()),
                weatherCondition = WeatherCondition.PARTLY_CLOUDY_DAY,
                temperatureMax = 20.0,
                temperatureMin = 11.0,
                feelsLikeMax = 20.0,
                feelsLikeMin = 11.0,
                sunrise = LocalTime.of(6, 0),
                sunset = LocalTime.of(19, 0),
                daylightDurationSeconds = 36_000.0,
                precipitationSum = 0.0,
                precipitationProbability = 0,
                windSpeedMax = 10.0,
                windDirectionDominant = WindDirection.N,
                windDirectionDegrees = 0,
                uvIndexMax = 5.0,
                averageHumidity = 60,
                averagePressure = 1012.0,
            )
        }
}
