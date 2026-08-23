package com.clockweather.app.presentation.widget.common

import android.content.Context
import android.graphics.Bitmap
import android.widget.RemoteViews
import com.clockweather.app.R
import com.clockweather.app.domain.model.TemperatureUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDateTime

/**
 * Quantifies rasterisation work per watchdog callback, per section 4.3.
 *
 * The payload half of that measurement lives in [WidgetPayloadBudgetTest]. This covers the other
 * half that can be measured off-device: the shared [WidgetRenderSnapshot] removed duplicate
 * Room/DataStore reads, but each widget still rasterises its own icons from that identical
 * snapshot, so bitmap work scales with widget count.
 *
 * These are structural counts through the `renderIcon` seam, not device timings. On-device CPU
 * and battery cost — in particular on the OEM device that exhibited the WorkManager deferral —
 * remains unmeasured and is not claimed here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class WidgetWatchdogCostTest {

    private lateinit var context: Context
    private val reference: LocalDateTime = LocalDateTime.now()

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
    }

    @Test
    fun `a forecast row rasterises one icon per visible day`() {
        var rendered = 0
        bindRows(dayCount = 5) { ctx, resId ->
            rendered++
            renderWidgetIconBitmap(ctx, resId, WidgetForecastIconMaxDimensionPx)
        }

        println("WATCHDOG rowIconsPerWidget=$rendered")
        assertEquals(5, rendered)
    }

    @Test
    fun `redrawing three widgets from one snapshot repeats the same rasterisation three times`() {
        var rendered = 0
        val counting: (Context, Int) -> Bitmap? = { ctx, resId ->
            rendered++
            renderWidgetIconBitmap(ctx, resId, WidgetForecastIconMaxDimensionPx)
        }

        repeat(3) { bindRows(dayCount = 5, renderIcon = counting) }

        println("WATCHDOG rowIconsForThreeWidgets=$rendered")
        assertEquals(
            "three widgets rasterise identical icons from an identical snapshot — the duplicate " +
                "work an icon cache (section 5.6) would remove",
            15, rendered
        )
    }

    @Test
    fun `the forecast cap is materially cheaper than the hero cap`() {
        val hero = sizeOf(WidgetIconMaxDimensionPx)
        val row = sizeOf(WidgetForecastIconMaxDimensionPx)

        println("WATCHDOG heroIconBytes=$hero rowIconBytes=$row")
        assertTrue(
            "row icons ($row bytes) should cost materially less than hero icons ($hero bytes)",
            row * 2 < hero
        )
    }

    private fun sizeOf(cap: Int): Int {
        val (w, h) = widgetIconTargetSize(512, 512, cap)
        return w * h * 4
    }

    private fun bindRows(
        dayCount: Int,
        renderIcon: (Context, Int) -> Bitmap?,
    ) {
        WidgetDataBinder.bindWeeklyForecastRows(
            context = context,
            views = RemoteViews(context.packageName, R.layout.widget_forecast),
            weatherData = WidgetTestFixtures.weatherData(
                reference,
                dailyForecasts = WidgetTestFixtures.dailyForecastsFrom(
                    reference.toLocalDate(), count = dayCount
                ),
            ),
            temperatureUnit = TemperatureUnit.CELSIUS,
            iconStyle = WeatherIconMapper.IconStyle.CLAY_3D,
            renderIcon = renderIcon,
        )
    }
}
