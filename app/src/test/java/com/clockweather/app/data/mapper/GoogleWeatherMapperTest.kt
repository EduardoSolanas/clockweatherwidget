package com.clockweather.app.data.mapper

import com.clockweather.app.data.remote.dto.google.GoogleCurrentConditionsDto
import com.clockweather.app.data.remote.dto.google.GoogleDailyForecastResponseDto
import com.clockweather.app.data.remote.dto.google.GoogleTemperatureDto
import com.clockweather.app.data.remote.dto.google.GoogleTimeZoneDto
import com.clockweather.app.domain.model.Location
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.TimeZone

class GoogleWeatherMapperTest {

    private val mapper = GoogleWeatherMapper()

    private val location = Location(
        id = 1L,
        name = "Paris",
        country = "FR",
        latitude = 48.85,
        longitude = 2.35
    )

    /**
     * lastUpdated is now stamped at fetch time (LocalDateTime.now()), not converted from
     * the API's currentTime field. Verify it is close to now (within 5 seconds) regardless
     * of the timezone embedded in the DTO.
     */
    @Test
    fun `lastUpdated is close to now regardless of DTO currentTime`() {
        val before = LocalDateTime.now()

        val currentDto = GoogleCurrentConditionsDto(
            currentTime = "2024-06-15T10:00:00Z",  // arbitrary past UTC time — must be ignored
            timeZone = GoogleTimeZoneDto(id = "Europe/Paris"),
            temperature = GoogleTemperatureDto(degrees = 22.0),
            feelsLikeTemperature = GoogleTemperatureDto(degrees = 21.0)
        )

        val result = mapper.mapToWeatherData(
            current = currentDto,
            hourly = null,
            daily = GoogleDailyForecastResponseDto(),
            location = location
        )

        val after = LocalDateTime.now()
        val lastUpdated = result.currentWeather.lastUpdated

        assertFalse(
            "lastUpdated should not be before the test started",
            lastUpdated.isBefore(before.minusSeconds(1))
        )
        assertFalse(
            "lastUpdated should not be after the test ended",
            lastUpdated.isAfter(after.plusSeconds(1))
        )
    }

    @Test
    fun `lastUpdated is close to now for large UTC offset location`() {
        val before = LocalDateTime.now()

        val currentDto = GoogleCurrentConditionsDto(
            currentTime = "2024-06-15T02:00:00Z",  // arbitrary past UTC time — must be ignored
            timeZone = GoogleTimeZoneDto(id = "Pacific/Auckland"),
            temperature = GoogleTemperatureDto(degrees = 10.0),
            feelsLikeTemperature = GoogleTemperatureDto(degrees = 9.0)
        )

        val result = mapper.mapToWeatherData(
            current = currentDto,
            hourly = null,
            daily = GoogleDailyForecastResponseDto(),
            location = location
        )

        val after = LocalDateTime.now()
        val secondsDiff = ChronoUnit.SECONDS.between(result.currentWeather.lastUpdated, after)

        assertTrue(
            "lastUpdated should be within 5 seconds of now, but was $secondsDiff seconds ago",
            secondsDiff in 0..5
        )
    }

    @Test
    fun `mapAirQuality maps GoogleAirQualityResponseDto to domain AirQuality correctly`() {
        val airQualityDto = com.clockweather.app.data.remote.dto.google.GoogleAirQualityResponseDto(
            indexes = listOf(
                com.clockweather.app.data.remote.dto.google.GoogleAirQualityIndexDto(
                    code = "usa_epa",
                    aqi = 42
                ),
                com.clockweather.app.data.remote.dto.google.GoogleAirQualityIndexDto(
                    code = "gb_daqi",
                    aqi = 2
                )
            ),
            pollutants = listOf(
                com.clockweather.app.data.remote.dto.google.GoogleAirQualityPollutantDto(
                    code = "pm25",
                    concentration = com.clockweather.app.data.remote.dto.google.GoogleAirQualityConcentrationDto(
                        value = 8.5,
                        units = "MICROGRAMS_PER_CUBIC_METER"
                    )
                ),
                com.clockweather.app.data.remote.dto.google.GoogleAirQualityPollutantDto(
                    code = "pm10",
                    concentration = com.clockweather.app.data.remote.dto.google.GoogleAirQualityConcentrationDto(
                        value = 18.0,
                        units = "MICROGRAMS_PER_CUBIC_METER"
                    )
                ),
                com.clockweather.app.data.remote.dto.google.GoogleAirQualityPollutantDto(
                    code = "no2",
                    concentration = com.clockweather.app.data.remote.dto.google.GoogleAirQualityConcentrationDto(
                        value = 10.0,
                        units = "PARTS_PER_BILLION"
                    )
                )
            )
        )

        val domainAq = mapper.mapAirQuality(airQualityDto)
        org.junit.Assert.assertNotNull(domainAq)
        assertEquals(1, domainAq!!.usEpaIndex)
        assertEquals(2, domainAq.gbDefraIndex)
        assertEquals(8.5, domainAq.pm25, 0.01)
        assertEquals(18.0, domainAq.pm10, 0.01)
        assertEquals(18.8, domainAq.no2, 0.01)
    }

