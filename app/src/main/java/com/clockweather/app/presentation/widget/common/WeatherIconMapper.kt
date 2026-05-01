package com.clockweather.app.presentation.widget.common

import com.clockweather.app.R
import com.clockweather.app.domain.model.WeatherCondition

object WeatherIconMapper {

    enum class IconStyle { GLASS_AI_GENERATED, GLASS_LAYERED, CLAY_3D, NEON_EDGE }

    fun fromPreferenceValue(value: String): IconStyle = when (value) {
        "glass_ai_generated" -> IconStyle.GLASS_AI_GENERATED
        "clay_3d" -> IconStyle.CLAY_3D
        "neon_edge" -> IconStyle.NEON_EDGE
        else -> IconStyle.GLASS_LAYERED
    }

    fun getDrawableResId(condition: WeatherCondition, style: IconStyle = IconStyle.GLASS_LAYERED): Int =
        when (style) {
            IconStyle.GLASS_AI_GENERATED -> getGlassAiDrawableResId(condition)
            IconStyle.GLASS_LAYERED -> getWidgetDrawableResId(condition)
            IconStyle.CLAY_3D -> getClayDrawableResId(condition)
            IconStyle.NEON_EDGE -> getNeonDrawableResId(condition)
        }

    private fun getGlassAiDrawableResId(condition: WeatherCondition): Int = when (condition) {
        WeatherCondition.CLEAR_DAY -> R.drawable.ic_weather_glass_ai_clear_day
        WeatherCondition.CLEAR_NIGHT -> R.drawable.ic_weather_glass_ai_clear_night
        WeatherCondition.MAINLY_CLEAR_DAY -> R.drawable.ic_weather_glass_ai_partly_cloudy_day
        WeatherCondition.MAINLY_CLEAR_NIGHT -> R.drawable.ic_weather_glass_ai_partly_cloudy_night
        WeatherCondition.PARTLY_CLOUDY_DAY -> R.drawable.ic_weather_glass_ai_partly_cloudy_day
        WeatherCondition.PARTLY_CLOUDY_NIGHT -> R.drawable.ic_weather_glass_ai_partly_cloudy_night
        WeatherCondition.OVERCAST -> R.drawable.ic_weather_glass_ai_cloudy
        WeatherCondition.FOG, WeatherCondition.DEPOSITING_RIME_FOG -> R.drawable.ic_weather_glass_ai_fog
        WeatherCondition.DRIZZLE_LIGHT, WeatherCondition.DRIZZLE_MODERATE, WeatherCondition.DRIZZLE_DENSE -> R.drawable.ic_weather_glass_ai_drizzle
        WeatherCondition.FREEZING_DRIZZLE_LIGHT, WeatherCondition.FREEZING_DRIZZLE_HEAVY -> R.drawable.ic_weather_glass_ai_drizzle
        WeatherCondition.RAIN_SLIGHT, WeatherCondition.RAIN_MODERATE, WeatherCondition.RAIN_HEAVY -> R.drawable.ic_weather_glass_ai_rain
        WeatherCondition.FREEZING_RAIN_LIGHT, WeatherCondition.FREEZING_RAIN_HEAVY -> R.drawable.ic_weather_glass_ai_rain
        WeatherCondition.SNOW_SLIGHT, WeatherCondition.SNOW_MODERATE, WeatherCondition.SNOW_HEAVY, WeatherCondition.SNOW_GRAINS -> R.drawable.ic_weather_glass_ai_snow
        WeatherCondition.RAIN_SHOWER_SLIGHT, WeatherCondition.RAIN_SHOWER_MODERATE, WeatherCondition.RAIN_SHOWER_VIOLENT -> R.drawable.ic_weather_glass_ai_rain
        WeatherCondition.SNOW_SHOWER_SLIGHT, WeatherCondition.SNOW_SHOWER_HEAVY -> R.drawable.ic_weather_glass_ai_snow
        WeatherCondition.THUNDERSTORM, WeatherCondition.THUNDERSTORM_SLIGHT_HAIL, WeatherCondition.THUNDERSTORM_HEAVY_HAIL -> R.drawable.ic_weather_glass_ai_thunderstorm
        WeatherCondition.UNKNOWN -> R.drawable.ic_weather_glass_ai_clear_day
    }

