package com.clockweather.app.domain.model

import com.clockweather.app.R

enum class ClockTileSize(val labelResId: Int) {
    SMALL(R.string.size_small),
    MEDIUM(R.string.size_medium),
    LARGE(R.string.size_large),
    EXTRA_LARGE(R.string.size_xl)
}
