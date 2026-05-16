package com.clockweather.app.presentation.detail.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WeatherOverlayPngAssetTest {

    @Test
    fun `wet glass overlay is a transparent png asset`() {
        val bytes = resourceFile("weather_overlay_wet_glass.png").readBytes()

        assertEquals(0x89.toByte(), bytes[0])
        assertEquals('P'.code.toByte(), bytes[1])
        assertEquals('N'.code.toByte(), bytes[2])
        assertEquals('G'.code.toByte(), bytes[3])
        assertEquals("PNG color type must be RGBA", 6, bytes[25].toInt())
        assertTrue("Imagegen-style overlay should not be an empty placeholder", bytes.size > 20_000)
    }

    private fun resourceFile(name: String): File {
        val candidates = listOf(
            File("src/main/res/drawable-nodpi/$name"),
            File("app/src/main/res/drawable-nodpi/$name")
        )
        return candidates.first { it.isFile }
    }
}
