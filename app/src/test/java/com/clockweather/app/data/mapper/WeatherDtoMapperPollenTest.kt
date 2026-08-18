package com.clockweather.app.data.mapper

import com.clockweather.app.data.remote.dto.*
import com.clockweather.app.data.remote.dto.openmeteo.OpenMeteoAirQualityHourlyDto
import com.clockweather.app.data.remote.dto.openmeteo.OpenMeteoAirQualityResponseDto
import com.clockweather.app.domain.model.Location
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class WeatherDtoMapperPollenTest {

    private val mapper = WeatherDtoMapper()
    private val location = Location(
        id = 1L,
        name = "Berlin",
        country = "DE",
        latitude = 52.52,
        longitude = 13.41
    )

    private val weatherResponse = WeatherResponseDto(
        latitude = 52.52,
        longitude = 13.41,
        elevation = 38.0,
        generationTimeMs = 0.5,
        utcOffsetSeconds = 0,
        timezone = "Europe/Berlin",
        timezoneAbbreviation = "CET",
        current = CurrentWeatherDto(
            time = "2026-08-18T12:00",
            temperature = 22.0,
            apparentTemperature = 21.0,
            isDay = 1,
            weatherCode = 0,
            relativeHumidity = 50,
            windSpeed = 10.0,
            windDirection = 180,
            windGusts = 15.0,
            precipitation = 0.0,
            pressureMsl = 1013.0,
            surfacePressure = 1013.0,
            visibility = 10000.0,
            uvIndex = 5.0,
            dewPoint = 12.0,
            cloudCover = 10
        ),
        currentUnits = null,
        hourly = HourlyWeatherDto(
            time = listOf("2026-08-18T12:00"),
            temperature = listOf(22.0),
            apparentTemperature = listOf(21.0),
            relativeHumidity = listOf(50),
            dewPoint = listOf(12.0),
            precipitationProbability = listOf(0),
            weatherCode = listOf(0),
            pressureMsl = listOf(1013.0),
            windSpeed = listOf(10.0),
            windDirection = listOf(180),
            visibility = listOf(10000.0),
            uvIndex = listOf(5.0),
            isDay = listOf(1)
        ),
        hourlyUnits = null,
        daily = DailyWeatherDto(
            time = listOf("2026-08-18"),
            weatherCode = listOf(0),
            temperatureMax = listOf(25.0),
            temperatureMin = listOf(14.0),
            apparentTemperatureMax = listOf(24.0),
            apparentTemperatureMin = listOf(13.0),
            sunrise = listOf("2026-08-18T06:00"),
            sunset = listOf("2026-08-18T20:30"),
            daylightDuration = listOf(52200.0),
            precipitationSum = listOf(0.0),
            precipitationProbabilityMax = listOf(10),
            windSpeedMax = listOf(15.0),
            windDirectionDominant = listOf(180),
            uvIndexMax = listOf(6.0)
        ),
        dailyUnits = null
    )

    @Test
    fun `mapToWeatherData with Open-Meteo AirQualityDto maps pollen and air quality correctly`() {
        val airQualityDto = OpenMeteoAirQualityResponseDto(
            latitude = 52.52,
            longitude = 13.41,
            timezone = "Europe/Berlin",
            hourly = OpenMeteoAirQualityHourlyDto(
                time = listOf(
                    "2026-08-18T06:00",
                    "2026-08-18T12:00",
                    "2026-08-18T18:00"
                ),
                pm10 = listOf(15.0, 20.0, 18.0),
                pm25 = listOf(8.0, 12.0, 10.0),
                carbonMonoxide = listOf(250.0, 300.0, 280.0),
                nitrogenDioxide = listOf(15.0, 25.0, 20.0),
                sulphurDioxide = listOf(2.0, 4.0, 3.0),
                ozone = listOf(45.0, 65.0, 55.0),
                usAqi = listOf(35, 45, 40),
                europeanAqi = listOf(1, 2, 2),
                // Pollen values (grains/m³)
                grassPollen = listOf(5.0, 35.0, 20.0),      // Peak 35 -> Moderate (index 3)
                birchPollen = listOf(0.0, 150.0, 80.0),     // Peak 150 -> High (index 4)
                alderPollen = listOf(0.0, 0.0, 0.0),
                olivePollen = listOf(0.0, 0.0, 0.0),
                ragweedPollen = listOf(0.0, 5.0, 2.0),      // Peak 5 -> Low (index 2)
                mugwortPollen = listOf(0.0, 0.0, 0.0)
            )
        )

        val weatherData = mapper.mapToWeatherData(weatherResponse, location, airQualityDto)

        // Check Air Quality mapped on WeatherData
        assertNotNull(weatherData.airQuality)
        val aq = weatherData.airQuality!!
        assertEquals(12.0, aq.pm25, 0.1)
        assertEquals(20.0, aq.pm10, 0.1)
        assertEquals(65.0, aq.o3, 0.1)
        assertEquals(25.0, aq.no2, 0.1)
        assertEquals(4.0, aq.so2, 0.1)
        assertEquals(300.0, aq.co, 0.1)
        assertEquals(1, aq.usEpaIndex)

        // Check Pollen on DailyForecast
        assertEquals(1, weatherData.dailyForecasts.size)
        val daily = weatherData.dailyForecasts.first()
        assertEquals(LocalDate.of(2026, 8, 18), daily.date)
        assertNotNull(daily.pollen)

        val pollen = daily.pollen!!
        assertTrue(pollen.hasData)

        // Grass pollen
        assertNotNull(pollen.grassPollen)
        assertEquals(3, pollen.grassPollen?.indexValue)
        assertEquals("Moderate", pollen.grassPollen?.category)
        assertTrue(pollen.grassPollen?.inSeason == true)

        // Tree pollen (from Birch 150 grains/m³)
        assertNotNull(pollen.treePollen)
        assertEquals(4, pollen.treePollen?.indexValue)
        assertEquals("High", pollen.treePollen?.category)
        assertTrue(pollen.treePollen?.inSeason == true)

        // Weed pollen (from Ragweed 5 grains/m³)
        assertNotNull(pollen.weedPollen)
        assertEquals(2, pollen.weedPollen?.indexValue)
        assertEquals("Low", pollen.weedPollen?.category)
        assertTrue(pollen.weedPollen?.inSeason == true)

        // Overall peak index
        assertEquals(4, pollen.maxIndex)
        assertEquals("High", pollen.maxCategory)

        // Active plant allergens
        val dominantPlantNames = pollen.dominantPlants.map { it.displayName }
        assertTrue("Dominant plants should include Birch", dominantPlantNames.contains("Birch"))
        assertTrue("Dominant plants should include Grass", dominantPlantNames.contains("Grass"))
        assertTrue("Dominant plants should include Ragweed", dominantPlantNames.contains("Ragweed"))
    }

    @Test
    fun `mapToWeatherData with null AirQualityDto preserves null pollen and null air quality`() {
        val weatherData = mapper.mapToWeatherData(weatherResponse, location, airQualityResponse = null)

        assertNull(weatherData.airQuality)
        assertEquals(1, weatherData.dailyForecasts.size)
        assertNull(weatherData.dailyForecasts.first().pollen)
    }
}
