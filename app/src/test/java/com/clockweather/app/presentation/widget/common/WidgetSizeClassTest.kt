package com.clockweather.app.presentation.widget.common

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetSizeClassTest {

    @Test
    fun `a null size means the caller is building the single non-responsive layout`() {
        assertEquals(WidgetSizeClass.REGULAR, WidgetSizeClass.forSize(null, null))
        assertEquals(WidgetSizeClass.REGULAR, WidgetSizeClass.forSize(400f, null))
        assertEquals(WidgetSizeClass.REGULAR, WidgetSizeClass.forSize(null, 200f))
    }

    @Test
    fun `height below the forecast-row minimum is compact`() {
        assertEquals(WidgetSizeClass.COMPACT, WidgetSizeClass.forSize(400f, 110f))
        assertEquals(
            WidgetSizeClass.COMPACT,
            WidgetSizeClass.forSize(400f, WidgetSizeClass.ForecastRowMinHeightDp - 1f)
        )
    }

    @Test
    fun `height at or above the forecast-row minimum is regular`() {
        assertEquals(
            WidgetSizeClass.REGULAR,
            WidgetSizeClass.forSize(400f, WidgetSizeClass.ForecastRowMinHeightDp)
        )
        assertEquals(WidgetSizeClass.REGULAR, WidgetSizeClass.forSize(400f, 220f))
    }

    @Test
    fun `width alone does not decide the class - the forecast row is height-constrained`() {
        assertEquals(WidgetSizeClass.REGULAR, WidgetSizeClass.forSize(180f, 220f))
        assertEquals(WidgetSizeClass.COMPACT, WidgetSizeClass.forSize(650f, 120f))
    }
}
