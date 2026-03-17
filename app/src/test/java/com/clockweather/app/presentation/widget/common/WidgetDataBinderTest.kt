package com.clockweather.app.presentation.widget.common

import android.content.Context
import android.content.res.Resources
import android.widget.RemoteViews
import com.clockweather.app.R
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import io.mockk.clearMocks
import org.junit.Before
import org.junit.Test

class WidgetDataBinderTest {

    private lateinit var context: Context
    private lateinit var resources: Resources
    private lateinit var views: RemoteViews

    @Before
    fun setup() {
        context = mockk()
        resources = mockk()
        views = mockk(relaxed = true)

        every { context.resources } returns resources
        every { context.packageName } returns "com.clockweather.app"

        // Mock getIdentifier to return 1-9 for digit 0-9
        every {
            resources.getIdentifier(any(), eq("id"), eq("com.clockweather.app"))
        } answers {
            val name = firstArg<String>()
            name.hashCode()
        }
    }

    @Test
    fun `bindClockViews non-incremental sets all digits as changed`() {
        WidgetDataBinder.bindClockViews(
            context = context,
            views = views,
            appWidgetId = 1,
            hour = 10,
            minute = 25,
            is24h = true,
            isIncremental = false
        )

        // All digits should receive setDisplayedChild in non-incremental (full) mode
        verify {
            views.setDisplayedChild(R.id.digit_h1, 1)
            views.setDisplayedChild(R.id.digit_h2, 0)
            views.setDisplayedChild(R.id.digit_m1, 2)
            views.setDisplayedChild(R.id.digit_m2, 5)
        }
    }

    @Test
    fun `bindClockViews incremental only sets displayed child for changed digits`() {
        // Assume time ticked from 10:25 to 10:26
        WidgetDataBinder.bindClockViews(
            context = context,
            views = views,
            appWidgetId = 1,
            hour = 10,
            minute = 26,
            is24h = true,
            isIncremental = true
        )

        // h1, h2, m1 are unchanged. m2 is changed
        verify(exactly = 0) { views.setDisplayedChild(R.id.digit_h1, any()) }
        verify(exactly = 0) { views.setDisplayedChild(R.id.digit_h2, any()) }
        verify(exactly = 0) { views.setDisplayedChild(R.id.digit_m1, any()) }
        
        verify(exactly = 1) { views.setDisplayedChild(R.id.digit_m2, 6) }
    }

    @Test
    fun `bindClockViews incremental handles hour boundary`() {
        // Assume time ticked from 09:59 to 10:00
        WidgetDataBinder.bindClockViews(
            context = context,
            views = views,
            appWidgetId = 1,
            hour = 10,
            minute = 0,
            is24h = true,
            isIncremental = true
        )

        // Previous hour: 09:59. Current: 10:00
        // h1 changed (0 -> 1)
        // h2 changed (9 -> 0)
        // m1 changed (5 -> 0)
        // m2 changed (9 -> 0)
        verify(exactly = 1) { views.setDisplayedChild(R.id.digit_h1, 1) }
        verify(exactly = 1) { views.setDisplayedChild(R.id.digit_h2, 0) }
        verify(exactly = 1) { views.setDisplayedChild(R.id.digit_m1, 0) }
        verify(exactly = 1) { views.setDisplayedChild(R.id.digit_m2, 0) }
    }

    @Test
    fun `bindClockViews incremental handles minute tens boundary`() {
        // Assume time ticked from 10:29 to 10:30
        WidgetDataBinder.bindClockViews(
            context = context,
            views = views,
            appWidgetId = 1,
            hour = 10,
            minute = 30,
            is24h = true,
            isIncremental = true
        )

        // Previous hour: 10:29. Current: 10:30
        // h1, h2: unchanged
        // m1 changed (2 -> 3)
        // m2 changed (9 -> 0)
        verify(exactly = 0) { views.setDisplayedChild(R.id.digit_h1, any()) }
        verify(exactly = 0) { views.setDisplayedChild(R.id.digit_h2, any()) }
        verify(exactly = 1) { views.setDisplayedChild(R.id.digit_m1, 3) }
        verify(exactly = 1) { views.setDisplayedChild(R.id.digit_m2, 0) }
    }

    @Test
    fun `incremental unchanged digits receive no RemoteViews commands at all`() {
        // Track all setTextViewText calls for h1 children
        val h1ChildIds = (0..9).map { i ->
            resources.getIdentifier("digit_h1_$i", "id", "com.clockweather.app")
        }.filter { it != 0 }

        val h2ChildIds = (0..9).map { i ->
            resources.getIdentifier("digit_h2_$i", "id", "com.clockweather.app")
        }.filter { it != 0 }

        clearMocks(views, answers = false)

        // 10:25 -> 10:26: only m2 changes
        WidgetDataBinder.bindClockViews(
            context = context,
            views = views,
            appWidgetId = 1,
            hour = 10,
            minute = 26,
            is24h = true,
            isIncremental = true
        )

        // Unchanged digits (h1, h2, m1) must not receive ANY setTextViewText calls
        // This prevents partiallyUpdateAppWidget from touching those ViewFlippers at all
        h1ChildIds.forEach { childId ->
            verify(exactly = 0) { views.setTextViewText(childId, any()) }
        }
        h2ChildIds.forEach { childId ->
            verify(exactly = 0) { views.setTextViewText(childId, any()) }
        }

        // Changed digit (m2) SHOULD receive setTextViewText to restore 0-9 labels
        val m2ChildIds = (0..9).map { i ->
            resources.getIdentifier("digit_m2_$i", "id", "com.clockweather.app")
        }.filter { it != 0 }
        m2ChildIds.forEach { childId ->
            verify(exactly = 1) { views.setTextViewText(childId, any()) }
        }
    }

    @Test
    fun `incremental 12h mode handles noon boundary correctly`() {
        // 11:59 -> 12:00 in 12h mode: display goes from 11:59 to 12:00
        WidgetDataBinder.bindClockViews(
            context = context,
            views = views,
            appWidgetId = 1,
            hour = 12,
            minute = 0,
            is24h = false,
            isIncremental = true
        )

        // 12h display: prev = 11:59, current = 12:00
        // h1: 1->1 (unchanged), h2: 1->2 (changed), m1: 5->0 (changed), m2: 9->0 (changed)
        verify(exactly = 0) { views.setDisplayedChild(R.id.digit_h1, any()) }
        verify(exactly = 1) { views.setDisplayedChild(R.id.digit_h2, 2) }
        verify(exactly = 1) { views.setDisplayedChild(R.id.digit_m1, 0) }
        verify(exactly = 1) { views.setDisplayedChild(R.id.digit_m2, 0) }
    }

    @Test
    fun `incremental midnight boundary in 24h mode`() {
        // 23:59 -> 00:00
        WidgetDataBinder.bindClockViews(
            context = context,
            views = views,
            appWidgetId = 1,
            hour = 0,
            minute = 0,
            is24h = true,
            isIncremental = true
        )

        // prev = 23:59, current = 00:00 — all digits change
        verify(exactly = 1) { views.setDisplayedChild(R.id.digit_h1, 0) }
        verify(exactly = 1) { views.setDisplayedChild(R.id.digit_h2, 0) }
        verify(exactly = 1) { views.setDisplayedChild(R.id.digit_m1, 0) }
        verify(exactly = 1) { views.setDisplayedChild(R.id.digit_m2, 0) }
    }
}
