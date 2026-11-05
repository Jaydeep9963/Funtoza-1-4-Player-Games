package com.aaritlabs.funtoza.monetization

import android.app.Activity
import android.content.Context
import android.util.DisplayMetrics
import android.view.View
import android.view.ViewGroup
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/**
 * Manages Google Mobile Ads (AdMob) for Funtoza.
 * Handles:
 * 1. Adaptive Banners in Lobby & Selection screens.
 * 2. Paced Interstitial Ads (Max 1 per 3 minutes on match completion).
 * 3. Rewarded Video Ads for unlocking Sea Battle ⚓ and Memory 🧠 for 24 hours.
 * 4. App Open Ads (Cold launch & resume paced to max 1 per 15 minutes).
 * 5. Automatic suppression of all ads if the user has VIP Ad-Free.
 */
class AdMobManager private constructor(private val appContext: Context) {

    companion object {
        // Google AdMob Ad Unit IDs
        const val AD_UNIT_BANNER = "ca-app-pub-4917639294278231/1447894057"
        const val AD_UNIT_INTERSTITIAL = "ca-app-pub-4917639294278231/7872692444"
        const val AD_UNIT_REWARDED = "ca-app-pub-4917639294278231/9813681265"
        const val AD_UNIT_APP_OPEN = "ca-app-pub-4917639294278231/6915843150"

        // Pacing Cooldowns
        private const val APP_OPEN_COOLDOWN_MS = 15 * 60 * 1000L // 15 Minutes
        private const val INTERSTITIAL_COOLDOWN_MS = 45 * 1000L // 45 Seconds standard pacing

        @Volatile
        private var instance: AdMobManager? = null

        fun getInstance(context: Context): AdMobManager {
            return instance ?: synchronized(this) {
                instance ?: AdMobManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null
    private var appOpenAd: AppOpenAd? = null

    private var lastAppOpenShownTime = 0L
    private var lastInterstitialShownTime = 0L
    private var isShowingAppOpenAd = false
    private var isInitialized = false

    fun initialize(context: Context) {
        if (isInitialized) return
        MobileAds.initialize(context) {
            isInitialized = true
            loadAppOpenAd()
            loadInterstitial()
            loadRewarded()
        }
    }

    // =========================================================================
    // 1. ADAPTIVE BANNER ADS
    // =========================================================================

    fun loadBanner(activity: Activity, container: ViewGroup) {
        if (BillingManager.isNoAdsActive(activity)) {
            container.visibility = View.GONE
            container.removeAllViews()
            return
        }

        container.visibility = View.VISIBLE
        container.removeAllViews()

        val adView = AdView(activity)
        adView.adUnitId = AD_UNIT_BANNER
        container.addView(adView)

        val display = activity.windowManager.defaultDisplay
        val outMetrics = DisplayMetrics()
        display.getMetrics(outMetrics)

        val density = outMetrics.density
        val adWidthPixels = if (container.width > 0) container.width.toFloat() else outMetrics.widthPixels.toFloat()
        val adWidth = (adWidthPixels / density).toInt()

        val adSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, adWidth)
        adView.setAdSize(adSize)

        val adRequest = AdRequest.Builder().build()
        adView.loadAd(adRequest)
    }

    // =========================================================================
    // 2. PACED INTERSTITIAL ADS
    // =========================================================================

    private var isInterstitialLoading = false

    fun loadInterstitial(onLoaded: ((InterstitialAd?) -> Unit)? = null) {
        if (BillingManager.isNoAdsActive(appContext)) {
            onLoaded?.invoke(null)
            return
        }
        val cachedAd = interstitialAd
        if (cachedAd != null) {
            onLoaded?.invoke(cachedAd)
            return
        }
        if (isInterstitialLoading) return

        isInterstitialLoading = true
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            appContext,
            AD_UNIT_INTERSTITIAL,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    isInterstitialLoading = false
                    onLoaded?.invoke(ad)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                    isInterstitialLoading = false
                    onLoaded?.invoke(null)
                }
            }
        )
    }

    fun showInterstitial(activity: Activity, force: Boolean = true, onDismiss: () -> Unit) {
        if (BillingManager.isNoAdsActive(activity)) {
            onDismiss()
            return
        }

        val elapsed = System.currentTimeMillis() - lastInterstitialShownTime
        if (!force && elapsed < INTERSTITIAL_COOLDOWN_MS) {
            onDismiss()
            loadInterstitial()
            return
        }

        val ad = interstitialAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    interstitialAd = null
                    lastInterstitialShownTime = System.currentTimeMillis()
                    loadInterstitial()
                    onDismiss()
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    interstitialAd = null
                    loadInterstitial()
                    onDismiss()
                }
            }
            ad.show(activity)
        } else {
            // If ad is not preloaded yet, fetch and show immediately
            loadInterstitial { freshAd ->
                if (freshAd != null && !activity.isFinishing && !activity.isDestroyed) {
                    freshAd.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            interstitialAd = null
                            lastInterstitialShownTime = System.currentTimeMillis()
                            loadInterstitial()
                            onDismiss()
                        }

                        override fun onAdFailedToShowFullScreenContent(error: AdError) {
                            interstitialAd = null
                            loadInterstitial()
                            onDismiss()
                        }
                    }
                    freshAd.show(activity)
                } else {
                    onDismiss()
                }
            }
        }
    }

    // =========================================================================
    // 3. REWARDED VIDEO ADS (UNLOCK MINIGAMES / BONUS CUP REPLAYS)
    // =========================================================================

    fun loadRewarded() {
        if (rewardedAd != null) return

        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(
            appContext,
            AD_UNIT_REWARDED,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                }
            }
        )
    }

    fun isRewardedAdReady(): Boolean = rewardedAd != null

    fun showRewarded(
        activity: Activity,
        onRewardEarned: () -> Unit,
        onDismiss: (() -> Unit)? = null
    ) {
        val ad = rewardedAd
        if (ad == null) {
            loadRewarded()
            onDismiss?.invoke()
            return
        }

        var earned = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                loadRewarded()
                if (earned) {
                    onRewardEarned()
                }
                onDismiss?.invoke()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                rewardedAd = null
                loadRewarded()
                onDismiss?.invoke()
            }
        }

        ad.show(activity) { _ ->
            earned = true
        }
    }

    // =========================================================================
    // 4. APP OPEN ADS (PACED TO MAX 1 PER 15 MINUTES)
    // =========================================================================

    fun loadAppOpenAd() {
        if (BillingManager.isNoAdsActive(appContext)) return
        if (appOpenAd != null) return

        val adRequest = AdRequest.Builder().build()
        AppOpenAd.load(
            appContext,
            AD_UNIT_APP_OPEN,
            adRequest,
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpenAd = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    appOpenAd = null
                }
            }
        )
    }

    fun showAppOpenAdIfAvailable(activity: Activity, onComplete: (() -> Unit)? = null) {
        if (BillingManager.isNoAdsActive(activity)) {
            onComplete?.invoke()
            return
        }

        if (isShowingAppOpenAd) {
            onComplete?.invoke()
            return
        }

        val elapsed = System.currentTimeMillis() - lastAppOpenShownTime
        if (elapsed < APP_OPEN_COOLDOWN_MS) {
            // Less than 15 minutes since last App Open Ad
            onComplete?.invoke()
            return
        }

        val ad = appOpenAd
        if (ad != null) {
            isShowingAppOpenAd = true
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    appOpenAd = null
                    isShowingAppOpenAd = false
                    lastAppOpenShownTime = System.currentTimeMillis()
                    loadAppOpenAd()
                    onComplete?.invoke()
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    appOpenAd = null
                    isShowingAppOpenAd = false
                    loadAppOpenAd()
                    onComplete?.invoke()
                }
            }
            ad.show(activity)
        } else {
            loadAppOpenAd()
            onComplete?.invoke()
        }
    }
}

