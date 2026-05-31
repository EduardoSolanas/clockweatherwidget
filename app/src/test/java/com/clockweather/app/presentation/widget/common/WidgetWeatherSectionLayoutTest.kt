package com.clockweather.app.presentation.widget.common

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetWeatherSectionLayoutTest {

    @Test
    fun `real widget layouts include the shared top clock weather section`() {
        val layoutFiles = listOf(
            "widget_compact.xml",
            "widget_extended.xml",
            "widget_forecast.xml",
        )

        layoutFiles.forEach { layoutFile ->
            val xml = readLayout(layoutFile)
            assertTrue(
                "$layoutFile should include @layout/widget_top_clock_weather",
                xml.contains("<include") && xml.contains("layout=\"@layout/widget_top_clock_weather\""),
            )
        }
    }

    @Test
    fun `shared top clock weather section includes clock date and weather card`() {
        val xml = readLayout("widget_top_clock_weather.xml")

        assertTrue(
            "shared top section should include @layout/widget_clock_block",
            xml.contains("<include") && xml.contains("layout=\"@layout/widget_clock_block\""),
        )
        assertTrue(
            "shared top section should expose widget_date for binders",
            xml.contains("android:id=\"@+id/widget_date\""),
        )
        assertTrue(
            "shared top section should include @layout/widget_weather_card",
            xml.contains("<include") && xml.contains("layout=\"@layout/widget_weather_card\""),
        )
    }

    @Test
    fun `preview widget layouts include the shared weather card`() {
        val layoutFiles = listOf(
            "widget_compact_preview.xml",
            "widget_extended_preview.xml",
            "widget_forecast_preview.xml",
        )

        layoutFiles.forEach { layoutFile ->
            val xml = readLayout(layoutFile)
            assertTrue(
                "$layoutFile should include @layout/widget_weather_card or @layout/widget_weather_card_preview",
                xml.contains("<include") && (
                    xml.contains("layout=\"@layout/widget_weather_card\"") ||
                    xml.contains("layout=\"@layout/widget_weather_card_preview\"")
                ),
            )
        }
    }

    @Test
    fun `main weather icon has inset padding to avoid edge clipping`() {
        val xml = readLayout("widget_weather_card.xml")

        assertTrue(
            "weather_icon should reserve padding so large icons do not draw flush to the ImageView bounds",
            xml.contains("android:id=\"@+id/weather_icon\"") &&
                xml.contains("android:padding"),
        )
    }

    @Test
    fun `main weather location text has a bounded width and ellipsizes as fallback`() {
        val xml = readLayout("widget_weather_card.xml")

        assertTrue(
            "city_name should keep a max width fallback even when label selection cannot find a shorter area",
            xml.contains("android:id=\"@+id/city_name\"") &&
                xml.contains("android:maxWidth=") &&
                xml.contains("android:ellipsize=\"end\""),
        )
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
