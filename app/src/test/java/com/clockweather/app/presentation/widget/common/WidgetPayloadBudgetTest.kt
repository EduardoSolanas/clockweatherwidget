package com.clockweather.app.presentation.widget.common

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Parcel
import android.widget.RemoteViews
import com.clockweather.app.di.WidgetEntryPoint
import com.clockweather.app.presentation.widget.compact.CompactWidgetUpdater
import com.clockweather.app.presentation.widget.extended.ExtendedWidgetUpdater
import com.clockweather.app.presentation.settings.SettingsViewModel
import com.clockweather.app.presentation.widget.forecast.ForecastWidgetUpdater
import io.mockk.mockk
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDateTime

/**
 * Measures the real parcelled size of each widget's [RemoteViews].
 *
 * This is the payload profiling section 5.1 requires before responsive layouts can return.
 * Robolectric parcels bitmap pixel data faithfully (a 192x192 ARGB_8888 icon measures ~147,684
 * bytes against a theoretical 147,456), so these numbers reflect what actually crosses the
 * binder to the launcher.
 *
 * The launcher's transaction budget is ~1MB shared. [Budget] leaves headroom for the launcher's
 * own overhead rather than spending the whole limit.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class WidgetPayloadBudgetTest {

    private companion object {
        /** Conservative ceiling for a single updateAppWidget transaction. */
        const val Budget = 700_000
    }

    private lateinit var context: Context
    private val appWidgetManager: AppWidgetManager = mockk(relaxed = true)
    private val entryPoint: WidgetEntryPoint = mockk(relaxed = true)

    /**
     * Anchored to the real clock on purpose. `weatherToday()` resolves against wall-clock now,
     * so a fixed past date makes every forecast row filter out as history and the five day
     * icons never bind — which would understate the payload by roughly an order of magnitude.
     */
    private val reference: LocalDateTime = LocalDateTime.now()

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
    }

    @Test
    fun `each widget stays within the single-transaction payload budget at every icon style`() {
        iconStyles().forEach { (styleName, stylePref) ->
            val snapshot = WidgetTestFixtures.snapshot(
                weather = WidgetTestFixtures.weatherData(reference),
                iconStyle = stylePref,
            )

            updaters().forEach { (name, updater) ->
                val bytes = parcelSize(updater.buildViews(appWidgetId = 1, snapshot = snapshot))
                println("PAYLOAD $name style=$styleName = $bytes bytes")

                assertTrue(
                    "$name at icon style $styleName is $bytes bytes, over the $Budget budget — " +
                        "a launcher transaction this large risks TransactionTooLargeException",
                    bytes < Budget
                )
            }
        }
    }

    /**
     * Guards the reason responsive layouts were withdrawn. A breakpoint map parcels every
     * mapped view in one transaction, so the sum — not the largest entry — must fit.
     */
    @Test
    fun `the sum of all responsive breakpoints stays within budget at every icon style`() {
        iconStyles().forEach { (styleName, stylePref) ->
            val snapshot = WidgetTestFixtures.snapshot(
                weather = WidgetTestFixtures.weatherData(reference),
                iconStyle = stylePref,
            )

            updaters().forEach { (name, updater) ->
                val breakpoints = updater.getResponsiveSizeBreakpoints()
                if (breakpoints.isEmpty()) return@forEach

                val total = breakpoints.sumOf { size ->
                    parcelSize(updater.buildViews(1, snapshot, size.width, size.height))
                }
                println("PAYLOAD $name style=$styleName breakpoints=${breakpoints.size} total=$total bytes")

                assertTrue(
                    "$name at $styleName maps ${breakpoints.size} breakpoints totalling $total " +
                        "bytes, over the $Budget budget — every mapped view is parcelled in the " +
                        "same transaction",
                    total < Budget
                )
            }
        }
    }

    private fun updaters() = listOf(
        "compact" to CompactWidgetUpdater(context, appWidgetManager, entryPoint),
        "extended" to ExtendedWidgetUpdater(context, appWidgetManager, entryPoint),
        "forecast" to ForecastWidgetUpdater(context, appWidgetManager, entryPoint),
    )

    /** Every selectable style, because per-icon cost varies ~5x between them. */
    private fun iconStyles() = listOf(
        "GLASS" to SettingsViewModel.ICON_STYLE_GLASS,
        "CLAY_3D" to SettingsViewModel.ICON_STYLE_CLAY,
        "NEON_EDGE" to SettingsViewModel.ICON_STYLE_NEON,
        "GLASS_AI" to SettingsViewModel.ICON_STYLE_GLASS_AI,
    )

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
