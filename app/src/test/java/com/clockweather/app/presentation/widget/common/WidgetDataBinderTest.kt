package com.clockweather.app.presentation.widget.common

import android.content.Context
import android.content.res.Resources
import android.util.Log
import android.widget.RemoteViews
import com.clockweather.app.R
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
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

        every {
            resources.getIdentifier(any(), eq("id"), eq("com.clockweather.app"))
        } answers {
            val name = firstArg<String>()
            name.hashCode()
        }
    }

    @Test
    fun `bindClockViews non-incremental sets visibility and displayed child for all digits`() {
        WidgetDataBinder.bindClockViews(
            context = context,
            views = views,
            appWidgetId = 1,
            hour = 10,
            minute = 25,
            is24h = true,
            isIncremental = false
        )

        // Non-incremental should not trigger ViewFlipper animations.
        verify(exactly = 0) { views.setDisplayedChild(R.id.digit_h1, any()) }
        verify(exactly = 0) { views.setDisplayedChild(R.id.digit_h2, any()) }
        verify(exactly = 0) { views.setDisplayedChild(R.id.digit_m1, any()) }
        verify(exactly = 0) { views.setDisplayedChild(R.id.digit_m2, any()) }

        // digit_h1 -> 1: child 1 VISIBLE, others GONE
        val h1_1 = resources.getIdentifier("digit_h1_1", "id", "com.clockweather.app")
        verify { views.setViewVisibility(h1_1, android.view.View.VISIBLE) }

        // digit_h2 -> 0: child 0 VISIBLE
        val h2_0 = resources.getIdentifier("digit_h2_0", "id", "com.clockweather.app")
        verify { views.setViewVisibility(h2_0, android.view.View.VISIBLE) }

        // digit_m1 -> 2: child 2 VISIBLE
        val m1_2 = resources.getIdentifier("digit_m1_2", "id", "com.clockweather.app")
        verify { views.setViewVisibility(m1_2, android.view.View.VISIBLE) }

        // digit_m2 -> 5: child 5 VISIBLE
        val m2_5 = resources.getIdentifier("digit_m2_5", "id", "com.clockweather.app")
        verify { views.setViewVisibility(m2_5, android.view.View.VISIBLE) }
    }

    @Test
    fun `bindClockViews incremental only sets displayed child for changed digits`() {
        WidgetDataBinder.bindClockViews(
            context = context,
            views = views,
            appWidgetId = 1,
            hour = 10,
            minute = 26,
            is24h = true,
            isIncremental = true
        )

        // h1, h2, m1 unchanged — no flip calls
        verify(exactly = 0) { views.setDisplayedChild(R.id.digit_h1, any()) }
        verify(exactly = 0) { views.showNext(R.id.digit_h1) }
        verify(exactly = 0) { views.setDisplayedChild(R.id.digit_h2, any()) }
        verify(exactly = 0) { views.showNext(R.id.digit_h2) }
        verify(exactly = 0) { views.setDisplayedChild(R.id.digit_m1, any()) }
        verify(exactly = 0) { views.showNext(R.id.digit_m1) }
        // m2: 5→6 is +1, so showNext (not setDisplayedChild)
        verify(exactly = 1) { views.showNext(R.id.digit_m2) }
        verify(exactly = 0) { views.setDisplayedChild(R.id.digit_m2, any()) }
    }

    @Test
    fun `bindClockViews incremental handles hour boundary`() {
        // prev fallback: 9:59 → 10:00
        WidgetDataBinder.bindClockViews(
            context = context,
            views = views,
            appWidgetId = 1,
            hour = 10,
            minute = 0,
            is24h = true,
            isIncremental = true
        )

        // h1: 0→1 (+1) → showNext
        verify(exactly = 1) { views.showNext(R.id.digit_h1) }
        // h2: 9→0 (+1 mod 10) → showNext
        verify(exactly = 1) { views.showNext(R.id.digit_h2) }
        // m1: 5→0 (not +1) → setDisplayedChild
        verify(exactly = 1) { views.setDisplayedChild(R.id.digit_m1, 0) }
        // m2: 9→0 (+1 mod 10) → showNext
        verify(exactly = 1) { views.showNext(R.id.digit_m2) }
    }

    @Test
    fun `bindClockViews incremental handles minute tens boundary`() {
        // prev fallback: 10:29 → 10:30
        WidgetDataBinder.bindClockViews(
            context = context,
            views = views,
            appWidgetId = 1,
            hour = 10,
            minute = 30,
            is24h = true,
            isIncremental = true
        )

        verify(exactly = 0) { views.setDisplayedChild(R.id.digit_h1, any()) }
        verify(exactly = 0) { views.showNext(R.id.digit_h1) }
        verify(exactly = 0) { views.setDisplayedChild(R.id.digit_h2, any()) }
        verify(exactly = 0) { views.showNext(R.id.digit_h2) }
        // m1: 2→3 (+1) → showNext
        verify(exactly = 1) { views.showNext(R.id.digit_m1) }
        // m2: 9→0 (+1 mod 10) → showNext
        verify(exactly = 1) { views.showNext(R.id.digit_m2) }
    }

    @Test
    fun `incremental mode sends NO setTextViewText for any digit only setDisplayedChild`() {
        WidgetDataBinder.bindClockViews(
            context = context,
            views = views,
            appWidgetId = 1,
            hour = 10,
            minute = 26,
            is24h = true,
            isIncremental = true
        )

        val allDigitPrefixes = listOf("digit_h1", "digit_h2", "digit_m1", "digit_m2")
        allDigitPrefixes.forEach { prefix ->
            (0..9).forEach { i ->
                val childId = resources.getIdentifier("${prefix}_$i", "id", "com.clockweather.app")
                if (childId != 0) {
                    verify(exactly = 0) { views.setTextViewText(childId, any()) }
                }
            }
        }

        verify(exactly = 0) { views.setDisplayedChild(R.id.digit_h1, any()) }
        verify(exactly = 0) { views.showNext(R.id.digit_h1) }
        verify(exactly = 0) { views.setDisplayedChild(R.id.digit_h2, any()) }
        verify(exactly = 0) { views.showNext(R.id.digit_h2) }
        verify(exactly = 0) { views.setDisplayedChild(R.id.digit_m1, any()) }
        verify(exactly = 0) { views.showNext(R.id.digit_m1) }
        // m2: 5→6 (+1) → showNext
        verify(exactly = 1) { views.showNext(R.id.digit_m2) }
        verify(exactly = 0) { views.setDisplayedChild(R.id.digit_m2, any()) }
    }

    @Test
    fun `incremental 12h mode handles noon boundary correctly`() {
        WidgetDataBinder.bindClockViews(
            context = context,
            views = views,
            appWidgetId = 1,
            hour = 12,
            minute = 0,
            is24h = false,
            isIncremental = true
        )

        // h1: 1→1, no change
        verify(exactly = 0) { views.setDisplayedChild(R.id.digit_h1, any()) }
        verify(exactly = 0) { views.showNext(R.id.digit_h1) }
        // h2: 1→2 (+1) → showNext
        verify(exactly = 1) { views.showNext(R.id.digit_h2) }
        // m1: 5→0 (not +1) → setDisplayedChild
        verify(exactly = 1) { views.setDisplayedChild(R.id.digit_m1, 0) }
        // m2: 9→0 (+1 mod 10) → showNext
        verify(exactly = 1) { views.showNext(R.id.digit_m2) }
    }

    @Test
    fun `incremental midnight boundary in 24h mode`() {
        // prev fallback: 23:59 → 0:00
        WidgetDataBinder.bindClockViews(
            context = context,
            views = views,
            appWidgetId = 1,
            hour = 0,
            minute = 0,
            is24h = true,
            isIncremental = true
        )

        // h1: 2→0 (not +1) → setDisplayedChild
        verify(exactly = 1) { views.setDisplayedChild(R.id.digit_h1, 0) }
        // h2: 3→0 (not +1) → setDisplayedChild
        verify(exactly = 1) { views.setDisplayedChild(R.id.digit_h2, 0) }
        // m1: 5→0 (not +1) → setDisplayedChild
        verify(exactly = 1) { views.setDisplayedChild(R.id.digit_m1, 0) }
        // m2: 9→0 (+1 mod 10) → showNext
        verify(exactly = 1) { views.showNext(R.id.digit_m2) }
    }
}
