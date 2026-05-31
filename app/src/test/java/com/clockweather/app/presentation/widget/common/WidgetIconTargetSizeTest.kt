package com.clockweather.app.presentation.widget.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetIconTargetSizeTest {

    @Test
    fun `does not upscale intrinsic size - caps oversized icon`() {
        // A 96dp x 90dp vector on a 2.75x density Xiaomi reports intrinsic 264x248 px
        // (intrinsicWidth is ALREADY density-scaled). The old code multiplied by density
        // again -> 726x682 (~2MB) which blew the launcher RemoteViews budget and produced
        // "Can't load widget" on Android 10 MIUI. The target must stay bounded.
        val (w, h) = widgetIconTargetSize(264, 248)

        assertTrue("width $w should be <= $WidgetIconMaxDimensionPx", w <= WidgetIconMaxDimensionPx)
        assertTrue("height $h should be <= $WidgetIconMaxDimensionPx", h <= WidgetIconMaxDimensionPx)
    }

    @Test
    fun `preserves aspect ratio when capping`() {
        val (w, h) = widgetIconTargetSize(264, 248)
        assertEquals(264.0 / 248.0, w.toDouble() / h, 0.03)
    }

    @Test
    fun `returns intrinsic size unchanged when within cap`() {
        assertEquals(96 to 90, widgetIconTargetSize(96, 90))
    }

    @Test
    fun `falls back to cap for non-positive intrinsic`() {
        assertEquals(
            WidgetIconMaxDimensionPx to WidgetIconMaxDimensionPx,
            widgetIconTargetSize(0, -5),
        )
    }

    @Test
    fun `six icons stay under the 1MB RemoteViews transaction budget`() {
        val (w, h) = widgetIconTargetSize(264, 248)
        val bytesPerIcon = w * h * 4 // ARGB_8888
        assertTrue(
            "six icons ($bytesPerIcon bytes each) must fit the launcher budget",
            bytesPerIcon * 6 < 1_000_000,
        )
    }
}
