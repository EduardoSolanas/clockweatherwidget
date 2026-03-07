package com.clockweather.app.domain.model

enum class WeatherCondition(
    val description: String,
    val iconResName: String,
    val isNightVariant: Boolean = false
) {
    CLEAR_DAY("Clear sky", "ic_weather_clear_day"),
    CLEAR_NIGHT("Clear sky", "ic_weather_clear_night", true),
    MAINLY_CLEAR_DAY("Mainly clear", "ic_weather_partly_cloudy_day"),
    MAINLY_CLEAR_NIGHT("Mainly clear", "ic_weather_partly_cloudy_night", true),
    PARTLY_CLOUDY_DAY("Partly cloudy", "ic_weather_partly_cloudy_day"),
    PARTLY_CLOUDY_NIGHT("Partly cloudy", "ic_weather_partly_cloudy_night", true),
    OVERCAST("Overcast", "ic_weather_cloudy"),
    FOG("Fog", "ic_weather_fog"),
    DEPOSITING_RIME_FOG("Depositing rime fog", "ic_weather_fog"),
    DRIZZLE_LIGHT("Light drizzle", "ic_weather_drizzle_light"),
    DRIZZLE_MODERATE("Moderate drizzle", "ic_weather_drizzle"),
    DRIZZLE_DENSE("Dense drizzle", "ic_weather_drizzle_heavy"),
    FREEZING_DRIZZLE_LIGHT("Light freezing drizzle", "ic_weather_freezing_drizzle"),
    FREEZING_DRIZZLE_HEAVY("Heavy freezing drizzle", "ic_weather_freezing_drizzle"),
    RAIN_SLIGHT("Slight rain", "ic_weather_rain_light"),
    RAIN_MODERATE("Moderate rain", "ic_weather_rain"),
    RAIN_HEAVY("Heavy rain", "ic_weather_rain_heavy"),
    FREEZING_RAIN_LIGHT("Light freezing rain", "ic_weather_freezing_rain"),
    FREEZING_RAIN_HEAVY("Heavy freezing rain", "ic_weather_freezing_rain"),
    SNOW_SLIGHT("Slight snowfall", "ic_weather_snow_light"),
    SNOW_MODERATE("Moderate snowfall", "ic_weather_snow"),
    SNOW_HEAVY("Heavy snowfall", "ic_weather_snow_heavy"),
    SNOW_GRAINS("Snow grains", "ic_weather_snow_grains"),
    RAIN_SHOWER_SLIGHT("Slight rain shower", "ic_weather_rain_shower_light"),
    RAIN_SHOWER_MODERATE("Moderate rain shower", "ic_weather_rain_shower"),
    RAIN_SHOWER_VIOLENT("Violent rain shower", "ic_weather_rain_shower_heavy"),
    SNOW_SHOWER_SLIGHT("Slight snow shower", "ic_weather_snow_shower_light"),
    SNOW_SHOWER_HEAVY("Heavy snow shower", "ic_weather_snow_shower"),
    THUNDERSTORM("Thunderstorm", "ic_weather_thunderstorm"),
    THUNDERSTORM_SLIGHT_HAIL("Thunderstorm with slight hail", "ic_weather_thunderstorm_hail"),
    THUNDERSTORM_HEAVY_HAIL("Thunderstorm with heavy hail", "ic_weather_thunderstorm_hail"),
    UNKNOWN("Unknown", "ic_weather_clear_day");

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

