package com.clockweather.app.domain.model

import com.clockweather.app.R
import org.junit.Assert.assertEquals
import org.junit.Test

class AirQualityDefraLabelTest {

    private fun aq(defraIndex: Int) = AirQuality(
        co = 0.0, no2 = 0.0, o3 = 0.0, so2 = 0.0,
        pm25 = 0.0, pm10 = 0.0, usEpaIndex = 1,
        gbDefraIndex = defraIndex,
    )

    @Test
    fun `gbDefraLabelResId returns Low for indices 1 to 3`() {
        for (i in 1..3) {
            assertEquals("DEFRA $i should be Low", R.string.aq_low, aq(i).gbDefraLabelResId)
        }
    }

    @Test
    fun `gbDefraLabelResId returns Moderate for indices 4 to 6`() {
        for (i in 4..6) {
            assertEquals("DEFRA $i should be Moderate", R.string.aq_moderate, aq(i).gbDefraLabelResId)
        }
    }

    @Test
    fun `gbDefraLabelResId returns High for indices 7 to 9`() {
        for (i in 7..9) {
            assertEquals("DEFRA $i should be High", R.string.aq_high, aq(i).gbDefraLabelResId)
        }
    }

    @Test
    fun `gbDefraLabelResId returns Very High for index 10`() {
        assertEquals(R.string.aq_very_high, aq(10).gbDefraLabelResId)
    }

    @Test
    fun `gbDefraLabelResId returns Unknown for index 0`() {
        assertEquals(R.string.label_unknown, aq(0).gbDefraLabelResId)
    }
}
