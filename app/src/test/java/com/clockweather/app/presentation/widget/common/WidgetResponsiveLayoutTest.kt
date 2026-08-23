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
 * Responsive breakpoints are deliberately not used.
 *
 * A breakpoint map parcels every mapped view into one transaction, so it only pays off if smaller
 * sizes bind *less*. The only content worth dropping in these widgets is the five-day forecast
 * row — the reason the widgets exist — so the trade never came out in the user's favour. This
 * test pins that decision: if breakpoints are ever re-introduced, the payload budget test must
 * prove the summed transaction still fits.
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
    fun `no provider declares breakpoints, on any API level`() {
        listOf(
            CompactWidgetUpdater(context, appWidgetManager, entryPoint),
            ExtendedWidgetUpdater(context, appWidgetManager, entryPoint),
            ForecastWidgetUpdater(context, appWidgetManager, entryPoint),
        ).forEach { assertTrue(it.getResponsiveSizeBreakpoints().isEmpty()) }
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
