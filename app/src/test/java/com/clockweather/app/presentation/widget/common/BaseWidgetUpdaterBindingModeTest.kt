package com.clockweather.app.presentation.widget.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BaseWidgetUpdaterBindingModeTest {

    @Test
    fun `first render never uses incremental clock binding`() {
        assertFalse(
            shouldUseIncrementalClockBinding(
                isFirstRender = true,
                isMinuteTick = true
            )
        )
    }

    @Test
    fun `non minute refresh never uses incremental clock binding`() {
        assertFalse(
            shouldUseIncrementalClockBinding(
                isFirstRender = false,
                isMinuteTick = false
            )
        )
    }

    @Test
    fun `minute tick on existing widget uses incremental clock binding`() {
        assertTrue(
            shouldUseIncrementalClockBinding(
                isFirstRender = false,
                isMinuteTick = true
            )
        )
    }
}
