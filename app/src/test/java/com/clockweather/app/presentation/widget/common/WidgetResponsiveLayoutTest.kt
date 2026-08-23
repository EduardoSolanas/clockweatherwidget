package com.clockweather.app.presentation.widget.common

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Parcel
import android.widget.RemoteViews
import com.clockweather.app.di.WidgetEntryPoint
import com.clockweather.app.presentation.settings.SettingsViewModel
import com.clockweather.app.presentation.widget.compact.CompactWidgetUpdater
import com.clockweather.app.presentation.widget.extended.ExtendedWidgetUpdater
import com.clockweather.app.presentation.widget.forecast.ForecastWidgetUpdater
import io.mockk.mockk
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
 * Responsive layouts must change what is *bound*, not merely how it is styled.
 *
 * The withdrawn first attempt passed size hints into `buildViews` that were never read, so every
 * breakpoint produced an identical view and the only effect was a multiplied payload. The test
 * that shipped alongside it asserted the hardcoded breakpoint list equalled the hardcoded
 * breakpoint list, so it could not fail. These tests assert on rendered output instead.
 */
@RunWith(RobolectricTestRunner::class)
class WidgetResponsiveLayoutTest {

    private lateinit var context: Context
    private val appWidgetManager: AppWidgetManager = mockk(relaxed = true)
    private val entryPoint: WidgetEntryPoint = mockk(relaxed = true)

    /** Real clock: `weatherToday()` filters forecast rows against wall-clock now. */
    private val reference: LocalDateTime = LocalDateTime.now()

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
    }

    private fun snapshot() = WidgetTestFixtures.snapshot(
        weather = WidgetTestFixtures.weatherData(reference),
        iconStyle = SettingsViewModel.ICON_STYLE_CLAY,
    )

    @Test
    @Config(sdk = [31])
    fun `a forecast-capable widget binds a smaller payload at its compact breakpoint`() {
        listOf(
            "extended" to ExtendedWidgetUpdater(context, appWidgetManager, entryPoint),
            "forecast" to ForecastWidgetUpdater(context, appWidgetManager, entryPoint),
        ).forEach { (name, updater) ->
            val breakpoints = updater.getResponsiveSizeBreakpoints()
            assertTrue("$name should declare breakpoints on API 31+", breakpoints.size >= 2)

            val smallest = breakpoints.minByOrNull { it.height }!!
            val largest = breakpoints.maxByOrNull { it.height }!!

            val small = parcelSize(updater.buildViews(1, snapshot(), smallest.width, smallest.height))
            val large = parcelSize(updater.buildViews(1, snapshot(), largest.width, largest.height))

            assertTrue(
                "$name renders $small bytes at ${smallest.height}dp and $large at " +
                    "${largest.height}dp — the compact breakpoint must bind less, otherwise the " +
                    "breakpoint map only multiplies the payload",
                small < large
            )
        }
    }

    @Test
    @Config(sdk = [31])
    fun `the compact breakpoint drops the forecast row rather than hiding it`() {
        val updater = ForecastWidgetUpdater(context, appWidgetManager, entryPoint)

        val compact = parcelSize(updater.buildViews(1, snapshot(), 180f, 120f))
        val regular = parcelSize(updater.buildViews(1, snapshot(), 350f, 220f))

        // Five CLAY_3D row icons at the forecast cap are ~65KB each. Merely setting the container
        // GONE would still parcel them, leaving the two sizes within noise of each other.
        assertTrue(
            "compact=$compact regular=$regular — the difference ($${regular - compact}) is too " +
                "small to represent five unbound row icons",
            regular - compact > 200_000
        )
    }

    @Test
    @Config(sdk = [31])
    fun `a widget with no forecast row renders the same at both breakpoints`() {
        val updater = CompactWidgetUpdater(context, appWidgetManager, entryPoint)
        val breakpoints = updater.getResponsiveSizeBreakpoints()

        val sizes = breakpoints.map {
            parcelSize(updater.buildViews(1, snapshot(), it.width, it.height))
        }.distinct()

        assertEquals(
            "compact has no size-varying content, so every breakpoint should render identically",
            1, sizes.size
        )
    }

    @Test
    @Config(sdk = [29])
    fun `pre-Android 12 declares no breakpoints and uses the single layout`() {
        listOf(
            CompactWidgetUpdater(context, appWidgetManager, entryPoint),
            ExtendedWidgetUpdater(context, appWidgetManager, entryPoint),
            ForecastWidgetUpdater(context, appWidgetManager, entryPoint),
        ).forEach { assertTrue(it.getResponsiveSizeBreakpoints().isEmpty()) }
    }

    @Test
    @Config(sdk = [31])
    fun `every declared breakpoint is at least the provider minimum resize size`() {
        // Breakpoints below minResize would never be selected; above the provider minimum they
        // would leave the smallest real size unmapped.
        mapOf(
            ExtendedWidgetUpdater(context, appWidgetManager, entryPoint) to (400f to 140f),
            ForecastWidgetUpdater(context, appWidgetManager, entryPoint) to (180f to 120f),
            CompactWidgetUpdater(context, appWidgetManager, entryPoint) to (180f to 110f),
        ).forEach { (updater, minResize) ->
            val smallest = updater.getResponsiveSizeBreakpoints().minByOrNull { it.width }!!
            assertEquals(minResize.first, smallest.width, 0.1f)
            assertEquals(minResize.second, smallest.height, 0.1f)
        }
    }

    private fun parcelSize(views: RemoteViews): Int {
        val parcel = Parcel.obtain()
        return try {
            views.writeToParcel(parcel, 0)
            parcel.dataSize()
        } finally {
            parcel.recycle()
        }
    }
}
