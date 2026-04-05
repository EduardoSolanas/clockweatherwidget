package com.clockweather.app.data.provider

import com.clockweather.app.domain.model.WeatherProviderType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeatherDataProviderFactory @Inject constructor(
    private val openMeteoWeatherProvider: OpenMeteoWeatherProvider,
    private val googleWeatherProvider: GoogleWeatherProvider,
    private val weatherApiProvider: WeatherApiProvider
) {
    fun get(providerType: WeatherProviderType): WeatherDataProvider = when (providerType) {
        WeatherProviderType.OPEN_METEO -> openMeteoWeatherProvider
        WeatherProviderType.GOOGLE -> googleWeatherProvider
        WeatherProviderType.WEATHER_API -> weatherApiProvider
    }
}

