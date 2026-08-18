package com.clockweather.app.data.mapper

import com.clockweather.app.data.remote.dto.google.*
import com.clockweather.app.domain.model.Location
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class GoogleWeatherMapperPollenTest {

    private val mapper = GoogleWeatherMapper()
    private val location = Location(
        id = 1L,
        name = "London",
        country = "GB",
        latitude = 51.5074,
        longitude = -0.1278
    )

    private val currentConditions = GoogleCurrentConditionsDto(
        currentTime = "2026-08-18T12:00:00Z",
        timeZone = GoogleTimeZoneDto(id = "Europe/London"),
        temperature = GoogleTemperatureDto(degrees = 22.0)
    )

    @Test
    fun `mapToWeatherData attaches pollen data to matching forecast days by date`() {
        val today = LocalDate.of(2026, 8, 18)
        val tomorrow = LocalDate.of(2026, 8, 19)
        val dayAfter = LocalDate.of(2026, 8, 20)

        val dailyDto = GoogleDailyForecastResponseDto(
            forecastDays = listOf(
                GoogleDailyForecastDto(
                    displayDate = GoogleDisplayDateDto(year = 2026, month = 8, day = 18),
                    maxTemperature = GoogleTemperatureDto(degrees = 24.0),
                    minTemperature = GoogleTemperatureDto(degrees = 15.0)
                ),
                GoogleDailyForecastDto(
                    displayDate = GoogleDisplayDateDto(year = 2026, month = 8, day = 19),
                    maxTemperature = GoogleTemperatureDto(degrees = 23.0),
                    minTemperature = GoogleTemperatureDto(degrees = 14.0)
                ),
                GoogleDailyForecastDto(
                    displayDate = GoogleDisplayDateDto(year = 2026, month = 8, day = 20),
                    maxTemperature = GoogleTemperatureDto(degrees = 21.0),
                    minTemperature = GoogleTemperatureDto(degrees = 13.0)
                )
            )
        )

        val pollenDto = GooglePollenForecastResponseDto(
            dailyInfo = listOf(
                GooglePollenDayInfoDto(
                    date = GooglePollenDateDto(year = 2026, month = 8, day = 18),
                    pollenTypeInfo = listOf(
                        GooglePollenTypeInfoDto(
                            code = "GRASS",
                            displayName = "Grass",
                            inSeason = true,
                            indexInfo = GooglePollenIndexInfoDto(value = 3, category = "Moderate"),
                            healthRecommendations = listOf("Keep windows closed during midday.")
                        ),
                        GooglePollenTypeInfoDto(
                            code = "TREE",
                            displayName = "Tree",
                            inSeason = true,
                            indexInfo = GooglePollenIndexInfoDto(value = 2, category = "Low")
                        ),
                        GooglePollenTypeInfoDto(
                            code = "WEED",
                            displayName = "Weed",
                            inSeason = false,
                            indexInfo = GooglePollenIndexInfoDto(value = 0, category = "None")
                        )
                    ),
                    plantInfo = listOf(
                        GooglePollenPlantInfoDto(
                            code = "BIRCH",
                            displayName = "Birch",
                            inSeason = true,
                            indexInfo = GooglePollenIndexInfoDto(value = 2, category = "Low")
                        )
                    )
                ),
                GooglePollenDayInfoDto(
                    date = GooglePollenDateDto(year = 2026, month = 8, day = 19),
                    pollenTypeInfo = listOf(
                        GooglePollenTypeInfoDto(
                            code = "GRASS",
                            displayName = "Grass",
                            inSeason = true,
                            indexInfo = GooglePollenIndexInfoDto(value = 4, category = "High")
                        )
                    )
                )
                // Day 20 has no pollen info
            )
        )

        val weatherData = mapper.mapToWeatherData(
            current = currentConditions,
            hourly = null,
            daily = dailyDto,
            pollen = pollenDto,
            location = location
        )

        assertEquals(3, weatherData.dailyForecasts.size)

        // Day 1: Has full pollen data
        val day1 = weatherData.dailyForecasts[0]
        assertEquals(today, day1.date)
        assertNotNull(day1.pollen)
        val p1 = day1.pollen!!
        assertEquals(3, p1.grassPollen?.indexValue)
        assertEquals("Moderate", p1.grassPollen?.category)
        assertTrue(p1.grassPollen?.inSeason == true)
        assertEquals(2, p1.treePollen?.indexValue)
        assertEquals("Low", p1.treePollen?.category)
        assertEquals(0, p1.weedPollen?.indexValue)
        assertEquals(3, p1.maxIndex)
        assertEquals("Moderate", p1.maxCategory)
        assertEquals(1, p1.healthRecommendations.size)
        assertEquals("Keep windows closed during midday.", p1.healthRecommendations.first())
        assertEquals(1, p1.dominantPlants.size)
        assertEquals("Birch", p1.dominantPlants.first().displayName)

        // Day 2: Has grass pollen data
        val day2 = weatherData.dailyForecasts[1]
        assertEquals(tomorrow, day2.date)
        assertNotNull(day2.pollen)
        val p2 = day2.pollen!!
        assertEquals(4, p2.grassPollen?.indexValue)
        assertEquals("High", p2.grassPollen?.category)
        assertNull(p2.treePollen)
        assertEquals(4, p2.maxIndex)
        assertEquals("High", p2.maxCategory)

        // Day 3: No pollen data -> null
        val day3 = weatherData.dailyForecasts[2]
        assertEquals(dayAfter, day3.date)
        assertNull(day3.pollen)
    }

    @Test
    fun `mapToWeatherData produces null pollen when pollen DTO is null or empty`() {
        val dailyDto = GoogleDailyForecastResponseDto(
            forecastDays = listOf(
                GoogleDailyForecastDto(
                    displayDate = GoogleDisplayDateDto(year = 2026, month = 8, day = 18),
                    maxTemperature = GoogleTemperatureDto(degrees = 24.0),
                    minTemperature = GoogleTemperatureDto(degrees = 15.0)
                )
            )
        )

        val weatherDataNullPollen = mapper.mapToWeatherData(
            current = currentConditions,
            hourly = null,
            daily = dailyDto,
            pollen = null,
            location = location
        )
        assertNull(weatherDataNullPollen.dailyForecasts.first().pollen)

        val weatherDataEmptyPollen = mapper.mapToWeatherData(
            current = currentConditions,
            hourly = null,
            daily = dailyDto,
            pollen = GooglePollenForecastResponseDto(dailyInfo = emptyList()),
            location = location
        )
        assertNull(weatherDataEmptyPollen.dailyForecasts.first().pollen)
    }
}