    private fun getClayDrawableResId(condition: WeatherCondition): Int = when (condition) {
        WeatherCondition.CLEAR_DAY -> R.drawable.ic_weather_clay_3d_clear_day
        WeatherCondition.CLEAR_NIGHT -> R.drawable.ic_weather_clay_3d_clear_night
        WeatherCondition.MAINLY_CLEAR_DAY -> R.drawable.ic_weather_clay_3d_partly_cloudy_day
        WeatherCondition.MAINLY_CLEAR_NIGHT -> R.drawable.ic_weather_clay_3d_partly_cloudy_night
        WeatherCondition.PARTLY_CLOUDY_DAY -> R.drawable.ic_weather_clay_3d_partly_cloudy_day
        WeatherCondition.PARTLY_CLOUDY_NIGHT -> R.drawable.ic_weather_clay_3d_partly_cloudy_night
        WeatherCondition.OVERCAST -> R.drawable.ic_weather_clay_3d_cloudy
        WeatherCondition.FOG, WeatherCondition.DEPOSITING_RIME_FOG -> R.drawable.ic_weather_clay_3d_fog
        WeatherCondition.DRIZZLE_LIGHT, WeatherCondition.DRIZZLE_MODERATE, WeatherCondition.DRIZZLE_DENSE -> R.drawable.ic_weather_clay_3d_drizzle
        WeatherCondition.FREEZING_DRIZZLE_LIGHT, WeatherCondition.FREEZING_DRIZZLE_HEAVY -> R.drawable.ic_weather_clay_3d_drizzle
        WeatherCondition.RAIN_SLIGHT, WeatherCondition.RAIN_MODERATE, WeatherCondition.RAIN_HEAVY -> R.drawable.ic_weather_clay_3d_rain
        WeatherCondition.FREEZING_RAIN_LIGHT, WeatherCondition.FREEZING_RAIN_HEAVY -> R.drawable.ic_weather_clay_3d_rain
        WeatherCondition.SNOW_SLIGHT, WeatherCondition.SNOW_MODERATE, WeatherCondition.SNOW_HEAVY, WeatherCondition.SNOW_GRAINS -> R.drawable.ic_weather_clay_3d_snow
        WeatherCondition.RAIN_SHOWER_SLIGHT, WeatherCondition.RAIN_SHOWER_MODERATE, WeatherCondition.RAIN_SHOWER_VIOLENT -> R.drawable.ic_weather_clay_3d_rain
        WeatherCondition.SNOW_SHOWER_SLIGHT, WeatherCondition.SNOW_SHOWER_HEAVY -> R.drawable.ic_weather_clay_3d_snow
        WeatherCondition.THUNDERSTORM, WeatherCondition.THUNDERSTORM_SLIGHT_HAIL, WeatherCondition.THUNDERSTORM_HEAVY_HAIL -> R.drawable.ic_weather_clay_3d_thunderstorm
        WeatherCondition.UNKNOWN -> R.drawable.ic_weather_clay_3d_clear_day
    }

