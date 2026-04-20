package com.clockweather.app.domain.model
import com.clockweather.app.R

enum class WeatherCondition(
    val description: String,
    @androidx.annotation.DrawableRes val iconResId: Int,
    val isNightVariant: Boolean = false,
    @androidx.annotation.StringRes val labelResId: Int
) {
    CLEAR_DAY("Clear sky", R.drawable.ic_weather_clear_day, labelResId = R.string.condition_clear_sky),
    CLEAR_NIGHT("Clear sky", R.drawable.ic_weather_clear_night, true, labelResId = R.string.condition_clear_sky),
    MAINLY_CLEAR_DAY("Mainly clear", R.drawable.ic_weather_partly_cloudy_day, labelResId = R.string.condition_mainly_clear),
    MAINLY_CLEAR_NIGHT("Mainly clear", R.drawable.ic_weather_partly_cloudy_night, true, labelResId = R.string.condition_mainly_clear),
    PARTLY_CLOUDY_DAY("Partly cloudy", R.drawable.ic_weather_partly_cloudy_day, labelResId = R.string.condition_partly_cloudy),
    PARTLY_CLOUDY_NIGHT("Partly cloudy", R.drawable.ic_weather_partly_cloudy_night, true, labelResId = R.string.condition_partly_cloudy),
    OVERCAST("Overcast", R.drawable.ic_weather_cloudy, labelResId = R.string.condition_overcast),
    FOG("Fog", R.drawable.ic_weather_fog, labelResId = R.string.condition_fog),
    DEPOSITING_RIME_FOG("Depositing rime fog", R.drawable.ic_weather_fog, labelResId = R.string.condition_rime_fog),
    DRIZZLE_LIGHT("Light drizzle", R.drawable.ic_weather_drizzle_light, labelResId = R.string.condition_drizzle_light),
    DRIZZLE_MODERATE("Moderate drizzle", R.drawable.ic_weather_drizzle, labelResId = R.string.condition_drizzle_moderate),
    DRIZZLE_DENSE("Dense drizzle", R.drawable.ic_weather_drizzle_heavy, labelResId = R.string.condition_drizzle_dense),
    FREEZING_DRIZZLE_LIGHT("Light freezing drizzle", R.drawable.ic_weather_freezing_drizzle, labelResId = R.string.condition_freezing_drizzle_light),
    FREEZING_DRIZZLE_HEAVY("Heavy freezing drizzle", R.drawable.ic_weather_freezing_drizzle, labelResId = R.string.condition_freezing_drizzle_dense),
    RAIN_SLIGHT("Slight rain", R.drawable.ic_weather_rain_light, labelResId = R.string.condition_rain_slight),
    RAIN_MODERATE("Moderate rain", R.drawable.ic_weather_rain, labelResId = R.string.condition_rain_moderate),
    RAIN_HEAVY("Heavy rain", R.drawable.ic_weather_rain_heavy, labelResId = R.string.condition_rain_heavy),
    FREEZING_RAIN_LIGHT("Light freezing rain", R.drawable.ic_weather_freezing_rain, labelResId = R.string.condition_freezing_rain_light),
    FREEZING_RAIN_HEAVY("Heavy freezing rain", R.drawable.ic_weather_freezing_rain, labelResId = R.string.condition_freezing_rain_heavy),
    SNOW_SLIGHT("Slight snowfall", R.drawable.ic_weather_snow_light, labelResId = R.string.condition_snow_slight),
    SNOW_MODERATE("Moderate snowfall", R.drawable.ic_weather_snow, labelResId = R.string.condition_snow_moderate),
    SNOW_HEAVY("Heavy snowfall", R.drawable.ic_weather_snow_heavy, labelResId = R.string.condition_snow_heavy),
    SNOW_GRAINS("Snow grains", R.drawable.ic_weather_snow_grains, labelResId = R.string.condition_snow_grains),
    RAIN_SHOWER_SLIGHT("Slight rain shower", R.drawable.ic_weather_rain_shower_light, labelResId = R.string.condition_rain_showers_slight),
    RAIN_SHOWER_MODERATE("Moderate rain shower", R.drawable.ic_weather_rain_shower, labelResId = R.string.condition_rain_showers_moderate),
    RAIN_SHOWER_VIOLENT("Violent rain shower", R.drawable.ic_weather_rain_shower_heavy, labelResId = R.string.condition_rain_showers_violent),
    SNOW_SHOWER_SLIGHT("Slight snow shower", R.drawable.ic_weather_snow_shower_light, labelResId = R.string.condition_snow_showers_slight),
    SNOW_SHOWER_HEAVY("Heavy snow shower", R.drawable.ic_weather_snow_shower, labelResId = R.string.condition_snow_showers_heavy),
    THUNDERSTORM("Thunderstorm", R.drawable.ic_weather_thunderstorm, labelResId = R.string.condition_thunderstorm),
    THUNDERSTORM_SLIGHT_HAIL("Thunderstorm with slight hail", R.drawable.ic_weather_thunderstorm_hail, labelResId = R.string.condition_thunderstorm_hail),
    THUNDERSTORM_HEAVY_HAIL("Thunderstorm with heavy hail", R.drawable.ic_weather_thunderstorm_hail, labelResId = R.string.condition_thunderstorm_hail_heavy),
    UNKNOWN("Unknown", R.drawable.ic_weather_clear_day, labelResId = R.string.condition_clear_sky);

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

        /** Map Google Weather API condition type strings → domain WeatherCondition */
        fun fromGoogleWeatherType(type: String, isDay: Boolean = true): WeatherCondition = when (type) {
            "CLEAR"                                              -> if (isDay) CLEAR_DAY else CLEAR_NIGHT
            "MOSTLY_CLEAR"                                       -> if (isDay) MAINLY_CLEAR_DAY else MAINLY_CLEAR_NIGHT
            "PARTLY_CLOUDY"                                      -> if (isDay) PARTLY_CLOUDY_DAY else PARTLY_CLOUDY_NIGHT
            "MOSTLY_CLOUDY", "CLOUDY"                            -> OVERCAST
            "WINDY"                                              -> if (isDay) MAINLY_CLEAR_DAY else MAINLY_CLEAR_NIGHT
            "WIND_AND_RAIN"                                      -> RAIN_MODERATE
            "LIGHT_RAIN"                                         -> RAIN_SLIGHT
            "RAIN"                                               -> RAIN_MODERATE
            "HEAVY_RAIN"                                         -> RAIN_HEAVY
            "LIGHT_RAIN_SHOWERS", "RAIN_SHOWERS"                 -> RAIN_SHOWER_SLIGHT
            "HEAVY_RAIN_SHOWERS"                                 -> RAIN_SHOWER_VIOLENT
            "LIGHT_DRIZZLE", "DRIZZLE"                           -> DRIZZLE_LIGHT
            "HEAVY_DRIZZLE"                                      -> DRIZZLE_DENSE
            "LIGHT_FREEZING_DRIZZLE", "FREEZING_DRIZZLE"         -> FREEZING_DRIZZLE_LIGHT
            "LIGHT_FREEZING_RAIN"                                -> FREEZING_DRIZZLE_HEAVY
            "FREEZING_RAIN"                                      -> FREEZING_RAIN_LIGHT
            "LIGHT_SNOW", "SNOW_FLURRIES", "FLURRIES"            -> SNOW_SLIGHT
            "SNOW"                                               -> SNOW_MODERATE
            "HEAVY_SNOW", "BLOWING_SNOW", "BLIZZARD"             -> SNOW_HEAVY
            "WINTRY_MIX", "MIXED_PRECIPITATION",
            "ICE_PELLETS", "LIGHT_ICE_PELLETS",
            "HEAVY_ICE_PELLETS", "HAIL", "ICE_CRYSTALS"         -> SNOW_GRAINS
            "LIGHT_SNOW_SHOWERS"                                 -> SNOW_SHOWER_SLIGHT
            "SNOW_SHOWERS", "HEAVY_SNOW_SHOWERS"                 -> SNOW_SHOWER_HEAVY
            "THUNDERSTORM", "LIGHT_THUNDERSTORM",
            "HEAVY_THUNDERSTORM"                                 -> THUNDERSTORM
            "THUNDERSTORM_WITH_HAIL", "HAIL_THUNDERSTORM"        -> THUNDERSTORM_HEAVY_HAIL
            "FOG", "LIGHT_FOG", "ICE_FOG"                        -> FOG
            "HAZE", "SMOKE", "DUST", "SAND_STORM"               -> DEPOSITING_RIME_FOG
            "TROPICAL_STORM", "HURRICANE", "TORNADO"             -> THUNDERSTORM_HEAVY_HAIL
            else                                                 -> if (isDay) MAINLY_CLEAR_DAY else MAINLY_CLEAR_NIGHT
        }

        /**
         * Map OpenWeatherMap condition IDs → domain WeatherCondition.
         * Reference: https://openweathermap.org/weather-conditions
         */
        fun fromOpenWeatherMapId(id: Int, isDay: Boolean = true): WeatherCondition = when (id) {
            // Thunderstorm 2xx
            200, 201, 210, 230, 231 -> THUNDERSTORM
            202, 211, 212, 221, 232 -> THUNDERSTORM
            // Drizzle 3xx
            300, 310, 313 -> DRIZZLE_LIGHT
            301, 311, 321 -> DRIZZLE_MODERATE
            302, 312, 314 -> DRIZZLE_DENSE
            // Rain 5xx
            500 -> RAIN_SLIGHT
            501 -> RAIN_MODERATE
            502, 503, 504 -> RAIN_HEAVY
            511 -> FREEZING_RAIN_LIGHT
            520 -> RAIN_SHOWER_SLIGHT
            521 -> RAIN_SHOWER_MODERATE
            522, 531 -> RAIN_SHOWER_VIOLENT
            // Snow 6xx
            600 -> SNOW_SLIGHT
            601 -> SNOW_MODERATE
            602 -> SNOW_HEAVY
            611, 612, 613 -> SNOW_GRAINS
            615, 616 -> SNOW_SLIGHT
            620 -> SNOW_SHOWER_SLIGHT
            621, 622 -> SNOW_SHOWER_HEAVY
            // Atmosphere 7xx
            701, 711, 721, 731, 741, 751, 761, 762, 771, 781 -> FOG
            // Clear / Clouds 8xx
            800 -> if (isDay) CLEAR_DAY else CLEAR_NIGHT
            801 -> if (isDay) MAINLY_CLEAR_DAY else MAINLY_CLEAR_NIGHT
            802 -> if (isDay) PARTLY_CLOUDY_DAY else PARTLY_CLOUDY_NIGHT
            803, 804 -> OVERCAST
            else -> if (isDay) MAINLY_CLEAR_DAY else MAINLY_CLEAR_NIGHT
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

