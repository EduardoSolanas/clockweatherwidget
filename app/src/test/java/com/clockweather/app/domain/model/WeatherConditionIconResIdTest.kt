package com.clockweather.app.domain.model

import com.clockweather.app.R
import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherConditionIconResIdTest {

    @Test
    fun `iconResId uses simplified widget icon set for all conditions`() {
        val expected = mapOf(
            WeatherCondition.CLEAR_DAY to R.drawable.ic_widget_weather_clear_day,
            WeatherCondition.CLEAR_NIGHT to R.drawable.ic_widget_weather_clear_night,
            WeatherCondition.MAINLY_CLEAR_DAY to R.drawable.ic_widget_weather_partly_cloudy_day,
            WeatherCondition.MAINLY_CLEAR_NIGHT to R.drawable.ic_widget_weather_partly_cloudy_night,
            WeatherCondition.PARTLY_CLOUDY_DAY to R.drawable.ic_widget_weather_partly_cloudy_day,
            WeatherCondition.PARTLY_CLOUDY_NIGHT to R.drawable.ic_widget_weather_partly_cloudy_night,
            WeatherCondition.OVERCAST to R.drawable.ic_widget_weather_cloudy,
            WeatherCondition.FOG to R.drawable.ic_widget_weather_fog,
            WeatherCondition.DEPOSITING_RIME_FOG to R.drawable.ic_widget_weather_fog,
            WeatherCondition.DRIZZLE_LIGHT to R.drawable.ic_widget_weather_drizzle,
            WeatherCondition.DRIZZLE_MODERATE to R.drawable.ic_widget_weather_drizzle,
            WeatherCondition.DRIZZLE_DENSE to R.drawable.ic_widget_weather_drizzle,
            WeatherCondition.FREEZING_DRIZZLE_LIGHT to R.drawable.ic_widget_weather_drizzle,
            WeatherCondition.FREEZING_DRIZZLE_HEAVY to R.drawable.ic_widget_weather_drizzle,
            WeatherCondition.RAIN_SLIGHT to R.drawable.ic_widget_weather_rain,
            WeatherCondition.RAIN_MODERATE to R.drawable.ic_widget_weather_rain,
            WeatherCondition.RAIN_HEAVY to R.drawable.ic_widget_weather_rain,
            WeatherCondition.FREEZING_RAIN_LIGHT to R.drawable.ic_widget_weather_rain,
            WeatherCondition.FREEZING_RAIN_HEAVY to R.drawable.ic_widget_weather_rain,
            WeatherCondition.SNOW_SLIGHT to R.drawable.ic_widget_weather_snow,
            WeatherCondition.SNOW_MODERATE to R.drawable.ic_widget_weather_snow,
            WeatherCondition.SNOW_HEAVY to R.drawable.ic_widget_weather_snow,
            WeatherCondition.SNOW_GRAINS to R.drawable.ic_widget_weather_snow,
            WeatherCondition.RAIN_SHOWER_SLIGHT to R.drawable.ic_widget_weather_rain,
            WeatherCondition.RAIN_SHOWER_MODERATE to R.drawable.ic_widget_weather_rain,
            WeatherCondition.RAIN_SHOWER_VIOLENT to R.drawable.ic_widget_weather_rain,
            WeatherCondition.SNOW_SHOWER_SLIGHT to R.drawable.ic_widget_weather_snow,
            WeatherCondition.SNOW_SHOWER_HEAVY to R.drawable.ic_widget_weather_snow,
            WeatherCondition.THUNDERSTORM to R.drawable.ic_widget_weather_thunderstorm,
            WeatherCondition.THUNDERSTORM_SLIGHT_HAIL to R.drawable.ic_widget_weather_thunderstorm,
            WeatherCondition.THUNDERSTORM_HEAVY_HAIL to R.drawable.ic_widget_weather_thunderstorm,
            WeatherCondition.UNKNOWN to R.drawable.ic_widget_weather_clear_day,
        )

        assertEquals(
            "Test must cover all WeatherCondition values",
            WeatherCondition.entries.size,
            expected.size
        )

        expected.forEach { (condition, expectedIcon) ->
            assertEquals(
                "iconResId mismatch for $condition",
                expectedIcon,
                condition.iconResId
            )
        }
    }
}
