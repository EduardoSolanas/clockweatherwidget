package com.clockweather.app.presentation.widget.common

import android.content.Context
import android.view.View
import android.widget.RemoteViews
import androidx.test.core.app.ApplicationProvider
import com.clockweather.app.R
import com.clockweather.app.domain.model.PollenData
import com.clockweather.app.domain.model.PollenType
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WidgetPollenBarTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `pollenLevelColor maps UPI levels to corresponding severity colors`() {
        assertEquals(0xFF22C55E.toInt(), pollenLevelColor(1)) // Low
        assertEquals(0xFF22C55E.toInt(), pollenLevelColor(2)) // Low
        assertEquals(0xFFEAB308.toInt(), pollenLevelColor(3)) // Moderate
        assertEquals(0xFFF97316.toInt(), pollenLevelColor(4)) // High
        assertEquals(0xFFEF4444.toInt(), pollenLevelColor(5)) // Very High
        assertEquals(0x5594A3B8, pollenLevelColor(0))         // Inactive
    }

    @Test
    fun `renderPollenSegmentsBitmap creates a non-null valid bitmap for each level`() {
        for (level in 0..5) {
            val bitmap = renderPollenSegmentsBitmap(context, level)
            assertNotNull("Bitmap should be created for level $level", bitmap)
            assertTrue("Bitmap width should be > 0", bitmap.width > 0)
            assertTrue("Bitmap height should be > 0", bitmap.height > 0)
        }
    }

    @Test
    fun `bindPollenBar hides container when pollenData is null`() {
        val views = mockk<RemoteViews>(relaxed = true)
        WidgetDataBinder.bindPollenBar(context, views, null, showPollen = true)

        verify { views.setViewVisibility(R.id.pollen_bar_container, View.GONE) }
    }

    @Test
    fun `bindPollenBar hides container when showPollen is false`() {
        val views = mockk<RemoteViews>(relaxed = true)
        val pollenData = PollenData(
            grassPollen = PollenType("GRASS", "Grass", inSeason = true, indexValue = 3),
        )
        WidgetDataBinder.bindPollenBar(context, views, pollenData, showPollen = false)

        verify { views.setViewVisibility(R.id.pollen_bar_container, View.GONE) }
    }

    @Test
    fun `bindPollenBar shows container and binds segments when pollenData is present`() {
        val views = mockk<RemoteViews>(relaxed = true)
        val pollenData = PollenData(
            grassPollen = PollenType("GRASS", "Grass", inSeason = true, indexValue = 3),
            treePollen = PollenType("TREE", "Tree", inSeason = true, indexValue = 1),
            weedPollen = PollenType("WEED", "Weed", inSeason = true, indexValue = 2),
        )
        WidgetDataBinder.bindPollenBar(context, views, pollenData, showPollen = true)

        verify { views.setViewVisibility(R.id.pollen_bar_container, View.VISIBLE) }
        verify { views.setImageViewBitmap(R.id.pollen_bar_grass_segments, any()) }
        verify { views.setImageViewBitmap(R.id.pollen_bar_tree_segments, any()) }
        verify { views.setImageViewBitmap(R.id.pollen_bar_weed_segments, any()) }
    }
}
