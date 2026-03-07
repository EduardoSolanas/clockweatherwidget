package com.clockweather.app.presentation.widget.common

import com.clockweather.app.R
import com.clockweather.app.domain.model.WeatherCondition

object WeatherIconMapper {

    fun getDrawableResId(condition: WeatherCondition): Int = when (condition) {
        WeatherCondition.CLEAR_DAY -> R.drawable.ic_weather_clear_day
        WeatherCondition.CLEAR_NIGHT -> R.drawable.ic_weather_clear_night
        WeatherCondition.MAINLY_CLEAR_DAY -> R.drawable.ic_weather_partly_cloudy_day
        WeatherCondition.MAINLY_CLEAR_NIGHT -> R.drawable.ic_weather_partly_cloudy_night
        WeatherCondition.PARTLY_CLOUDY_DAY -> R.drawable.ic_weather_partly_cloudy_day
        WeatherCondition.PARTLY_CLOUDY_NIGHT -> R.drawable.ic_weather_partly_cloudy_night
        WeatherCondition.OVERCAST -> R.drawable.ic_weather_cloudy
        WeatherCondition.FOG,
        WeatherCondition.DEPOSITING_RIME_FOG -> R.drawable.ic_weather_fog
        WeatherCondition.DRIZZLE_LIGHT -> R.drawable.ic_weather_drizzle_light
        WeatherCondition.DRIZZLE_MODERATE -> R.drawable.ic_weather_drizzle
        WeatherCondition.DRIZZLE_DENSE -> R.drawable.ic_weather_drizzle_heavy
        WeatherCondition.FREEZING_DRIZZLE_LIGHT,
        WeatherCondition.FREEZING_DRIZZLE_HEAVY -> R.drawable.ic_weather_freezing_drizzle
        WeatherCondition.RAIN_SLIGHT -> R.drawable.ic_weather_rain_light
        WeatherCondition.RAIN_MODERATE -> R.drawable.ic_weather_rain
        WeatherCondition.RAIN_HEAVY -> R.drawable.ic_weather_rain_heavy
        WeatherCondition.FREEZING_RAIN_LIGHT,
        WeatherCondition.FREEZING_RAIN_HEAVY -> R.drawable.ic_weather_freezing_rain
        WeatherCondition.SNOW_SLIGHT -> R.drawable.ic_weather_snow_light
        WeatherCondition.SNOW_MODERATE -> R.drawable.ic_weather_snow
        WeatherCondition.SNOW_HEAVY -> R.drawable.ic_weather_snow_heavy
        WeatherCondition.SNOW_GRAINS -> R.drawable.ic_weather_snow_grains
        WeatherCondition.RAIN_SHOWER_SLIGHT -> R.drawable.ic_weather_rain_shower_light
        WeatherCondition.RAIN_SHOWER_MODERATE -> R.drawable.ic_weather_rain_shower
        WeatherCondition.RAIN_SHOWER_VIOLENT -> R.drawable.ic_weather_rain_shower_heavy
        WeatherCondition.SNOW_SHOWER_SLIGHT -> R.drawable.ic_weather_snow_shower_light
        WeatherCondition.SNOW_SHOWER_HEAVY -> R.drawable.ic_weather_snow_shower
        WeatherCondition.THUNDERSTORM -> R.drawable.ic_weather_thunderstorm
        WeatherCondition.THUNDERSTORM_SLIGHT_HAIL,
        WeatherCondition.THUNDERSTORM_HEAVY_HAIL -> R.drawable.ic_weather_thunderstorm_hail
        WeatherCondition.UNKNOWN -> R.drawable.ic_weather_clear_day
    }
}