    private fun getNeonDrawableResId(condition: WeatherCondition): Int = when (condition) {
        WeatherCondition.CLEAR_DAY -> R.drawable.ic_weather_neon_edge_clear_day
        WeatherCondition.CLEAR_NIGHT -> R.drawable.ic_weather_neon_edge_clear_night
        WeatherCondition.MAINLY_CLEAR_DAY -> R.drawable.ic_weather_neon_edge_partly_cloudy_day
        WeatherCondition.MAINLY_CLEAR_NIGHT -> R.drawable.ic_weather_neon_edge_partly_cloudy_night
        WeatherCondition.PARTLY_CLOUDY_DAY -> R.drawable.ic_weather_neon_edge_partly_cloudy_day
        WeatherCondition.PARTLY_CLOUDY_NIGHT -> R.drawable.ic_weather_neon_edge_partly_cloudy_night
        WeatherCondition.OVERCAST -> R.drawable.ic_weather_neon_edge_cloudy
        WeatherCondition.FOG, WeatherCondition.DEPOSITING_RIME_FOG -> R.drawable.ic_weather_neon_edge_fog
        WeatherCondition.DRIZZLE_LIGHT, WeatherCondition.DRIZZLE_MODERATE, WeatherCondition.DRIZZLE_DENSE -> R.drawable.ic_weather_neon_edge_drizzle
        WeatherCondition.FREEZING_DRIZZLE_LIGHT, WeatherCondition.FREEZING_DRIZZLE_HEAVY -> R.drawable.ic_weather_neon_edge_drizzle
        WeatherCondition.RAIN_SLIGHT, WeatherCondition.RAIN_MODERATE, WeatherCondition.RAIN_HEAVY -> R.drawable.ic_weather_neon_edge_rain
        WeatherCondition.FREEZING_RAIN_LIGHT, WeatherCondition.FREEZING_RAIN_HEAVY -> R.drawable.ic_weather_neon_edge_rain
        WeatherCondition.SNOW_SLIGHT, WeatherCondition.SNOW_MODERATE, WeatherCondition.SNOW_HEAVY, WeatherCondition.SNOW_GRAINS -> R.drawable.ic_weather_neon_edge_snow
        WeatherCondition.RAIN_SHOWER_SLIGHT, WeatherCondition.RAIN_SHOWER_MODERATE, WeatherCondition.RAIN_SHOWER_VIOLENT -> R.drawable.ic_weather_neon_edge_rain
        WeatherCondition.SNOW_SHOWER_SLIGHT, WeatherCondition.SNOW_SHOWER_HEAVY -> R.drawable.ic_weather_neon_edge_snow
        WeatherCondition.THUNDERSTORM, WeatherCondition.THUNDERSTORM_SLIGHT_HAIL, WeatherCondition.THUNDERSTORM_HEAVY_HAIL -> R.drawable.ic_weather_neon_edge_thunderstorm
        WeatherCondition.UNKNOWN -> R.drawable.ic_weather_neon_edge_clear_day
    }

