package com.clockweather.app.presentation.settings

import android.content.Intent
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class SettingsActivityScrollIntentTest {

    @Test
    fun `buildScrollToBatteryIntent includes scroll-to-battery extra`() {
        val context = RuntimeEnvironment.getApplication()
        val intent = SettingsActivity.buildScrollToBatteryIntent(context)

        assertTrue(
            "Intent should contain EXTRA_SCROLL_TO_BATTERY = true",
            intent.getBooleanExtra(SettingsActivity.EXTRA_SCROLL_TO_BATTERY, false)
        )
    }
}
