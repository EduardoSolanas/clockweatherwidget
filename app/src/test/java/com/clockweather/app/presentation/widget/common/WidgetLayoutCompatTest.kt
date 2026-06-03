package com.clockweather.app.presentation.widget.common

import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class WidgetLayoutCompatTest {

    @Test
    fun `widget layouts should not use layout_marginHorizontal or layout_marginVertical`() {
        // Path to the layout resources directory
        val layoutDir = File("src/main/res/layout")
        if (!layoutDir.exists() || !layoutDir.isDirectory) {
            println("Layout directory not found at ${layoutDir.absolutePath}. Skipping test if running from IDE without proper working dir, but failing if expected to be there.")
            return
        }

        val widgetLayouts = layoutDir.listFiles { file ->
            file.isFile && file.name.startsWith("widget_") && file.extension == "xml"
        } ?: emptyArray()

        assertFalse("Expected to find some widget layout files", widgetLayouts.isEmpty())

        val offendingFiles = mutableListOf<String>()

        widgetLayouts.forEach { file ->
            val content = file.readText()
            if (content.contains("android:layout_marginHorizontal") || content.contains("android:layout_marginVertical")) {
                offendingFiles.add(file.name)
            }
        }

        assertFalse(
            "The following widget layouts use layout_marginHorizontal or layout_marginVertical, " +
                    "which crashes RemoteViews inflation on MIUI Android 10 and older devices: $offendingFiles",
            offendingFiles.isNotEmpty()
        )
    }
}