    @Test
    fun `mapAirQuality maps uaqi index scale correctly`() {
        val airQualityDto = com.clockweather.app.data.remote.dto.google.GoogleAirQualityResponseDto(
            indexes = listOf(
                com.clockweather.app.data.remote.dto.google.GoogleAirQualityIndexDto(
                    code = "uaqi",
                    aqi = 85
                ),
                com.clockweather.app.data.remote.dto.google.GoogleAirQualityIndexDto(
                    code = "gbr_defra",
                    aqi = 3
                )
            )
        )

        val domainAq = mapper.mapAirQuality(airQualityDto)
        org.junit.Assert.assertNotNull(domainAq)
        assertEquals(1, domainAq!!.usEpaIndex)
        assertEquals(3, domainAq.gbDefraIndex)
    }

    @Test
    fun `mapAirQuality returns null when dto is null or empty`() {
        org.junit.Assert.assertNull(mapper.mapAirQuality(null))
        org.junit.Assert.assertNull(
            mapper.mapAirQuality(
                com.clockweather.app.data.remote.dto.google.GoogleAirQualityResponseDto()
            )
        )
    }

    @Test
    fun `hourly forecast with parse failure is dropped and does not masquerade as now`() {
        val currentDto = GoogleCurrentConditionsDto(
            currentTime = "2026-09-05T10:00:00Z",
            timeZone = GoogleTimeZoneDto(id = "Europe/London"),
            temperature = GoogleTemperatureDto(degrees = 18.0)
        )
        val badHourlyDto = com.clockweather.app.data.remote.dto.google.GoogleHourlyForecastDto(
            displayDateTime = com.clockweather.app.data.remote.dto.google.GoogleDisplayDateTimeDto(
                year = 2026,
                month = 99, // Invalid month!
                day = 1,
                hours = 12
            )
        )
        val result = mapper.mapToWeatherData(
            current = currentDto,
            hourly = com.clockweather.app.data.remote.dto.google.GoogleHourlyForecastResponseDto(
                forecastHours = listOf(badHourlyDto)
            ),
            daily = GoogleDailyForecastResponseDto(),
            location = location
        )
        // Parse failure must drop the hour rather than mapping it to LocalDateTime.now()
        assertTrue("Invalid hour should be dropped", result.hourlyForecasts.isEmpty())
    }

    @Test
    fun `hourly forecast parses interval startTime when provided`() {
        withDefaultTimeZone("UTC") {
            val currentDto = GoogleCurrentConditionsDto(
                currentTime = "2026-09-05T10:00:00Z",
                timeZone = GoogleTimeZoneDto(id = "UTC"),
                temperature = GoogleTemperatureDto(degrees = 18.0)
            )
            val hourlyDto = com.clockweather.app.data.remote.dto.google.GoogleHourlyForecastDto(
                interval = com.clockweather.app.data.remote.dto.google.GoogleIntervalDto(
                    startTime = "2026-09-05T14:00:00Z"
                ),
                displayDateTime = com.clockweather.app.data.remote.dto.google.GoogleDisplayDateTimeDto(
                    year = 2026,
                    month = 9,
                    day = 5,
                    hours = 14,
                    utcOffset = "+00:00"
                )
            )
            val result = mapper.mapToWeatherData(
                current = currentDto,
                hourly = com.clockweather.app.data.remote.dto.google.GoogleHourlyForecastResponseDto(
                    forecastHours = listOf(hourlyDto)
                ),
                daily = GoogleDailyForecastResponseDto(),
                location = location
            )
            assertEquals(1, result.hourlyForecasts.size)
            assertEquals(LocalDateTime.of(2026, 9, 5, 14, 0, 0), result.hourlyForecasts.first().dateTime)
        }
    }

    @Test
    fun `mapAirQuality stamps lastUpdated timestamp`() {
        val airQualityDto = com.clockweather.app.data.remote.dto.google.GoogleAirQualityResponseDto(
            indexes = listOf(
                com.clockweather.app.data.remote.dto.google.GoogleAirQualityIndexDto(
                    code = "usa_epa",
                    aqi = 42
                )
            ),
            pollutants = emptyList()
        )
        val domainAq = mapper.mapAirQuality(airQualityDto)
        org.junit.Assert.assertNotNull(domainAq)
        org.junit.Assert.assertNotNull(domainAq!!.lastUpdated)
    }

