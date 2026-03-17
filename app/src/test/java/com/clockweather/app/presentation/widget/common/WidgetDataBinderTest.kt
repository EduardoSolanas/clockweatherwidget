package com.clockweather.app.presentation.widget.common

import android.content.Context
import android.content.res.Resources
import android.widget.RemoteViews
import com.clockweather.app.R
import android.util.Log
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
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
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0

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
    fun `bindClockViews non-incremental uses setViewVisibility — no setDisplayedChild`() {
        WidgetDataBinder.bindClockViews(
            context = context,
            views = views,
            appWidgetId = 1,
            hour = 10,
            minute = 25,
            is24h = true,
            isIncremental = false
        )

        // Full update must NOT call setDisplayedChild — that would accumulate into
        // mergeRemoteViews and trigger flip animations on ALL digits on every tick.
        verify(exactly = 0) { views.setDisplayedChild(any(), any()) }

        // Instead it sets visibility on each child directly (idempotent, no animation side effects)
        val h1Id = resources.getIdentifier("digit_h1_1", "id", "com.clockweather.app")
        verify { views.setViewVisibility(h1Id, android.view.View.VISIBLE) }
        val h2Id = resources.getIdentifier("digit_h2_0", "id", "com.clockweather.app")
        verify { views.setViewVisibility(h2Id, android.view.View.VISIBLE) }
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
    fun `incremental mode sends NO setTextViewText for any digit — only setDisplayedChild`() {
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

        // Incremental path must emit ZERO text commands for all digits (changed or not).
        // This prevents partiallyUpdateAppWidget / mergeRemoteViews from accumulating
        // text actions that would cause all ViewFlippers to visually refresh and suppress
        // the flip animation on the next minute tick.
        val allDigitPrefixes = listOf("digit_h1", "digit_h2", "digit_m1", "digit_m2")
        allDigitPrefixes.forEach { prefix ->
            (0..9).forEach { i ->
                val childId = resources.getIdentifier("${prefix}_$i", "id", "com.clockweather.app")
                if (childId != 0) {
                    verify(exactly = 0) { views.setTextViewText(childId, any()) }
                }
            }
        }

        // Only changed digit (m2=6) gets setDisplayedChild
        verify(exactly = 0) { views.setDisplayedChild(R.id.digit_h1, any()) }
        verify(exactly = 0) { views.setDisplayedChild(R.id.digit_h2, any()) }
        verify(exactly = 0) { views.setDisplayedChild(R.id.digit_m1, any()) }
        verify(exactly = 1) { views.setDisplayedChild(R.id.digit_m2, 6) }
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
