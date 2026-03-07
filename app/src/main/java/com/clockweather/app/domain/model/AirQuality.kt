package com.clockweather.app.domain.model

data class AirQuality(
    val co: Double,       // Carbon Monoxide μg/m³
    val no2: Double,      // Nitrogen Dioxide μg/m³
    val o3: Double,       // Ozone μg/m³
    val so2: Double,      // Sulphur Dioxide μg/m³
    val pm25: Double,     // Fine particles μg/m³
    val pm10: Double,     // Coarse particles μg/m³
    val usEpaIndex: Int,  // 1=Good, 2=Moderate, 3=Unhealthy for sensitive, 4=Unhealthy, 5=Very Unhealthy, 6=Hazardous
    val gbDefraIndex: Int // 1-3=Low, 4-6=Moderate, 7-9=High, 10=Very High
) {
    val usEpaLabel: String get() = when (usEpaIndex) {
        1 -> "Good"
        2 -> "Moderate"
        3 -> "Unhealthy for Sensitive"
        4 -> "Unhealthy"
        5 -> "Very Unhealthy"
        6 -> "Hazardous"
        else -> "Unknown"
    }

    val gbDefraLabel: String get() = when {
        gbDefraIndex in 1..3 -> "Low"
        gbDefraIndex in 4..6 -> "Moderate"
        gbDefraIndex in 7..9 -> "High"
        gbDefraIndex >= 10  -> "Very High"
        else -> "Unknown"
    }
}

