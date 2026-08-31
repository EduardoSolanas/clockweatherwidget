package com.clockweather.app.data.ads

import com.clockweather.app.BuildConfig
import org.junit.Assert.assertFalse
import org.junit.Test

class AdManagerTest {

    @Test
    fun `testers are never eligible to show ads`() {
        val eligible = AdManager.isEligibleToShowAd(isTester = true)
        assertFalse(eligible)
    }

    @Test
    fun `non-testers are not eligible if no ad has been preloaded`() {
        val eligible = AdManager.isEligibleToShowAd(isTester = false)
        // With no ad preloaded, eligibility is false
        assertFalse(eligible)
    }

    @Test
    fun `ads are disabled in debug and internal tester builds`() {
        assertFalse(BuildConfig.ADS_ENABLED)
    }

    @Test
    fun `no user is eligible while ads are disabled for the build`() {
        assertFalse(AdManager.isEligibleToShowAd(isTester = false))
    }
}