    private fun getWidgetDrawableResId(condition: WeatherCondition): Int = when (condition) {
        WeatherCondition.CLEAR_DAY -> R.drawable.ic_widget_weather_clear_day
        WeatherCondition.CLEAR_NIGHT -> R.drawable.ic_widget_weather_clear_night
        WeatherCondition.MAINLY_CLEAR_DAY -> R.drawable.ic_widget_weather_partly_cloudy_day
        WeatherCondition.MAINLY_CLEAR_NIGHT -> R.drawable.ic_widget_weather_partly_cloudy_night
        WeatherCondition.PARTLY_CLOUDY_DAY -> R.drawable.ic_widget_weather_partly_cloudy_day
        WeatherCondition.PARTLY_CLOUDY_NIGHT -> R.drawable.ic_widget_weather_partly_cloudy_night
        WeatherCondition.OVERCAST -> R.drawable.ic_widget_weather_cloudy
        WeatherCondition.FOG, WeatherCondition.DEPOSITING_RIME_FOG -> R.drawable.ic_widget_weather_fog
        WeatherCondition.DRIZZLE_LIGHT, WeatherCondition.DRIZZLE_MODERATE, WeatherCondition.DRIZZLE_DENSE -> R.drawable.ic_widget_weather_drizzle
        WeatherCondition.FREEZING_DRIZZLE_LIGHT, WeatherCondition.FREEZING_DRIZZLE_HEAVY -> R.drawable.ic_widget_weather_drizzle
        WeatherCondition.RAIN_SLIGHT, WeatherCondition.RAIN_MODERATE, WeatherCondition.RAIN_HEAVY -> R.drawable.ic_widget_weather_rain
        WeatherCondition.FREEZING_RAIN_LIGHT, WeatherCondition.FREEZING_RAIN_HEAVY -> R.drawable.ic_widget_weather_rain
        WeatherCondition.SNOW_SLIGHT, WeatherCondition.SNOW_MODERATE, WeatherCondition.SNOW_HEAVY, WeatherCondition.SNOW_GRAINS -> R.drawable.ic_widget_weather_snow
        WeatherCondition.RAIN_SHOWER_SLIGHT, WeatherCondition.RAIN_SHOWER_MODERATE, WeatherCondition.RAIN_SHOWER_VIOLENT -> R.drawable.ic_widget_weather_rain
        WeatherCondition.SNOW_SHOWER_SLIGHT, WeatherCondition.SNOW_SHOWER_HEAVY -> R.drawable.ic_widget_weather_snow
        WeatherCondition.THUNDERSTORM, WeatherCondition.THUNDERSTORM_SLIGHT_HAIL, WeatherCondition.THUNDERSTORM_HEAVY_HAIL -> R.drawable.ic_widget_weather_thunderstorm
        WeatherCondition.UNKNOWN -> R.drawable.ic_widget_weather_clear_day
    }

    private fun getRegularDrawableResId(condition: WeatherCondition): Int = when (condition) {
        WeatherCondition.CLEAR_DAY -> R.drawable.ic_weather_clear_day
        WeatherCondition.CLEAR_NIGHT -> R.drawable.ic_weather_clear_night
        WeatherCondition.MAINLY_CLEAR_DAY -> R.drawable.ic_weather_partly_cloudy_day
        WeatherCondition.MAINLY_CLEAR_NIGHT -> R.drawable.ic_weather_partly_cloudy_night
        WeatherCondition.PARTLY_CLOUDY_DAY -> R.drawable.ic_weather_partly_cloudy_day
        WeatherCondition.PARTLY_CLOUDY_NIGHT -> R.drawable.ic_weather_partly_cloudy_night
        WeatherCondition.OVERCAST -> R.drawable.ic_weather_cloudy
        WeatherCondition.FOG, WeatherCondition.DEPOSITING_RIME_FOG -> R.drawable.ic_weather_fog
        WeatherCondition.DRIZZLE_LIGHT -> R.drawable.ic_weather_drizzle_light
        WeatherCondition.DRIZZLE_MODERATE -> R.drawable.ic_weather_drizzle
        WeatherCondition.DRIZZLE_DENSE -> R.drawable.ic_weather_drizzle_heavy
        WeatherCondition.FREEZING_DRIZZLE_LIGHT -> R.drawable.ic_weather_freezing_drizzle
        WeatherCondition.FREEZING_DRIZZLE_HEAVY -> R.drawable.ic_weather_freezing_drizzle
        WeatherCondition.RAIN_SLIGHT -> R.drawable.ic_weather_rain_light
        WeatherCondition.RAIN_MODERATE -> R.drawable.ic_weather_rain
        WeatherCondition.RAIN_HEAVY -> R.drawable.ic_weather_rain_heavy
        WeatherCondition.FREEZING_RAIN_LIGHT, WeatherCondition.FREEZING_RAIN_HEAVY -> R.drawable.ic_weather_freezing_rain
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
        WeatherCondition.THUNDERSTORM_SLIGHT_HAIL, WeatherCondition.THUNDERSTORM_HEAVY_HAIL -> R.drawable.ic_weather_thunderstorm_hail
        WeatherCondition.UNKNOWN -> R.drawable.ic_weather_clear_day
    }
}
