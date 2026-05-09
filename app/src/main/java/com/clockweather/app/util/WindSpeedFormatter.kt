package com.clockweather.app.util

import com.clockweather.app.domain.model.SpeedUnit
import kotlin.math.roundToInt

object WindSpeedFormatter {

    fun convert(kmh: Double, unit: SpeedUnit): Double = when (unit) {
        SpeedUnit.KMH -> kmh
        SpeedUnit.MPH -> kmh / 1.60934
        SpeedUnit.MS -> kmh / 3.6
        SpeedUnit.KNOTS -> kmh / 1.852
    }

    fun format(kmh: Double, unit: SpeedUnit): String {
        return "${convert(kmh, unit).roundToInt()}"
    }

    fun formatWithUnit(kmh: Double, unit: SpeedUnit): String {
        return "${convert(kmh, unit).roundToInt()}${unit.symbol}"
    }
}
