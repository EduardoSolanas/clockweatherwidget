package com.clockweather.app.domain.model
import com.clockweather.app.R

enum class WeatherCondition(
    val description: String,
    val iconResName: String,
    val isNightVariant: Boolean = false,
    @androidx.annotation.StringRes val labelResId: Int
) {
    CLEAR_DAY("Clear sky", "ic_weather_clear_day", labelResId = R.string.condition_clear_sky),
    CLEAR_NIGHT("Clear sky", "ic_weather_clear_night", true, labelResId = R.string.condition_clear_sky),
    MAINLY_CLEAR_DAY("Mainly clear", "ic_weather_partly_cloudy_day", labelResId = R.string.condition_mainly_clear),
    MAINLY_CLEAR_NIGHT("Mainly clear", "ic_weather_partly_cloudy_night", true, labelResId = R.string.condition_mainly_clear),
    PARTLY_CLOUDY_DAY("Partly cloudy", "ic_weather_partly_cloudy_day", labelResId = R.string.condition_partly_cloudy),
    PARTLY_CLOUDY_NIGHT("Partly cloudy", "ic_weather_partly_cloudy_night", true, labelResId = R.string.condition_partly_cloudy),
    OVERCAST("Overcast", "ic_weather_cloudy", labelResId = R.string.condition_overcast),
    FOG("Fog", "ic_weather_fog", labelResId = R.string.condition_fog),
    DEPOSITING_RIME_FOG("Depositing rime fog", "ic_weather_fog", labelResId = R.string.condition_rime_fog),
    DRIZZLE_LIGHT("Light drizzle", "ic_weather_drizzle_light", labelResId = R.string.condition_drizzle_light),
    DRIZZLE_MODERATE("Moderate drizzle", "ic_weather_drizzle", labelResId = R.string.condition_drizzle_moderate),
    DRIZZLE_DENSE("Dense drizzle", "ic_weather_drizzle_heavy", labelResId = R.string.condition_drizzle_dense),
    FREEZING_DRIZZLE_LIGHT("Light freezing drizzle", "ic_weather_freezing_drizzle", labelResId = R.string.condition_freezing_drizzle_light),
    FREEZING_DRIZZLE_HEAVY("Heavy freezing drizzle", "ic_weather_freezing_drizzle", labelResId = R.string.condition_freezing_drizzle_dense),
    RAIN_SLIGHT("Slight rain", "ic_weather_rain_light", labelResId = R.string.condition_rain_slight),
    RAIN_MODERATE("Moderate rain", "ic_weather_rain", labelResId = R.string.condition_rain_moderate),
    RAIN_HEAVY("Heavy rain", "ic_weather_rain_heavy", labelResId = R.string.condition_rain_heavy),
    FREEZING_RAIN_LIGHT("Light freezing rain", "ic_weather_freezing_rain", labelResId = R.string.condition_freezing_rain_light),
    FREEZING_RAIN_HEAVY("Heavy freezing rain", "ic_weather_freezing_rain", labelResId = R.string.condition_freezing_rain_heavy),
    SNOW_SLIGHT("Slight snowfall", "ic_weather_snow_light", labelResId = R.string.condition_snow_slight),
    SNOW_MODERATE("Moderate snowfall", "ic_weather_snow", labelResId = R.string.condition_snow_moderate),
    SNOW_HEAVY("Heavy snowfall", "ic_weather_snow_heavy", labelResId = R.string.condition_snow_heavy),
    SNOW_GRAINS("Snow grains", "ic_weather_snow_grains", labelResId = R.string.condition_snow_grains),
    RAIN_SHOWER_SLIGHT("Slight rain shower", "ic_weather_rain_shower_light", labelResId = R.string.condition_rain_showers_slight),
    RAIN_SHOWER_MODERATE("Moderate rain shower", "ic_weather_rain_shower", labelResId = R.string.condition_rain_showers_moderate),
    RAIN_SHOWER_VIOLENT("Violent rain shower", "ic_weather_rain_shower_heavy", labelResId = R.string.condition_rain_showers_violent),
    SNOW_SHOWER_SLIGHT("Slight snow shower", "ic_weather_snow_shower_light", labelResId = R.string.condition_snow_showers_slight),
    SNOW_SHOWER_HEAVY("Heavy snow shower", "ic_weather_snow_shower", labelResId = R.string.condition_snow_showers_heavy),
    THUNDERSTORM("Thunderstorm", "ic_weather_thunderstorm", labelResId = R.string.condition_thunderstorm),
    THUNDERSTORM_SLIGHT_HAIL("Thunderstorm with slight hail", "ic_weather_thunderstorm_hail", labelResId = R.string.condition_thunderstorm_hail),
    THUNDERSTORM_HEAVY_HAIL("Thunderstorm with heavy hail", "ic_weather_thunderstorm_hail", labelResId = R.string.condition_thunderstorm_hail_heavy),
    UNKNOWN("Unknown", "ic_weather_clear_day", labelResId = R.string.condition_clear_sky);

    /** Convert back to WMO weather code for database storage */
    fun toCode(): Int = when (this) {
        CLEAR_DAY, CLEAR_NIGHT -> 0
        MAINLY_CLEAR_DAY, MAINLY_CLEAR_NIGHT -> 1
        PARTLY_CLOUDY_DAY, PARTLY_CLOUDY_NIGHT -> 2
        OVERCAST -> 3
        FOG -> 45
        DEPOSITING_RIME_FOG -> 48
        DRIZZLE_LIGHT -> 51
        DRIZZLE_MODERATE -> 53
        DRIZZLE_DENSE -> 55
        FREEZING_DRIZZLE_LIGHT -> 56
        FREEZING_DRIZZLE_HEAVY -> 57
        RAIN_SLIGHT -> 61
        RAIN_MODERATE -> 63
        RAIN_HEAVY -> 65
        FREEZING_RAIN_LIGHT -> 66
        FREEZING_RAIN_HEAVY -> 67
        SNOW_SLIGHT -> 71
        SNOW_MODERATE -> 73
        SNOW_HEAVY -> 75
        SNOW_GRAINS -> 77
        RAIN_SHOWER_SLIGHT -> 80
        RAIN_SHOWER_MODERATE -> 81
        RAIN_SHOWER_VIOLENT -> 82
        SNOW_SHOWER_SLIGHT -> 85
        SNOW_SHOWER_HEAVY -> 86
        THUNDERSTORM -> 95
        THUNDERSTORM_SLIGHT_HAIL -> 96
        THUNDERSTORM_HEAVY_HAIL -> 99
        UNKNOWN -> -1
    }

    companion object {
        fun fromCode(code: Int, isDay: Boolean = true): WeatherCondition = when (code) {
            0 -> if (isDay) CLEAR_DAY else CLEAR_NIGHT
            1 -> if (isDay) MAINLY_CLEAR_DAY else MAINLY_CLEAR_NIGHT
            2 -> if (isDay) PARTLY_CLOUDY_DAY else PARTLY_CLOUDY_NIGHT
            3 -> OVERCAST
            45 -> FOG
            48 -> DEPOSITING_RIME_FOG
            51 -> DRIZZLE_LIGHT
            53 -> DRIZZLE_MODERATE
            55 -> DRIZZLE_DENSE
            56 -> FREEZING_DRIZZLE_LIGHT
            57 -> FREEZING_DRIZZLE_HEAVY
            61 -> RAIN_SLIGHT
            63 -> RAIN_MODERATE
            65 -> RAIN_HEAVY
            66 -> FREEZING_RAIN_LIGHT
            67 -> FREEZING_RAIN_HEAVY
            71 -> SNOW_SLIGHT
            73 -> SNOW_MODERATE
            75 -> SNOW_HEAVY
            77 -> SNOW_GRAINS
            80 -> RAIN_SHOWER_SLIGHT
            81 -> RAIN_SHOWER_MODERATE
            82 -> RAIN_SHOWER_VIOLENT
            85 -> SNOW_SHOWER_SLIGHT
            86 -> SNOW_SHOWER_HEAVY
            95 -> THUNDERSTORM
            96 -> THUNDERSTORM_SLIGHT_HAIL
            99 -> THUNDERSTORM_HEAVY_HAIL
            else -> UNKNOWN
        }

        /** Map WeatherAPI.com condition codes → domain WeatherCondition */
        fun fromWeatherApiCode(code: Int, isDay: Boolean = true): WeatherCondition = when (code) {
            1000 -> if (isDay) CLEAR_DAY else CLEAR_NIGHT
            1003 -> if (isDay) PARTLY_CLOUDY_DAY else PARTLY_CLOUDY_NIGHT
            1006 -> OVERCAST
            1009 -> OVERCAST
            1030, 1135 -> FOG
            1147 -> DEPOSITING_RIME_FOG
            1063, 1180, 1183 -> RAIN_SLIGHT
            1186, 1189 -> RAIN_MODERATE
            1192, 1195 -> RAIN_HEAVY
            1072, 1168 -> FREEZING_DRIZZLE_LIGHT
            1171 -> FREEZING_DRIZZLE_HEAVY
            1150, 1153 -> DRIZZLE_LIGHT
            1159 -> DRIZZLE_MODERATE
            1162 -> DRIZZLE_DENSE
            1198 -> FREEZING_RAIN_LIGHT
            1201 -> FREEZING_RAIN_HEAVY
            1066, 1210, 1213 -> SNOW_SLIGHT
            1216, 1219 -> SNOW_MODERATE
            1222, 1225 -> SNOW_HEAVY
            1069, 1204, 1207 -> SNOW_GRAINS
            1114, 1117 -> SNOW_HEAVY
            1240 -> RAIN_SHOWER_SLIGHT
            1243 -> RAIN_SHOWER_MODERATE
            1246 -> RAIN_SHOWER_VIOLENT
            1255 -> SNOW_SHOWER_SLIGHT
            1258 -> SNOW_SHOWER_HEAVY
            1087, 1273, 1276 -> THUNDERSTORM
            1279, 1282 -> THUNDERSTORM_HEAVY_HAIL
            else -> if (isDay) MAINLY_CLEAR_DAY else MAINLY_CLEAR_NIGHT
        }
    }
}

