package com.clockweather.app.presentation.widget.common

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WidgetManualRefreshContractTest {

    @Test
    fun `all runtime widgets expose the shared manual refresh control`() {
        val sharedCard = File("src/main/res/layout/widget_weather_card.xml").readText()
        assertTrue(sharedCard.contains("android:id=\"@+id/widget_refresh\""))

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
        val method = scheduler.substringAfter("fun scheduleUserRefresh")

        assertTrue(method.contains("WeatherUpdateWorker.INPUT_FORCE_REFRESH to true"))
        assertTrue(method.contains("OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST"))
        assertTrue(method.contains("ExistingWorkPolicy.KEEP"))
    }
}
