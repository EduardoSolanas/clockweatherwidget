package com.clockweather.app.presentation.widget.common

import com.clockweather.app.domain.model.DailyForecast
import com.clockweather.app.domain.model.WeatherCondition
import java.time.LocalTime

/**
 * Maps weather conditions to their appropriate Day or Night visual variant
 * based on local reference time and the day's sunrise/sunset times.
 *
 * This runs purely on the display layer during widget redraws, ensuring that
 * condition icons switch to/from night variants immediately without waiting
 * for a network refresh or scheduling exact alarms.
 */
object LocalDayNightDisplayMapper {

    fun mapCondition(
        condition: WeatherCondition,
        currentTime: LocalTime,
        todayForecast: DailyForecast?
    ): WeatherCondition {
        val isDay = if (todayForecast != null && todayForecast.sunrise != todayForecast.sunset) {
            val sunrise = todayForecast.sunrise
            val sunset = todayForecast.sunset
            if (sunrise.isBefore(sunset)) {
                // Normal day: e.g. sunrise 06:30, sunset 19:45
                !currentTime.isBefore(sunrise) && currentTime.isBefore(sunset)
            } else {
                // Polar or wrap-around edge case
                !currentTime.isBefore(sunrise) || currentTime.isBefore(sunset)
            }
        } else {
            // Fallback: 6:00 AM to 8:00 PM is considered daytime
            !currentTime.isBefore(LocalTime.of(6, 0)) && currentTime.isBefore(LocalTime.of(20, 0))
        }

        return mapConditionForDayNight(condition, isDay)
    }

    fun mapConditionForDayNight(condition: WeatherCondition, isDay: Boolean): WeatherCondition {
        return when (condition) {
            WeatherCondition.CLEAR_DAY, WeatherCondition.CLEAR_NIGHT ->
                if (isDay) WeatherCondition.CLEAR_DAY else WeatherCondition.CLEAR_NIGHT
            WeatherCondition.MAINLY_CLEAR_DAY, WeatherCondition.MAINLY_CLEAR_NIGHT ->
                if (isDay) WeatherCondition.MAINLY_CLEAR_DAY else WeatherCondition.MAINLY_CLEAR_NIGHT
            WeatherCondition.PARTLY_CLOUDY_DAY, WeatherCondition.PARTLY_CLOUDY_NIGHT ->
                if (isDay) WeatherCondition.PARTLY_CLOUDY_DAY else WeatherCondition.PARTLY_CLOUDY_NIGHT
            else -> condition
        }
    }
}
