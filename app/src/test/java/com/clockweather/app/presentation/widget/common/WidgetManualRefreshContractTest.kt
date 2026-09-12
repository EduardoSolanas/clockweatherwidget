package com.clockweather.app.presentation.widget.common

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WidgetManualRefreshContractTest {

    @Test
    fun `all runtime widgets include the shared top section and weather card`() {
        val topSection = File("src/main/res/layout/widget_top_clock_weather.xml").readText()
        assertTrue(topSection.contains("layout=\"@layout/widget_weather_card\""))

        listOf("compact", "extended", "forecast").forEach { widget ->
            val layout = File("src/main/res/layout/widget_${widget}.xml").readText()
            assertTrue(
                "widget_$widget must include the shared top section",
                layout.contains("layout=\"@layout/widget_top_clock_weather\"")
            )
        }
    }

    @Test
    fun `manual refresh receiver is internal`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val receiver = Regex(
            """<receiver\s+android:name="\.presentation\.widget\.common\.WidgetRefreshReceiver"\s+android:exported="false"\s*/>"""
        )
        assertTrue("WidgetRefreshReceiver must be declared non-exported", receiver.containsMatchIn(manifest))
    }

    @Test
    fun `manual refresh is unique expedited and safely falls back`() {
        val scheduler = File(
            "src/main/java/com/clockweather/app/worker/WeatherUpdateScheduler.kt"
        ).readText()

        // The user-facing entry point still forces a fetch and still deduplicates against work
        // already queued. Relocation has its own entry point with a different queueing policy,
        // so these two properties belong to this method rather than to the shared builder.
        val userRefresh = scheduler.substringAfter("fun scheduleUserRefresh(").substringBefore("}")
        assertTrue(
            "a user refresh must force a fetch rather than consult freshness",
            userRefresh.contains("forceRefresh = true")
        )
        assertTrue(
            "a user refresh must not displace work already queued",
            userRefresh.contains("ExistingWorkPolicy.KEEP")
        )

        // The shared builder carries the expedited request and the quota fallback that keeps it
        // from being dropped when the app has no expedited quota left.
        val builder = scheduler.substringAfter("private fun scheduleUserRefreshInternal")
        assertTrue(builder.contains("WeatherUpdateWorker.INPUT_FORCE_REFRESH to forceRefresh"))
        assertTrue(builder.contains("OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST"))
    }
}
