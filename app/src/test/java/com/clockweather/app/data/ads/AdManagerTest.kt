package com.clockweather.app.data.ads

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
}
