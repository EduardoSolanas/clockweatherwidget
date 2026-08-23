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
}
