package com.clockweather.app.presentation.widget.common

import com.clockweather.app.R
import com.clockweather.app.domain.model.WeatherCondition
import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherIconMapperTest {

    @Test
    fun `premium widget icon mapping uses widget drawable family`() {
        assertEquals(R.drawable.ic_widget_weather_clear_day, WeatherIconMapper.getDrawableResId(WeatherCondition.CLEAR_DAY))
        assertEquals(R.drawable.ic_widget_weather_clear_night, WeatherIconMapper.getDrawableResId(WeatherCondition.CLEAR_NIGHT))
        assertEquals(R.drawable.ic_widget_weather_partly_cloudy_day, WeatherIconMapper.getDrawableResId(WeatherCondition.PARTLY_CLOUDY_DAY))
        assertEquals(R.drawable.ic_widget_weather_partly_cloudy_night, WeatherIconMapper.getDrawableResId(WeatherCondition.PARTLY_CLOUDY_NIGHT))
        assertEquals(R.drawable.ic_widget_weather_cloudy, WeatherIconMapper.getDrawableResId(WeatherCondition.OVERCAST))
        assertEquals(R.drawable.ic_widget_weather_drizzle, WeatherIconMapper.getDrawableResId(WeatherCondition.DRIZZLE_MODERATE))
        assertEquals(R.drawable.ic_widget_weather_rain, WeatherIconMapper.getDrawableResId(WeatherCondition.RAIN_MODERATE))
        assertEquals(R.drawable.ic_widget_weather_snow, WeatherIconMapper.getDrawableResId(WeatherCondition.SNOW_MODERATE))
        assertEquals(R.drawable.ic_widget_weather_thunderstorm, WeatherIconMapper.getDrawableResId(WeatherCondition.THUNDERSTORM))
        assertEquals(R.drawable.ic_widget_weather_fog, WeatherIconMapper.getDrawableResId(WeatherCondition.FOG))
    }

    @Test
    fun `clay icon style maps to clay drawable family`() {
        assertEquals(
            R.drawable.ic_weather_clay_3d_clear_day,
            WeatherIconMapper.getDrawableResId(WeatherCondition.CLEAR_DAY, WeatherIconMapper.IconStyle.CLAY_3D)
        )
        assertEquals(
            R.drawable.ic_weather_clay_3d_partly_cloudy_night,
            WeatherIconMapper.getDrawableResId(WeatherCondition.PARTLY_CLOUDY_NIGHT, WeatherIconMapper.IconStyle.CLAY_3D)
        )
        assertEquals(
            R.drawable.ic_weather_clay_3d_rain,
            WeatherIconMapper.getDrawableResId(WeatherCondition.RAIN_HEAVY, WeatherIconMapper.IconStyle.CLAY_3D)
        )
        assertEquals(
            R.drawable.ic_weather_clay_3d_thunderstorm,
            WeatherIconMapper.getDrawableResId(WeatherCondition.THUNDERSTORM_HEAVY_HAIL, WeatherIconMapper.IconStyle.CLAY_3D)
        )
    }

    @Test
    fun `neon icon style maps to neon drawable family`() {
        assertEquals(
            R.drawable.ic_weather_neon_edge_clear_night,
            WeatherIconMapper.getDrawableResId(WeatherCondition.CLEAR_NIGHT, WeatherIconMapper.IconStyle.NEON_EDGE)
        )
        assertEquals(
            R.drawable.ic_weather_neon_edge_cloudy,
            WeatherIconMapper.getDrawableResId(WeatherCondition.OVERCAST, WeatherIconMapper.IconStyle.NEON_EDGE)
        )
        assertEquals(
            R.drawable.ic_weather_neon_edge_snow,
            WeatherIconMapper.getDrawableResId(WeatherCondition.SNOW_GRAINS, WeatherIconMapper.IconStyle.NEON_EDGE)
        )
        assertEquals(
            R.drawable.ic_weather_neon_edge_fog,
            WeatherIconMapper.getDrawableResId(WeatherCondition.DEPOSITING_RIME_FOG, WeatherIconMapper.IconStyle.NEON_EDGE)
        )
    }
}
