package com.aaritlabs.funtoza

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.aaritlabs.funtoza.monetization.AdMobManager
import com.aaritlabs.funtoza.monetization.BillingManager

/**
 * Application class for Funtoza.
 * Initializes Play Billing, AdMob SDK, and tracks app foreground lifecycle
 * for App Open Ads (paced to 15-minute intervals).
 */
class FuntozaApp : Application(), Application.ActivityLifecycleCallbacks, DefaultLifecycleObserver {

    private var currentActivity: Activity? = null

    override fun onCreate() {
        super<Application>.onCreate()
        registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        // Initialize Play Billing & restore existing purchases
        BillingManager.getInstance(this).startConnection()

        // Initialize Google Mobile Ads SDK
        AdMobManager.getInstance(this).initialize(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        // Trigger App Open Ad on foreground resume if 15 minutes elapsed
        currentActivity?.let { activity ->
            // Do not show App Open Ad over active gameplay
            if (activity !is GameActivity) {
                AdMobManager.getInstance(this).showAppOpenAdIfAvailable(activity)
            }
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {
        currentActivity = activity
    }
    override fun onActivityResumed(activity: Activity) {
        currentActivity = activity
    }
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivity == activity) {
            currentActivity = null
        }
    }
}

