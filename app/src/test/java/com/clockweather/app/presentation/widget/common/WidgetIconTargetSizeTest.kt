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

    /**
     * The previous version of this test asserted six icons at the hero cap against a flat 1MB.
     * With the 512x512 CLAY_3D source that is 884,736 bytes - under 1MB, so it passed, while the
     * real Extended widget was shipping 893,764 bytes per update. It measured a hypothetical and
     * called it safe. Real payloads are measured end to end in WidgetPayloadBudgetTest; this test
     * now only guards the relationship between the two caps.
     */
    @Test
    fun `row icons are capped well below the hero icon`() {
        val (heroW, heroH) = widgetIconTargetSize(512, 512)
        val (rowW, rowH) = widgetIconTargetSize(512, 512, WidgetForecastIconMaxDimensionPx)

        val heroBytes = heroW * heroH * 4
        val rowBytes = rowW * rowH * 4

        assertTrue(
            "row icons ($rowBytes bytes) must cost materially less than the hero icon ($heroBytes)",
            rowBytes * 2 < heroBytes,
        )
    }

    @Test
    fun `a hero icon plus five row icons leaves headroom in the transaction budget`() {
        val (heroW, heroH) = widgetIconTargetSize(512, 512)
        val (rowW, rowH) = widgetIconTargetSize(512, 512, WidgetForecastIconMaxDimensionPx)

        val total = heroW * heroH * 4 + 5 * rowW * rowH * 4
        assertTrue(
            "one hero plus five row icons is $total bytes; a single view should leave room for a " +
                "second responsive breakpoint in the same transaction",
            total * 2 < 1_000_000,
        )
    }
}
