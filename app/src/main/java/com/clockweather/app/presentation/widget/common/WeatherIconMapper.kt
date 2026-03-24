package com.clockweather.app.presentation.widget.common

import com.clockweather.app.R
import com.clockweather.app.domain.model.WeatherCondition

object WeatherIconMapper {

    fun getDrawableResId(condition: WeatherCondition): Int = when (condition) {
        WeatherCondition.CLEAR_DAY -> R.drawable.ic_widget_weather_clear_day
        WeatherCondition.CLEAR_NIGHT -> R.drawable.ic_widget_weather_clear_night
        WeatherCondition.MAINLY_CLEAR_DAY -> R.drawable.ic_widget_weather_partly_cloudy_day
        WeatherCondition.MAINLY_CLEAR_NIGHT -> R.drawable.ic_widget_weather_partly_cloudy_night
        WeatherCondition.PARTLY_CLOUDY_DAY -> R.drawable.ic_widget_weather_partly_cloudy_day
        WeatherCondition.PARTLY_CLOUDY_NIGHT -> R.drawable.ic_widget_weather_partly_cloudy_night
        WeatherCondition.OVERCAST -> R.drawable.ic_widget_weather_cloudy
        WeatherCondition.FOG,
        WeatherCondition.DEPOSITING_RIME_FOG -> R.drawable.ic_widget_weather_fog
        WeatherCondition.DRIZZLE_LIGHT -> R.drawable.ic_widget_weather_drizzle
        WeatherCondition.DRIZZLE_MODERATE -> R.drawable.ic_widget_weather_drizzle
        WeatherCondition.DRIZZLE_DENSE -> R.drawable.ic_widget_weather_drizzle
        WeatherCondition.FREEZING_DRIZZLE_LIGHT,
        WeatherCondition.FREEZING_DRIZZLE_HEAVY -> R.drawable.ic_widget_weather_drizzle
        WeatherCondition.RAIN_SLIGHT -> R.drawable.ic_widget_weather_rain
        WeatherCondition.RAIN_MODERATE -> R.drawable.ic_widget_weather_rain
        WeatherCondition.RAIN_HEAVY -> R.drawable.ic_widget_weather_rain
        WeatherCondition.FREEZING_RAIN_LIGHT,
        WeatherCondition.FREEZING_RAIN_HEAVY -> R.drawable.ic_widget_weather_rain
        WeatherCondition.SNOW_SLIGHT -> R.drawable.ic_widget_weather_snow
        WeatherCondition.SNOW_MODERATE -> R.drawable.ic_widget_weather_snow
        WeatherCondition.SNOW_HEAVY -> R.drawable.ic_widget_weather_snow
        WeatherCondition.SNOW_GRAINS -> R.drawable.ic_widget_weather_snow
        WeatherCondition.RAIN_SHOWER_SLIGHT -> R.drawable.ic_widget_weather_rain
        WeatherCondition.RAIN_SHOWER_MODERATE -> R.drawable.ic_widget_weather_rain
        WeatherCondition.RAIN_SHOWER_VIOLENT -> R.drawable.ic_widget_weather_rain
        WeatherCondition.SNOW_SHOWER_SLIGHT -> R.drawable.ic_widget_weather_snow
        WeatherCondition.SNOW_SHOWER_HEAVY -> R.drawable.ic_widget_weather_snow
        WeatherCondition.THUNDERSTORM -> R.drawable.ic_widget_weather_thunderstorm
        WeatherCondition.THUNDERSTORM_SLIGHT_HAIL,
        WeatherCondition.THUNDERSTORM_HEAVY_HAIL -> R.drawable.ic_widget_weather_thunderstorm
        WeatherCondition.UNKNOWN -> R.drawable.ic_widget_weather_clear_day
    }
}

