package com.clockweather.app.presentation.widget.common

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetWeatherSectionLayoutTest {

    @Test
    fun `real widget layouts include the shared weather card`() {
        val layoutFiles = listOf(
            "widget_compact.xml",
            "widget_extended.xml",
            "widget_forecast.xml",
            "widget_large.xml",
        )

        layoutFiles.forEach { layoutFile ->
            val xml = readLayout(layoutFile)
            assertTrue(
                "$layoutFile should include @layout/widget_weather_card",
                xml.contains("<include") && xml.contains("layout=\"@layout/widget_weather_card\""),
            )
        }
    }

    @Test
    fun `preview widget layouts include the shared preview weather card`() {
        val layoutFiles = listOf(
            "widget_compact_preview.xml",
            "widget_extended_preview.xml",
            "widget_forecast_preview.xml",
            "widget_large_preview.xml",
        )

        layoutFiles.forEach { layoutFile ->
            val xml = readLayout(layoutFile)
            assertTrue(
                "$layoutFile should include @layout/widget_weather_card_preview",
                xml.contains("<include") && xml.contains("layout=\"@layout/widget_weather_card_preview\""),
            )
        }
    }

    private fun readLayout(fileName: String): String {
        val candidates = listOf(
            File("src/main/res/layout/$fileName"),
            File("app/src/main/res/layout/$fileName"),
        )

        val layoutFile = candidates.firstOrNull(File::exists)
            ?: error("Unable to locate layout file $fileName from ${System.getProperty("user.dir")}")

        return layoutFile.readText()
    }
}