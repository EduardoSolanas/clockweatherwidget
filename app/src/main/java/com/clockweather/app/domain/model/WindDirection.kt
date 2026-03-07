package com.clockweather.app.domain.model

enum class WindDirection(val label: String) {
    N("N"),
    NNE("NNE"),
    NE("NE"),
    ENE("ENE"),
    E("E"),
    ESE("ESE"),
    SE("SE"),
    SSE("SSE"),
    S("S"),
    SSW("SSW"),
    SW("SW"),
    WSW("WSW"),
    W("W"),
    WNW("WNW"),
    NW("NW"),
    NNW("NNW");

    companion object {
        fun fromDegrees(degrees: Int): WindDirection {
            val d = ((degrees % 360) + 360) % 360
            return when {
                d >= 348 || d <= 11 -> N
                d in 12..33 -> NNE
                d in 34..56 -> NE
                d in 57..78 -> ENE
                d in 79..101 -> E
                d in 102..123 -> ESE
                d in 124..146 -> SE
                d in 147..168 -> SSE
                d in 169..191 -> S
                d in 192..213 -> SSW
                d in 214..236 -> SW
                d in 237..258 -> WSW
                d in 259..281 -> W
                d in 282..303 -> WNW
                d in 304..326 -> NW
                d in 327..347 -> NNW
                else -> N
            }
        }

        fun fromLabel(label: String): WindDirection =
            entries.firstOrNull { it.label.equals(label.trim(), ignoreCase = true) } ?: N
    }
}

