package com.clockweather.app.presentation.widget.common

/**
 * How much content a widget should bind for a given rendered size.
 *
 * Responsive `RemoteViews` parcels every mapped breakpoint in one transaction, so the size class
 * has to change what is *bound*, not just how it is styled. Binding the same content at every
 * breakpoint multiplies the payload with no user-visible benefit — which is exactly why the first
 * responsive attempt was withdrawn.
 */
enum class WidgetSizeClass {
    /** Too short for a forecast row: hero weather only. */
    COMPACT,

    /** Full content for the provider. */
    REGULAR;

    companion object {
        /**
         * Height below which a weekly forecast row cannot render legibly. Derived from the
         * providers' `minResizeHeight` values (110-140dp) rather than launcher cell counts,
         * which vary by device.
         */
        const val ForecastRowMinHeightDp = 150f

        /**
         * Resolves a size class from the rendered size. A null size means the caller is building
         * the single non-responsive layout, which always binds full content.
         */
        fun forSize(widthDp: Float?, heightDp: Float?): WidgetSizeClass {
            if (widthDp == null || heightDp == null) return REGULAR
            return if (heightDp < ForecastRowMinHeightDp) COMPACT else REGULAR
        }
    }
}
