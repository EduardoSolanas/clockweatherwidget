package com.clockweather.app.util

import com.clockweather.app.domain.model.TemperatureUnit
import kotlin.math.roundToInt

object TemperatureFormatter {

    fun convert(celsius: Double, unit: TemperatureUnit): Double = when (unit) {
        TemperatureUnit.CELSIUS -> celsius
        TemperatureUnit.FAHRENHEIT -> celsius * 9.0 / 5.0 + 32.0
    }

    fun format(temperature: Double, unit: TemperatureUnit): String {
        val converted = convert(temperature, unit)
        return "${converted.roundToInt()}"
    }

    fun formatWithUnit(temperature: Double, unit: TemperatureUnit): String {
        val converted = convert(temperature, unit)
        return "${converted.roundToInt()}${unit.symbol}"
    }

    fun formatHighLow(high: Double, low: Double, unit: TemperatureUnit): String {
        return "${format(high, unit)}° / ${format(low, unit)}°"
    }
}

