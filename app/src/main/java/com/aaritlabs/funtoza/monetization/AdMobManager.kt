package com.aaritlabs.funtoza.monetization

import android.app.Activity
import android.content.Context
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.view.ViewGroup
import com.google.android.gms.ads.*
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/**
 * Manages Google Mobile Ads (AdMob) for Funtoza.
 * Supports production ad units with automatic fallback to Google Sample Test Units
 * whenever new ad units are pending activation or experience low fill.
 */
class AdMobManager private constructor(private val appContext: Context) {

    companion object {
        private const val TAG = "AdMobManager"

        // Production Google AdMob Ad Unit IDs
        const val PROD_BANNER = "ca-app-pub-4917639294278231/1447894057"
        const val PROD_INTERSTITIAL = "ca-app-pub-4917639294278231/7872692444"
        const val PROD_REWARDED = "ca-app-pub-4917639294278231/9813681265"
        const val PROD_APP_OPEN = "ca-app-pub-4917639294278231/6915843150"

        // Official Google Sample Test Ad Unit IDs (100% Guaranteed Fill)
        const val TEST_BANNER = "ca-app-pub-3940256099942544/6300978111"
        const val TEST_INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"
        const val TEST_REWARDED = "ca-app-pub-3940256099942544/5224354917"
        const val TEST_APP_OPEN = "ca-app-pub-3940256099942544/9257395921"

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

    private var isInterstitialLoading = false
    private var isRewardedLoading = false
    private var isAppOpenLoading = false

    private var lastAppOpenShownTime = 0L
    private var lastInterstitialShownTime = 0L
    private var isShowingAppOpenAd = false
    private var isInitialized = false

    fun initialize(context: Context) {
        if (isInitialized) return
        MobileAds.initialize(context) { status ->
            isInitialized = true
            Log.d(TAG, "AdMob MobileAds SDK initialized successfully: $status")
            loadAppOpenAd()
            loadInterstitial()
            loadRewarded()
        }
    }

    // =========================================================================
    // 1. ADAPTIVE BANNER ADS (WITH AUTOMATIC TEST FALLBACK)
    // =========================================================================

    fun loadBanner(activity: Activity, container: ViewGroup) {
        if (BillingManager.isNoAdsActive(activity)) {
            Log.d(TAG, "VIP Ad-Free is active! Suppressing banner.")
            container.visibility = View.GONE
            container.removeAllViews()
            return
        }

        container.visibility = View.VISIBLE
        loadBannerInternal(activity, container, useFallback = false)
    }

    private fun loadBannerInternal(activity: Activity, container: ViewGroup, useFallback: Boolean) {
        val adUnitId = if (useFallback) TEST_BANNER else PROD_BANNER
        val adView = AdView(activity).apply {
            this.adUnitId = adUnitId
        }
        container.removeAllViews()
        container.addView(adView)

        val displayMetrics = activity.resources.displayMetrics
        val adWidthPixels = if (container.width > 0) container.width.toFloat() else displayMetrics.widthPixels.toFloat()
        val adWidth = (adWidthPixels / displayMetrics.density).toInt()
        val adSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, adWidth)
        adView.setAdSize(adSize)

        adView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                Log.d(TAG, "Banner loaded successfully (${if (useFallback) "TEST" else "PROD"} ID: $adUnitId)")
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                Log.w(TAG, "Banner load failed (${if (useFallback) "TEST" else "PROD"} ID: $adUnitId): [${error.code}] ${error.message}")
                if (!useFallback) {
                    Log.i(TAG, "Switching to Google Sample Test Banner fallback...")
                    loadBannerInternal(activity, container, useFallback = true)
                }
            }
        }

        val adRequest = AdRequest.Builder().build()
        adView.loadAd(adRequest)
    }

    // =========================================================================
    // 2. PACED INTERSTITIAL ADS (WITH AUTOMATIC TEST FALLBACK)
    // =========================================================================

    fun loadInterstitial(useFallback: Boolean = false, onLoaded: ((InterstitialAd?) -> Unit)? = null) {
        if (BillingManager.isNoAdsActive(appContext)) {
            Log.d(TAG, "VIP Ad-Free is active: Skipping interstitial load.")
            onLoaded?.invoke(null)
            return
        }
        val cached = interstitialAd
        if (cached != null) {
            onLoaded?.invoke(cached)
            return
        }
        if (isInterstitialLoading && !useFallback) return

        isInterstitialLoading = true
        val adUnitId = if (useFallback) TEST_INTERSTITIAL else PROD_INTERSTITIAL
        val adRequest = AdRequest.Builder().build()

        InterstitialAd.load(
            appContext,
            adUnitId,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.d(TAG, "Interstitial loaded successfully (${if (useFallback) "TEST" else "PROD"} ID: $adUnitId)")
                    interstitialAd = ad
                    isInterstitialLoading = false
                    onLoaded?.invoke(ad)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "Interstitial load failed (${if (useFallback) "TEST" else "PROD"} ID: $adUnitId): [${error.code}] ${error.message}")
                    isInterstitialLoading = false
                    if (!useFallback) {
                        Log.i(TAG, "Switching to Google Sample Test Interstitial fallback...")
                        loadInterstitial(useFallback = true, onLoaded = onLoaded)
                    } else {
                        interstitialAd = null
                        onLoaded?.invoke(null)
                    }
                }
            }
        )
    }

    fun showInterstitial(activity: Activity, force: Boolean = true, onDismiss: () -> Unit) {
        if (BillingManager.isNoAdsActive(activity)) {
            Log.d(TAG, "VIP Ad-Free is active! Suppressing interstitial.")
            onDismiss()
            return
        }

        val elapsed = System.currentTimeMillis() - lastInterstitialShownTime
        if (!force && elapsed < INTERSTITIAL_COOLDOWN_MS) {
            Log.d(TAG, "Interstitial cooldown active (${elapsed / 1000}s < ${INTERSTITIAL_COOLDOWN_MS / 1000}s). Skipping.")
            onDismiss()
            loadInterstitial()
            return
        }

        val ad = interstitialAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d(TAG, "Interstitial dismissed.")
                    interstitialAd = null
                    lastInterstitialShownTime = System.currentTimeMillis()
                    loadInterstitial()
                    onDismiss()
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    Log.w(TAG, "Interstitial failed to show: [${error.code}] ${error.message}")
                    interstitialAd = null
                    loadInterstitial()
                    onDismiss()
                }
            }
            ad.show(activity)
        } else {
            Log.d(TAG, "Interstitial not preloaded, fetching on-demand with test fallback...")
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
    // 3. REWARDED VIDEO ADS (WITH AUTOMATIC TEST FALLBACK)
    // =========================================================================

    fun loadRewarded(useFallback: Boolean = false, onLoaded: ((RewardedAd?) -> Unit)? = null) {
        val cached = rewardedAd
        if (cached != null) {
            onLoaded?.invoke(cached)
            return
        }
        if (isRewardedLoading && !useFallback) return

        isRewardedLoading = true
        val adUnitId = if (useFallback) TEST_REWARDED else PROD_REWARDED
        val adRequest = AdRequest.Builder().build()

        RewardedAd.load(
            appContext,
            adUnitId,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    Log.d(TAG, "Rewarded video loaded successfully (${if (useFallback) "TEST" else "PROD"} ID: $adUnitId)")
                    rewardedAd = ad
                    isRewardedLoading = false
                    onLoaded?.invoke(ad)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "Rewarded video load failed (${if (useFallback) "TEST" else "PROD"} ID: $adUnitId): [${error.code}] ${error.message}")
                    isRewardedLoading = false
                    if (!useFallback) {
                        Log.i(TAG, "Switching to Google Sample Test Rewarded Video fallback...")
                        loadRewarded(useFallback = true, onLoaded = onLoaded)
                    } else {
                        rewardedAd = null
                        onLoaded?.invoke(null)
                    }
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
            Log.d(TAG, "Rewarded ad not cached, fetching on-demand with test fallback...")
            loadRewarded { freshAd ->
                if (freshAd != null && !activity.isFinishing && !activity.isDestroyed) {
                    displayRewarded(activity, freshAd, onRewardEarned, onDismiss)
                } else {
                    onDismiss?.invoke()
                }
            }
            return
        }

        displayRewarded(activity, ad, onRewardEarned, onDismiss)
    }

    private fun displayRewarded(
        activity: Activity,
        ad: RewardedAd,
        onRewardEarned: () -> Unit,
        onDismiss: (() -> Unit)?
    ) {
        var earned = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Rewarded video dismissed. Reward earned = $earned")
                rewardedAd = null
                loadRewarded()
                if (earned) {
                    onRewardEarned()
                }
                onDismiss?.invoke()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.w(TAG, "Rewarded video failed to show: [${error.code}] ${error.message}")
                rewardedAd = null
                loadRewarded()
                onDismiss?.invoke()
            }
        }

        ad.show(activity) { _ ->
            earned = true
            Log.d(TAG, "User earned rewarded video reward!")
        }
    }

    // =========================================================================
    // 4. APP OPEN ADS (WITH AUTOMATIC TEST FALLBACK)
    // =========================================================================

    fun loadAppOpenAd(useFallback: Boolean = false) {
        if (BillingManager.isNoAdsActive(appContext)) {
            Log.d(TAG, "VIP Ad-Free is active: Skipping App Open ad load.")
            return
        }
        if (appOpenAd != null) return
        if (isAppOpenLoading && !useFallback) return

        isAppOpenLoading = true
        val adUnitId = if (useFallback) TEST_APP_OPEN else PROD_APP_OPEN
        val adRequest = AdRequest.Builder().build()

        AppOpenAd.load(
            appContext,
            adUnitId,
            adRequest,
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    Log.d(TAG, "App Open Ad loaded successfully (${if (useFallback) "TEST" else "PROD"} ID: $adUnitId)")
                    appOpenAd = ad
                    isAppOpenLoading = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "App Open Ad load failed (${if (useFallback) "TEST" else "PROD"} ID: $adUnitId): [${error.code}] ${error.message}")
                    isAppOpenLoading = false
                    if (!useFallback) {
                        Log.i(TAG, "Switching to Google Sample Test App Open Ad fallback...")
                        loadAppOpenAd(useFallback = true)
                    } else {
                        appOpenAd = null
                    }
                }
            }
        )
    }

    fun showAppOpenAdIfAvailable(activity: Activity, onComplete: (() -> Unit)? = null) {
        if (BillingManager.isNoAdsActive(activity)) {
            Log.d(TAG, "VIP Ad-Free is active: Suppressing App Open ad.")
            onComplete?.invoke()
            return
        }

        if (isShowingAppOpenAd) {
            onComplete?.invoke()
            return
        }

        val elapsed = System.currentTimeMillis() - lastAppOpenShownTime
        if (elapsed < APP_OPEN_COOLDOWN_MS) {
            onComplete?.invoke()
            return
        }

        val ad = appOpenAd
        if (ad != null) {
            isShowingAppOpenAd = true
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d(TAG, "App Open Ad dismissed.")
                    appOpenAd = null
                    isShowingAppOpenAd = false
                    lastAppOpenShownTime = System.currentTimeMillis()
                    loadAppOpenAd()
                    onComplete?.invoke()
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    Log.w(TAG, "App Open Ad failed to show: [${error.code}] ${error.message}")
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
