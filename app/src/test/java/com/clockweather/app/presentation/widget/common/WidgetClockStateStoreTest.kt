package com.clockweather.app.presentation.widget.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WidgetClockStateStoreTest {

    private val context = RuntimeEnvironment.getApplication()

    @Before
    fun clearState() {
        WidgetClockStateStore.clearWidget(context, 1)
        WidgetClockStateStore.clearWidget(context, 2)
    }

    @Test
    fun `returns null when widget has never rendered`() {
        assertNull(WidgetClockStateStore.getLastRenderedEpochMinute(context, 1))
    }

    @Test
    fun `stores render minute per widget id`() {
        WidgetClockStateStore.markRendered(context, 1, 1234L)
        WidgetClockStateStore.markRendered(context, 2, 5678L)

        assertEquals(1234L, WidgetClockStateStore.getLastRenderedEpochMinute(context, 1))
        assertEquals(5678L, WidgetClockStateStore.getLastRenderedEpochMinute(context, 2))
    }

    @Test
    fun `clearing one widget does not affect others`() {
        WidgetClockStateStore.markRendered(context, 1, 1234L)
        WidgetClockStateStore.markRendered(context, 2, 5678L)

        WidgetClockStateStore.clearWidget(context, 1)

        assertNull(WidgetClockStateStore.getLastRenderedEpochMinute(context, 1))
        assertEquals(5678L, WidgetClockStateStore.getLastRenderedEpochMinute(context, 2))
    }
}
