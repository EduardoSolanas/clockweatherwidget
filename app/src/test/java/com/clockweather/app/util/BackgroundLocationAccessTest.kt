package com.clockweather.app.util

import android.os.Build
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The widget refreshes from a WorkManager job, which Android treats as background.
 * From Android 10 that needs the "all the time" grant, otherwise the fused provider
 * hands back nothing and the widget stays on whichever city was last seen in the
 * foreground.
 */
class BackgroundLocationAccessTest {

    @Test
    fun `android 10 and later needs the all-the-time grant`() {
        assertTrue(
            BackgroundLocationAccess.needsGrant(
                sdkInt = Build.VERSION_CODES.Q,
                foregroundGranted = true,
                backgroundGranted = false,
            )
        )
    }

    @Test
    fun `before android 10 the foreground grant already covers background access`() {
        assertFalse(
            BackgroundLocationAccess.needsGrant(
                sdkInt = Build.VERSION_CODES.P,
                foregroundGranted = true,
                backgroundGranted = false,
            )
        )
    }

    @Test
    fun `nothing to upgrade until the foreground grant is in place`() {
        assertFalse(
            BackgroundLocationAccess.needsGrant(
                sdkInt = Build.VERSION_CODES.TIRAMISU,
                foregroundGranted = false,
                backgroundGranted = false,
            )
        )
    }

    @Test
    fun `an already granted background permission needs no prompt`() {
        assertFalse(
            BackgroundLocationAccess.needsGrant(
                sdkInt = Build.VERSION_CODES.TIRAMISU,
                foregroundGranted = true,
                backgroundGranted = true,
            )
        )
    }
}