    @Test
    fun `mapToWeatherData preserves cached air quality and pollen timestamps when responses are null`() {
        val currentDto = GoogleCurrentConditionsDto(
            currentTime = "2026-09-05T10:00:00Z",
            timeZone = GoogleTimeZoneDto(id = "UTC"),
            temperature = GoogleTemperatureDto(degrees = 18.0)
        )
        val cachedTime = LocalDateTime.of(2026, 9, 5, 8, 0)
        val cachedAq = com.clockweather.app.domain.model.AirQuality(
            co = 1.0, no2 = 1.0, o3 = 1.0, so2 = 1.0, pm25 = 5.0, pm10 = 10.0, usEpaIndex = 1, gbDefraIndex = 1,
            lastUpdated = cachedTime
        )
        val result = mapper.mapToWeatherData(
            current = currentDto,
            hourly = null,
            daily = GoogleDailyForecastResponseDto(),
            pollen = null,
            openMeteoPollen = null,
            cachedAirQuality = cachedAq,
            cachedPollenLastUpdated = cachedTime,
            location = location
        )
        assertEquals(cachedTime, result.airQuality?.lastUpdated)
        assertEquals(cachedTime, result.pollenLastUpdated)
    }

    @Test
    fun `hourly interval instant is displayed in the device timezone`() {
        withDefaultTimeZone("America/Los_Angeles") {
            val result = mapper.mapToWeatherData(
                current = currentDto(timeZone = "Europe/Paris"),
                hourly = hourlyResponse(
                    intervalStart = "2026-09-05T14:00:00Z",
                    year = 2026,
                    month = 9,
                    day = 5,
                    hours = 16,
                    utcOffset = "3600s"
                ),
                daily = GoogleDailyForecastResponseDto(),
                location = location
            )

            assertEquals(LocalDateTime.of(2026, 9, 5, 7, 0), result.hourlyForecasts.single().dateTime)
        }
    }

    @Test
    fun `hourly display fallback converts seconds offset into device timezone`() {
        withDefaultTimeZone("Europe/London") {
            val result = mapper.mapToWeatherData(
                current = currentDto(timeZone = "America/Los_Angeles"),
                hourly = hourlyResponse(
                    year = 2026,
                    month = 9,
                    day = 5,
                    hours = 7,
                    utcOffset = "-28800s"
                ),
                daily = GoogleDailyForecastResponseDto(),
                location = location
            )

            assertEquals(LocalDateTime.of(2026, 9, 5, 16, 0), result.hourlyForecasts.single().dateTime)
        }
    }

    @Test
    fun `hourly display fallback also supports ISO offsets`() {
        withDefaultTimeZone("UTC") {
            val result = mapper.mapToWeatherData(
                current = currentDto(timeZone = "Europe/Paris"),
                hourly = hourlyResponse(
                    year = 2026,
                    month = 9,
                    day = 5,
                    hours = 16,
                    utcOffset = "+01:00"
                ),
                daily = GoogleDailyForecastResponseDto(),
                location = location
            )

            assertEquals(LocalDateTime.of(2026, 9, 5, 15, 0), result.hourlyForecasts.single().dateTime)
        }
    }

    @Test
    fun `malformed interval timestamp is dropped rather than fabricated from display time`() {
        val result = mapper.mapToWeatherData(
            current = currentDto(),
            hourly = hourlyResponse(
                intervalStart = "not-an-instant",
                year = 2026,
                month = 9,
                day = 5,
                hours = 16,
                utcOffset = "3600s"
            ),
            daily = GoogleDailyForecastResponseDto(),
            location = location
        )

        assertTrue(result.hourlyForecasts.isEmpty())
    }

    private fun currentDto(timeZone: String = "UTC") = GoogleCurrentConditionsDto(
        timeZone = GoogleTimeZoneDto(id = timeZone),
        temperature = GoogleTemperatureDto(degrees = 18.0)
    )

    private fun hourlyResponse(
        intervalStart: String? = null,
        year: Int,
        month: Int,
        day: Int,
        hours: Int,
        utcOffset: String
    ) = com.clockweather.app.data.remote.dto.google.GoogleHourlyForecastResponseDto(
        forecastHours = listOf(
            com.clockweather.app.data.remote.dto.google.GoogleHourlyForecastDto(
                interval = intervalStart?.let {
                    com.clockweather.app.data.remote.dto.google.GoogleIntervalDto(startTime = it)
                },
                displayDateTime = com.clockweather.app.data.remote.dto.google.GoogleDisplayDateTimeDto(
                    year = year,
                    month = month,
                    day = day,
                    hours = hours,
                    utcOffset = utcOffset
                )
            )
        )
    )

    private fun <T> withDefaultTimeZone(id: String, block: () -> T): T {
        val previous = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(id))
        return try {
            block()
        } finally {
            TimeZone.setDefault(previous)
        }
    }
}
