package com.aaritlabs.funtoza.monetization

import android.app.Activity
import android.content.Context
import android.widget.Toast
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.aaritlabs.funtoza.model.GameType

/**
 * Manages Google Play Billing Library v7.0.0 integration for Funtoza.
 * Handles In-App Purchases (Remove Ads, VIP Bundle, Dev Tip Coffee),
 * Purchase Acknowledgement, SKU Details Caching, and Entitlement Persistence.
 */
class BillingManager private constructor(private val appContext: Context) : PurchasesUpdatedListener {

    companion object {
        const val PREFS_NAME = "funtoza_billing_prefs"
        const val KEY_VIP_NO_ADS = "is_vip_no_ads"
        const val KEY_VIP_PARTY_BUNDLE = "is_vip_party_bundle"
        const val KEY_UNLOCK_SEA_BATTLE_UNTIL = "unlock_sea_battle_until"
        const val KEY_UNLOCK_MEMORY_UNTIL = "unlock_memory_until"

        const val SKU_REMOVE_ADS = "funtoza_remove_ads"
        const val SKU_VIP_PARTY_BUNDLE = "funtoza_vip_party_bundle"
        const val SKU_TIP_COFFEE = "funtoza_tip_coffee"

        @Volatile
        private var instance: BillingManager? = null

        fun getInstance(context: Context): BillingManager {
            return instance ?: synchronized(this) {
                instance ?: BillingManager(context.applicationContext).also { instance = it }
            }
        }

        fun isNoAdsActive(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_VIP_NO_ADS, false) || prefs.getBoolean(KEY_VIP_PARTY_BUNDLE, false)
        }

