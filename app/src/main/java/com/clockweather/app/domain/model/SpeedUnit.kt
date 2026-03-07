package com.clockweather.app.domain.model

enum class SpeedUnit(val symbol: String, val apiValue: String) {
    KMH("km/h", "kmh"),
    MPH("mph", "mph"),
    MS("m/s", "ms"),
    KNOTS("kn", "kn")
}

