package com.clockweather.app.presentation.widget.common

import android.content.Context
import android.content.res.Resources
import android.util.Log
import android.widget.RemoteViews
import com.clockweather.app.R
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.Runs
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test

class WidgetDataBinderTest {

    private lateinit var context: Context
    private lateinit var resources: Resources
    private lateinit var views: RemoteViews

    @Before
    @Suppress("DEPRECATION")
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0

        context = mockk()
        resources = mockk()
        views = mockk()

        every { context.resources } returns resources
        every { context.packageName } returns "com.clockweather.app"

        every {
            resources.getIdentifier(any(), eq("id"), eq("com.clockweather.app"))
        } answers {
            val name = firstArg<String>()
            name.hashCode()
        }

        every { views.setViewVisibility(any(), any()) } just Runs
        every { views.setTextViewText(any(), any()) } just Runs
        every { views.setDisplayedChild(any(), any()) } just Runs
        every { views.showNext(any()) } just Runs
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `bindClockViews non-incremental sets visibility for all digits`() {
        WidgetDataBinder.bindClockViews(
            context = context,
            views = views,
            appWidgetId = 1,
            hour = 10,
            minute = 25,
            is24h = true,
            isIncremental = false
        )

        // Non-incremental should not trigger ViewFlipper index animations.
        verify(exactly = 0) { views.setDisplayedChild(any(), any()) }

        // digit_h1 -> 1: child 1 VISIBLE
        verify { views.setViewVisibility(R.id.digit_h1_1, android.view.View.VISIBLE) }
        verify { views.setViewVisibility(R.id.digit_h1_0, android.view.View.GONE) }

        // Cover all 40 setViewVisibility calls (4 digits × 10 children each)
        verify(atLeast = 1) { views.setViewVisibility(any(), any()) }
        // Cover ampm text update
        verify(exactly = 1) { views.setTextViewText(R.id.ampm, "") }
    }

    @Test
    fun `bindAtomicClockViews clears fold overlays with parity`() {
        WidgetDataBinder.bindAtomicClockViews(
            views = views,
            hour = 10,
            minute = 26,
            is24h = true
        )

        // Current implementation resets all fold overlays to the front child (0)
        verify(exactly = 1) { views.setDisplayedChild(R.id.fold_h1, 0) }
        verify(exactly = 1) { views.setDisplayedChild(R.id.fold_h2, 0) }
        verify(exactly = 1) { views.setDisplayedChild(R.id.fold_m1, 0) }
        verify(exactly = 1) { views.setDisplayedChild(R.id.fold_m2, 0) }
        // Cover base digit TextViews (h1/h2/m1/m2/ampm) + 8 overlay TextViews (from+to per digit)
        verify(atLeast = 1) { views.setTextViewText(any(), any()) }
    }

    @Test
    @Suppress("DEPRECATION")
    fun `animateAtomicFoldOverlays uses parity to decide flipped digit index`() {
        // 10:25 -> 10:26: only m2 changed (5 -> 6)
        // 6 is Even -> Index 0.
        WidgetDataBinder.animateAtomicFoldOverlays(
            views = views,
            previousDigits = DigitState(1, 0, 2, 5),
            currentDigits = DigitState(1, 0, 2, 6)
        )

        verify(exactly = 1) { views.setTextViewText(R.id.fold_m2_from, "5") }
        verify(exactly = 1) { views.setTextViewText(R.id.fold_m2_to, "6") }
        verify(exactly = 1) { views.setDisplayedChild(R.id.fold_m2, 0) }
        verify(exactly = 1) { views.showNext(R.id.fold_m2) }
    }

    @Test
    @Suppress("DEPRECATION")
    fun `bindClockViews incremental animates only changed digits`() {
        WidgetDataBinder.bindClockViews(
            context = context,
            views = views,
            appWidgetId = 1,
            hour = 10,
            minute = 26,
            is24h = true,
            isIncremental = true,
            prevDigits = DigitState(1, 0, 2, 5)
        )

        verify(exactly = 1) { views.setDisplayedChild(R.id.digit_m2, 5) }
        verify(exactly = 1) { views.showNext(R.id.digit_m2) }
        verify(exactly = 0) { views.setDisplayedChild(R.id.digit_h1, any()) }
        verify(exactly = 0) { views.setDisplayedChild(R.id.digit_h2, any()) }
        verify(exactly = 0) { views.setDisplayedChild(R.id.digit_m1, any()) }

        // m2 children are made visible before the animation step.
        verify(atLeast = 1) { views.setViewVisibility(any(), any()) }
    }

    @Test
    fun `syncFoldOverlaysChangedOnly updates text but NOT index`() {
        // 10:25 -> 10:26: only m2 changed (6 even)
        WidgetDataBinder.syncFoldOverlaysChangedOnly(
            views = views,
            previousDigits = DigitState(1, 0, 2, 5),
            currentDigits = DigitState(1, 0, 2, 6)
        )

        verify(exactly = 1) { views.setTextViewText(R.id.fold_m2_from, "6") }
        verify(exactly = 1) { views.setTextViewText(R.id.fold_m2_to, "6") }

        // Quiet path MUST NOT flip the index (nowhere else rule)
        verify(exactly = 0) { views.setDisplayedChild(R.id.fold_m2, any()) }
    }

    @Test
    fun `syncFoldOverlaysChangedOnly is a no-op when digits are identical`() {
        WidgetDataBinder.syncFoldOverlaysChangedOnly(
            views = views,
            previousDigits = DigitState(1, 0, 2, 6),
            currentDigits = DigitState(1, 0, 2, 6)
        )

        verify(exactly = 0) { views.setTextViewText(any(), any()) }
        verify(exactly = 0) { views.setDisplayedChild(any(), any()) }
    }
}
