package com.clockweather.app.data.repository

import com.clockweather.app.data.local.dao.CurrentWeatherDao
import com.clockweather.app.data.local.dao.DailyForecastDao
import com.clockweather.app.data.local.dao.HourlyForecastDao
import com.clockweather.app.data.local.dao.LocationDao
import com.clockweather.app.data.local.db.WeatherDatabase
import com.clockweather.app.data.mapper.WeatherDtoMapper
import com.clockweather.app.data.mapper.WeatherEntityMapper
import com.clockweather.app.data.remote.api.OpenMeteoWeatherApi
import com.clockweather.app.data.remote.dto.WeatherResponseDto
import com.clockweather.app.domain.model.CurrentWeather
import com.clockweather.app.domain.model.Location
import com.clockweather.app.domain.model.WeatherCondition
import com.clockweather.app.domain.model.WeatherData
import com.clockweather.app.domain.model.WindDirection
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDateTime

/**
 * TDD: WeatherRepositoryImpl.refreshWeatherData must pass the given forecastDays
 * value to the API instead of always using a hardcoded 7.
 *
 * The test aborts the flow after the API call (by throwing from dtoMapper) so we never
 * need to mock Room's withTransaction extension, keeping the test simple and focused.
 */
class WeatherRepositoryForecastDaysTest {

    private val api: OpenMeteoWeatherApi = mockk()
    private val database: WeatherDatabase = mockk()
    private val currentWeatherDao: CurrentWeatherDao = mockk()
    private val hourlyForecastDao: HourlyForecastDao = mockk()
    private val dailyForecastDao: DailyForecastDao = mockk()
    private val locationDao: LocationDao = mockk()
    private val dtoMapper: WeatherDtoMapper = mockk()
    private val entityMapper: WeatherEntityMapper = mockk()

    private val repository = WeatherRepositoryImpl(
        openMeteoApi = api,
        database = database,
        currentWeatherDao = currentWeatherDao,
        hourlyForecastDao = hourlyForecastDao,
        dailyForecastDao = dailyForecastDao,
        locationDao = locationDao,
        dtoMapper = dtoMapper,
        entityMapper = entityMapper
    )

    private val location = Location(
        id = 1L,
        name = "Berlin",
        country = "DE",
        latitude = 52.52,
        longitude = 13.405
    )

    private fun fakeResponseDto() = WeatherResponseDto(
        latitude = 52.52,
        longitude = 13.405,
        elevation = 34.0,
        generationTimeMs = 1.0,
        utcOffsetSeconds = 3600,
        timezone = "Europe/Berlin",
        timezoneAbbreviation = "CET",
        current = null,
        currentUnits = null,
        hourly = null,
        hourlyUnits = null,
        daily = null,
        dailyUnits = null
    )

    /**
     * Set up the API mock to capture forecastDays, then throw from dtoMapper so we
     * never need to reach the Room transaction (keeping the test focused and simple).
     */
    private fun setupApiCapture(forecastDaysSlot: io.mockk.CapturingSlot<Int>) {
        coEvery {
            api.getWeatherForecast(
                latitude = any(),
                longitude = any(),
                current = any(),
                hourly = any(),
                daily = any(),
                timezone = any(),
                forecastDays = capture(forecastDaysSlot),
                windSpeedUnit = any(),
                temperatureUnit = any()
            )
        } returns fakeResponseDto()

        // Abort after the API call so we never touch the Room transaction
        coEvery { dtoMapper.mapToWeatherData(any(), any()) } throws
            RuntimeException("abort-after-api-call (intentional test sentinel)")
    }

    @Test
    fun `refreshWeatherData calls API with forecastDays 14 when 14 passed`() = runTest {
        val forecastDaysSlot = slot<Int>()
        setupApiCapture(forecastDaysSlot)

        runCatching { repository.refreshWeatherData(location, forecastDays = 14) }

        assert(forecastDaysSlot.isCaptured) { "API was not called at all" }
        assert(forecastDaysSlot.captured == 14) {
            "Expected forecastDays=14 in API call but got ${forecastDaysSlot.captured}"
        }
    }

    @Test
    fun `refreshWeatherData calls API with forecastDays 7 when 7 passed`() = runTest {
        val forecastDaysSlot = slot<Int>()
        setupApiCapture(forecastDaysSlot)

        runCatching { repository.refreshWeatherData(location, forecastDays = 7) }

        assert(forecastDaysSlot.isCaptured) { "API was not called at all" }
        assert(forecastDaysSlot.captured == 7) {
            "Expected forecastDays=7 in API call but got ${forecastDaysSlot.captured}"
        }
    }

    @Test
    fun `refreshWeatherData never hardcodes 7 when 14 is requested`() = runTest {
        val forecastDaysSlot = slot<Int>()
        setupApiCapture(forecastDaysSlot)

        runCatching { repository.refreshWeatherData(location, forecastDays = 14) }

        assert(forecastDaysSlot.isCaptured) { "API was not called at all" }
        assert(forecastDaysSlot.captured != 7) {
            "forecastDays was hardcoded to 7 instead of using the requested 14"
        }
        coVerify(exactly = 1) {
            api.getWeatherForecast(
                latitude = location.latitude,
                longitude = location.longitude,
                forecastDays = 14,
                current = any(),
                hourly = any(),
                daily = any(),
                timezone = any(),
                windSpeedUnit = any(),
                temperatureUnit = any()
            )
        }
    }
}