        fun isVipPartyActive(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_VIP_PARTY_BUNDLE, false)
        }

        fun isGameUnlocked(context: Context, gameType: GameType): Boolean {
            if (gameType != GameType.SEA_BATTLE && gameType != GameType.MEMORY) {
                return true // 8 classic games are 100% free forever
            }
            if (isNoAdsActive(context) || isVipPartyActive(context)) {
                return true // VIP unlocks everything
            }
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val key = if (gameType == GameType.SEA_BATTLE) KEY_UNLOCK_SEA_BATTLE_UNTIL else KEY_UNLOCK_MEMORY_UNTIL
            val expireTime = prefs.getLong(key, 0L)
            return System.currentTimeMillis() < expireTime
        }

        fun getRemainingUnlockTimeHours(context: Context, gameType: GameType): Int {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val key = if (gameType == GameType.SEA_BATTLE) KEY_UNLOCK_SEA_BATTLE_UNTIL else KEY_UNLOCK_MEMORY_UNTIL
            val expireTime = prefs.getLong(key, 0L)
            val remainingMs = expireTime - System.currentTimeMillis()
            return if (remainingMs > 0) (remainingMs / (1000 * 60 * 60)).toInt() + 1 else 0
        }

        fun unlockGameFor24Hours(context: Context, gameType: GameType) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val key = if (gameType == GameType.SEA_BATTLE) KEY_UNLOCK_SEA_BATTLE_UNTIL else KEY_UNLOCK_MEMORY_UNTIL
            val unlockUntil = System.currentTimeMillis() + (24 * 60 * 60 * 1000L)
            prefs.edit().putLong(key, unlockUntil).apply()
        }
    }

    private var billingClient: BillingClient? = null
    private val cachedProductDetails = mutableMapOf<String, ProductDetails>()
    var onPurchaseCompletedListener: ((sku: String) -> Unit)? = null

    init {
        initBillingClient()
    }

    private fun initBillingClient() {
        val pendingPurchasesParams = PendingPurchasesParams.newBuilder()
            .enableOneTimeProducts()
            .build()

        billingClient = BillingClient.newBuilder(appContext)
            .setListener(this)
            .enablePendingPurchases(pendingPurchasesParams)
            .build()
    }

    fun startConnection(onConnected: (() -> Unit)? = null) {
        val client = billingClient ?: return
        if (client.isReady) {
            queryAvailableProducts(onConnected)
            return
        }

        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryAvailableProducts(onConnected)
                    restorePurchases(null)
                }
            }

            override fun onBillingServiceDisconnected() {
                // Will retry on next purchase attempt or screen load
            }
        })
    }

    private fun queryAvailableProducts(onFinished: (() -> Unit)? = null) {
        val client = billingClient ?: return
        if (!client.isReady) return

        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(SKU_REMOVE_ADS)
                .setProductType(BillingClient.ProductType.INAPP)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(SKU_VIP_PARTY_BUNDLE)
                .setProductType(BillingClient.ProductType.INAPP)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(SKU_TIP_COFFEE)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        client.queryProductDetailsAsync(params) { billingResult, productDetailsResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                cachedProductDetails.clear()
                productDetailsResult.productDetailsList.forEach { details ->
                    cachedProductDetails[details.productId] = details
                }
            }
            onFinished?.invoke()
        }
    }

    fun getFormattedPrice(sku: String, fallback: String): String {
        val details = cachedProductDetails[sku] ?: return fallback
        return details.oneTimePurchaseOfferDetails?.formattedPrice ?: fallback
    }

    fun launchPurchaseFlow(activity: Activity, sku: String) {
        val client = billingClient
        if (client == null || !client.isReady) {
            startConnection {
                launchPurchaseFlow(activity, sku)
            }
            return
        }

        val details = cachedProductDetails[sku]
        if (details == null) {
            Toast.makeText(activity, "Connecting to Google Play... please retry in a moment.", Toast.LENGTH_SHORT).show()
            queryAvailableProducts()
            return
        }

        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details)
                .build()
        )

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        client.launchBillingFlow(activity, billingFlowParams)
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                handlePurchase(purchase)
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            // User cancelled checkout, normal flow
        } else {
            Toast.makeText(appContext, "Purchase failed: ${billingResult.debugMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            val skus = purchase.products
            val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            skus.forEach { sku ->
                when (sku) {
                    SKU_REMOVE_ADS -> {
                        prefs.edit().putBoolean(KEY_VIP_NO_ADS, true).apply()
                        acknowledgePurchase(purchase)
                        onPurchaseCompletedListener?.invoke(sku)
                    }
                    SKU_VIP_PARTY_BUNDLE -> {
                        prefs.edit()
                            .putBoolean(KEY_VIP_NO_ADS, true)
                            .putBoolean(KEY_VIP_PARTY_BUNDLE, true)
                            .apply()
                        acknowledgePurchase(purchase)
                        onPurchaseCompletedListener?.invoke(sku)
                    }
                    SKU_TIP_COFFEE -> {
                        // Consumable tip - consume it so it can be purchased repeatedly
                        consumePurchase(purchase)
                        onPurchaseCompletedListener?.invoke(sku)
                    }
                }
            }
        }
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        if (!purchase.isAcknowledged) {
            val client = billingClient ?: return
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()

            client.acknowledgePurchase(params) { billingResult ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    // Purchase acknowledged successfully
                }
            }
        }
    }

    private fun consumePurchase(purchase: Purchase) {
        val client = billingClient ?: return
        val consumeParams = ConsumeParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        client.consumeAsync(consumeParams) { _, _ ->
            // Tip consumed, ready for next donation
        }
    }

    fun restorePurchases(onResult: ((restoredCount: Int) -> Unit)?) {
        val client = billingClient ?: return
        if (!client.isReady) {
            startConnection { restorePurchases(onResult) }
            return
        }

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        client.queryPurchasesAsync(params) { billingResult, purchasesList ->
            var count = 0
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                for (purchase in purchasesList) {
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        if (purchase.products.contains(SKU_REMOVE_ADS)) {
                            prefs.edit().putBoolean(KEY_VIP_NO_ADS, true).apply()
                            count++
                        }
                        if (purchase.products.contains(SKU_VIP_PARTY_BUNDLE)) {
                            prefs.edit()
                                .putBoolean(KEY_VIP_NO_ADS, true)
                                .putBoolean(KEY_VIP_PARTY_BUNDLE, true)
                                .apply()
                            count++
                        }
                        acknowledgePurchase(purchase)
                    }
                }
            }
            onResult?.invoke(count)
        }
    }
}

