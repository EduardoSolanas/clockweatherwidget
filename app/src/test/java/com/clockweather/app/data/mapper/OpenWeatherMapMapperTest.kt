package com.clockweather.app.data.mapper

import com.clockweather.app.data.remote.dto.openweathermap.OpenWeatherMapConditionDto
import com.clockweather.app.data.remote.dto.openweathermap.OpenWeatherMapCurrentDto
import com.clockweather.app.data.remote.dto.openweathermap.OpenWeatherMapDailyDto
import com.clockweather.app.data.remote.dto.openweathermap.OpenWeatherMapDailyFeelsLikeDto
import com.clockweather.app.data.remote.dto.openweathermap.OpenWeatherMapDailyTempDto
import com.clockweather.app.data.remote.dto.openweathermap.OpenWeatherMapHourlyDto
import com.clockweather.app.data.remote.dto.openweathermap.OpenWeatherMapOneCallResponseDto
import com.clockweather.app.data.remote.dto.openweathermap.OpenWeatherMapPrecipitationDto
import com.clockweather.app.domain.model.Location
import com.clockweather.app.domain.model.WeatherCondition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class OpenWeatherMapMapperTest {

    private val mapper = OpenWeatherMapMapper()

    @Test
    fun `daily forecast uses daily aggregate weather condition`() {
        val location = Location(
            id = 1L,
            name = "Test City",
            country = "Test",
            latitude = 0.0,
            longitude = 0.0
        )

        val dayTimestamp = 1767158400L

        val dailyCondition = OpenWeatherMapConditionDto(
            id = 803,
            main = "Clouds",
            description = "broken clouds",
            icon = "04d"
        )

        val response = OpenWeatherMapOneCallResponseDto(
            lat = 0.0,
            lon = 0.0,
            timezone = "UTC",
            timezoneOffset = 0,
            current = OpenWeatherMapCurrentDto(
                dt = dayTimestamp,
                temp = 18.0,
                feelsLike = 17.0,
                humidity = 60,
                dewPoint = 6.0,
                pressure = 1013,
                clouds = 80,
                uvi = 3.0,
                visibility = 10000,
                windSpeed = 12.0,
                windDeg = 180,
                windGust = 15.0,
                weather = listOf(dailyCondition),
                rain = null,
                snow = null,
                sunrise = dayTimestamp + 1800,
                sunset = dayTimestamp + 10800
            ),
            hourly = listOf(
                OpenWeatherMapHourlyDto(
                    dt = dayTimestamp,
                    temp = 18.0,
                    feelsLike = 17.0,
                    humidity = 60,
                    dewPoint = 6.0,
                    pressure = 1013,
                    clouds = 0,
                    uvi = 3.0,
                    visibility = 10000,
                    windSpeed = 12.0,
                    windDeg = 180,
                    windGust = 15.0,
                    pop = 0.1,
                    weather = listOf(
                        OpenWeatherMapConditionDto(
                            id = 800,
                            main = "Clear",
                            description = "clear sky",
                            icon = "01d"
                        )
                    )
                )
            ),
            daily = listOf(
                OpenWeatherMapDailyDto(
                    dt = dayTimestamp,
                    sunrise = dayTimestamp + 1800,
                    sunset = dayTimestamp + 10800,
                    temp = OpenWeatherMapDailyTempDto(
                        day = 18.0,
                        min = 10.0,
                        max = 20.0,
                        night = 11.0,
                        eve = 15.0,
                        morn = 12.0
                    ),
                    feelsLike = OpenWeatherMapDailyFeelsLikeDto(
                        day = 17.0,
                        night = 9.0,
                        eve = 14.0,
                        morn = 10.0
                    ),
                    humidity = 60,
                    dewPoint = 6.0,
                    pressure = 1013,
                    clouds = 80,
                    uvi = 3.0,
                    windSpeed = 12.0,
                    windDeg = 180,
                    windGust = 15.0,
                    pop = 0.8,
                    weather = listOf(dailyCondition),
                    rain = 5.0,
                    snow = null
                )
            )
        )

        val weatherData = mapper.mapToWeatherData(response, location)

        assertEquals(1, weatherData.dailyForecasts.size)
        assertNotNull(weatherData.dailyForecasts[0])

        val forecast = weatherData.dailyForecasts[0]
        assertEquals(WeatherCondition.OVERCAST, forecast.weatherCondition)
        assertEquals(20.0, forecast.temperatureMax, 0.01)
        assertEquals(10.0, forecast.temperatureMin, 0.01)
    }
}
