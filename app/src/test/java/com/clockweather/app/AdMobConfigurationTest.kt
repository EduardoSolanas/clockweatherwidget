package com.clockweather.app

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A GitHub secret that was never created still reaches Gradle: the workflow expands
 * `${{ secrets.ADMOB_APP_ID }}` to an empty string rather than leaving the variable unset.
 * `System.getenv(...) ?: fallback` only falls back on null, so the empty value survives and
 * the manifest ships `android:value=""`.
 *
 * MobileAdsInitProvider rejects that id while content providers are installed, which happens
 * before Application.onCreate. The throw is fatal to the whole process, so the app, the widget
 * provider and the background worker all die on every start -- widgets freeze on their raw
 * layout and no weather is ever fetched.
 *
 * These assertions read the packaged BuildConfig, so they fail in the CI variant that would
 * ship the broken artifact rather than only on a developer machine that has local.properties.
 */
class AdMobConfigurationTest {

    @Test
    fun `the admob application id is not blank`() {
        assertTrue(
            "BuildConfig.ADMOB_APP_ID is blank, so MobileAdsInitProvider crashes every process start",
            BuildConfig.ADMOB_APP_ID.isNotBlank(),
        )
    }

    @Test
    fun `the admob application id is shaped like an admob id`() {
        assertTrue(
            "Not an AdMob application id: '${BuildConfig.ADMOB_APP_ID}'",
            BuildConfig.ADMOB_APP_ID.startsWith("ca-app-pub-"),
        )
    }

    @Test
    fun `the interstitial ad unit id is not blank`() {
        assertTrue(
            "BuildConfig.ADMOB_INTERSTITIAL_AD_UNIT_ID is blank",
            BuildConfig.ADMOB_INTERSTITIAL_AD_UNIT_ID.isNotBlank(),
        )
    }

    @Test
    fun `the interstitial ad unit id is shaped like an admob id`() {
        assertTrue(
            "Not an AdMob ad unit id: '${BuildConfig.ADMOB_INTERSTITIAL_AD_UNIT_ID}'",
            BuildConfig.ADMOB_INTERSTITIAL_AD_UNIT_ID.startsWith("ca-app-pub-"),
        )
    }
}
