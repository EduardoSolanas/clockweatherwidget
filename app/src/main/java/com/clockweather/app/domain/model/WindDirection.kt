package com.clockweather.app.domain.model

import com.clockweather.app.R

enum class WindDirection(val labelResId: Int) {
    N(R.string.wind_n),
    NNE(R.string.wind_nne),
    NE(R.string.wind_ne),
    ENE(R.string.wind_ene),
    E(R.string.wind_e),
    ESE(R.string.wind_ese),
    SE(R.string.wind_se),
    SSE(R.string.wind_sse),
    S(R.string.wind_s),
    SSW(R.string.wind_ssw),
    SW(R.string.wind_sw),
    WSW(R.string.wind_wsw),
    W(R.string.wind_w),
    WNW(R.string.wind_wnw),
    NW(R.string.wind_nw),
    NNW(R.string.wind_nnw);

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
            entries.firstOrNull { it.name.equals(label.trim(), ignoreCase = true) } ?: N
    }
}

