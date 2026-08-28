package com.clockweather.app.data.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.clockweather.app.BuildConfig
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages Google AdMob Interstitial Ad lifecycle, User Messaging Platform (UMP) GDPR
 * consent flow, frequency capping, and tester exemptions.
 */
object AdManager {

    private const val TAG = "AdManager"

    /** Minimum interval between full-page interstitial ads to prevent user disruption (3 minutes). */
    private const val MIN_INTERSTITIAL_INTERVAL_MS = 3 * 60 * 1000L

    private var interstitialAd: InterstitialAd? = null
    private var isAdLoading = false
    private var isMobileAdsInitialized = AtomicBoolean(false)
    private var lastAdShownTimestamp: Long = 0L

    /**
     * Initializes UMP consent information and Google Mobile Ads SDK on activity startup.
     */
    fun initialize(activity: Activity) {
        val consentInformation = UserMessagingPlatform.getConsentInformation(activity)
        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .build()

        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) {
                        Log.w(TAG, "Consent form error: ${formError.message}")
                    }
                    if (consentInformation.canRequestAds()) {
                        initMobileAds(activity.applicationContext)
                    }
                }
            },
            { requestConsentError ->
                Log.w(TAG, "Consent info update failed: ${requestConsentError.message}")
                if (consentInformation.canRequestAds()) {
                    initMobileAds(activity.applicationContext)
                }
            }
        )

        if (consentInformation.canRequestAds()) {
            initMobileAds(activity.applicationContext)
        }
    }

    private fun initMobileAds(context: Context) {
        if (isMobileAdsInitialized.getAndSet(true)) return
        MobileAds.initialize(context) {
            Log.d(TAG, "Google Mobile Ads initialized successfully.")
            preloadInterstitial(context)
        }
    }

    /**
     * Preloads an interstitial ad into memory so it is ready for instant display.
     */
    fun preloadInterstitial(context: Context) {
        if (interstitialAd != null || isAdLoading) return

        val adUnitId = BuildConfig.ADMOB_INTERSTITIAL_AD_UNIT_ID.ifEmpty {
            "ca-app-pub-3940256099942544/1033173712" // Fallback to Google sample interstitial ID
        }

        isAdLoading = true
        val adRequest = AdRequest.Builder().build()

        InterstitialAd.load(
            context,
            adUnitId,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    isAdLoading = false
                    Log.d(TAG, "Interstitial ad loaded successfully.")
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    interstitialAd = null
                    isAdLoading = false
                    Log.w(TAG, "Failed to load interstitial ad: ${loadAdError.message}")
                }
            }
        )
    }

    /**
     * Checks whether the current session is eligible to show an interstitial ad.
     */
    fun isEligibleToShowAd(isTester: Boolean): Boolean {
        if (isTester) {
            Log.d(TAG, "User is a tester: ad suppressed.")
            return false
        }

        val now = System.currentTimeMillis()
        if (now - lastAdShownTimestamp < MIN_INTERSTITIAL_INTERVAL_MS) {
            Log.d(TAG, "Cooldown active: ad suppressed.")
            return false
        }

        return interstitialAd != null
    }

    /**
     * Displays the full-page interstitial ad if available and eligible.
     * AdMob automatically manages the countdown and skip/close ("X") button on the top corner.
     *
     * @param activity The host activity.
     * @param isTester True if the user is a designated tester or has tester mode enabled.
     * @param onDismissed Invoked when the ad is closed, skipped, or if no ad was shown.
     */
    fun showInterstitialAd(
        activity: Activity,
        isTester: Boolean = false,
        onDismissed: () -> Unit = {}
    ) {
        if (!isEligibleToShowAd(isTester)) {
            preloadInterstitial(activity.applicationContext)
            onDismissed()
            return
        }

        val ad = interstitialAd
        interstitialAd = null

        ad?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                lastAdShownTimestamp = System.currentTimeMillis()
                Log.d(TAG, "Interstitial ad showed full screen content.")
            }

            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Interstitial ad dismissed by user.")
                preloadInterstitial(activity.applicationContext)
                onDismissed()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.w(TAG, "Interstitial ad failed to show: ${adError.message}")
                preloadInterstitial(activity.applicationContext)
                onDismissed()
            }
        }

        ad?.show(activity) ?: onDismissed()
    }
}
