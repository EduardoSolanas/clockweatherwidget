package com.clockweather.app.data.mapper

import com.clockweather.app.data.local.entity.DailyForecastEntity
import com.clockweather.app.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class WeatherEntityMapperPollenTest {

    private val mapper = WeatherEntityMapper()

    @Test
    fun `mapDailyToEntity and mapDailyToDomain roundtrip preserves pollen data`() {
        val forecastWithPollen = DailyForecast(
            date = LocalDate.of(2026, 8, 18),
            weatherCondition = WeatherCondition.CLEAR_DAY,
            temperatureMax = 25.0,
            temperatureMin = 15.0,
            feelsLikeMax = 26.0,
            feelsLikeMin = 14.0,
            sunrise = LocalTime.of(6, 0),
            sunset = LocalTime.of(20, 0),
            daylightDurationSeconds = 50400.0,
            precipitationSum = 0.0,
            precipitationProbability = 10,
            windSpeedMax = 12.0,
            windDirectionDominant = WindDirection.N,
            windDirectionDegrees = 0,
            uvIndexMax = 5.0,
            averageHumidity = 50,
            averagePressure = 1013.25,
            pollen = PollenData(
                grassPollen = PollenType("GRASS", "Grass", inSeason = true, indexValue = 3, category = "Moderate"),
                treePollen = PollenType("TREE", "Tree", inSeason = true, indexValue = 2, category = "Low"),
                weedPollen = PollenType("WEED", "Weed", inSeason = false, indexValue = 0, category = "None"),
                dominantPlants = listOf(PlantPollen("BIRCH", "Birch", inSeason = true)),
                healthRecommendations = listOf("Keep windows closed.")
            )
        )

        val entity = mapper.mapDailyToEntity(forecastWithPollen, locationId = 1L)
        assertEquals(3, entity.pollenGrassIndex)
        assertEquals("Moderate", entity.pollenGrassCategory)
        assertEquals(2, entity.pollenTreeIndex)
        assertEquals("Low", entity.pollenTreeCategory)
        assertEquals(0, entity.pollenWeedIndex)
        assertEquals("None", entity.pollenWeedCategory)
        assertEquals("Keep windows closed.", entity.pollenHealthRecommendations)
        assertEquals("Birch", entity.pollenDominantPlants)

        val domain = mapper.mapDailyToDomain(entity)
        assertNotNull(domain.pollen)
        val pollen = domain.pollen!!
        assertEquals(3, pollen.grassPollen?.indexValue)
        assertEquals("Moderate", pollen.grassPollen?.category)
        assertEquals(2, pollen.treePollen?.indexValue)
        assertEquals("Low", pollen.treePollen?.category)
        assertEquals(0, pollen.weedPollen?.indexValue)
        assertEquals(1, pollen.dominantPlants.size)
        assertEquals("Birch", pollen.dominantPlants.first().displayName)
        assertEquals(1, pollen.healthRecommendations.size)
        assertEquals("Keep windows closed.", pollen.healthRecommendations.first())
    }

    @Test
    fun `mapDailyToDomain returns null pollen when entity has no pollen data`() {
        val entity = DailyForecastEntity(
            id = 1L,
            locationId = 1L,
            date = "2026-08-18",
            weatherCode = 0,
            temperatureMax = 25.0,
            temperatureMin = 15.0,
            feelsLikeMax = 26.0,
            feelsLikeMin = 14.0,
            sunrise = "06:00",
            sunset = "20:00",
            daylightDurationSeconds = 50400.0,
            precipitationSum = 0.0,
            precipitationProbability = 10,
            windSpeedMax = 12.0,
            windDirectionDegrees = 0,
            uvIndexMax = 5.0,
            averageHumidity = 50,
            averagePressure = 1013.25,
            pollenGrassIndex = null,
            pollenGrassCategory = null,
            pollenTreeIndex = null,
            pollenTreeCategory = null,
            pollenWeedIndex = null,
            pollenWeedCategory = null,
            pollenHealthRecommendations = null,
            pollenDominantPlants = null
        )

        val domain = mapper.mapDailyToDomain(entity)
        assertNull(domain.pollen)
    }
}
