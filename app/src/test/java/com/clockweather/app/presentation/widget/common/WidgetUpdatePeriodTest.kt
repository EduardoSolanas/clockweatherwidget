package com.clockweather.app.presentation.widget.common

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WidgetUpdatePeriodTest {

    // Android enforces a minimum of 30 minutes (1_800_000 ms).
    // Setting 0 disables periodic onUpdate entirely, which leaves the widget
    // frozen when the screen stays on continuously — no screen-on event fires,
    // so ScreenWakeReceiver never triggers, and the WorkManager periodic job
    // may be deferred by the OS for 30–90 min on OEM devices.
    private val minimumUpdatePeriodMillis = 1_800_000L

    @Test
    fun `all widget info XMLs declare a non-zero updatePeriodMillis`() {
        val resDir = File("src/main/res")
        val widgetInfoFiles = resDir.listFiles { file ->
            file.isDirectory && (file.name == "xml" || file.name.startsWith("xml-"))
        }.orEmpty().flatMap { xmlDir ->
            xmlDir.listFiles { file ->
                file.isFile && file.name.startsWith("widget_") && file.name.endsWith("_info.xml")
            }.orEmpty().asList()
        }

        assertTrue("No widget info XML files found under ${resDir.absolutePath}", widgetInfoFiles.isNotEmpty())

        widgetInfoFiles.forEach { file ->
            val match = Regex("""android:updatePeriodMillis="(\d+)"""")
                .find(file.readText())
            val period = match?.groupValues?.get(1)?.toLongOrNull()
            assertTrue(
                "${file.path}: updatePeriodMillis must be >= $minimumUpdatePeriodMillis (was $period)",
                period != null && period >= minimumUpdatePeriodMillis
            )
        }
    }
}
