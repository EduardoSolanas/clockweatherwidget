package com.clockweather.app.domain.model
import com.clockweather.app.R

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
    val usEpaLabelResId: Int get() = when (usEpaIndex) {
        1 -> R.string.aq_good
        2 -> R.string.aq_moderate
        3 -> R.string.aq_unhealthy_sensitive
        4 -> R.string.aq_unhealthy
        5 -> R.string.aq_very_unhealthy
        6 -> R.string.aq_hazardous
        else -> R.string.label_unknown
    }

    val gbDefraLabelResId: Int get() = when {
        gbDefraIndex in 1..3 -> R.string.aq_low
        gbDefraIndex in 4..6 -> R.string.aq_moderate
        gbDefraIndex in 7..9 -> R.string.aq_high
        gbDefraIndex >= 10  -> R.string.aq_very_high
        else -> R.string.label_unknown
    }

    val usEpaLabel: String get() = usEpaIndex.toString() // Fallback or remove if unused
    val gbDefraLabel: String get() = gbDefraIndex.toString()
}

