package com.formlogic.utils

import android.app.Activity
import android.content.Context
import androidx.lifecycle.MutableLiveData
import com.formlogic.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * SubscriptionManager — Kotlin port of utils/subscriptions.ts
 *
 * Wraps the Google Play Billing / RevenueCat SDK behind a clean interface.
 *
 * ── Production Setup (RevenueCat) ────────────────────────────────────────────
 * 1. Add to app/build.gradle.kts:
 *      implementation("com.revenuecat.purchases:purchases:7.10.1")
 *      implementation("com.revenuecat.purchases:purchases-ui:7.10.1")
 *
 * 2. Add to AndroidManifest.xml:
 *      <uses-permission android:name="com.android.vending.BILLING" />
 *
 * 3. Add to local.properties:
 *      REVENUECAT_API_KEY=appl_xxx
 *
 * 4. Uncomment the RevenueCat blocks below (marked TODO_RC).
 *
 * ── Architecture ──────────────────────────────────────────────────────────────
 * SubscriptionManager.initialize() → called once in FormLogicApp.onCreate()
 * PaywallScreen → onPurchase() → SubscriptionManager.purchase()
 * App startup / after login → SubscriptionManager.refresh()
 */
object SubscriptionManager {

    data class Package(val identifier: String, val priceString: String, val productId: String)

    private val _isPro      = MutableStateFlow(false)
    private val _isLoading  = MutableStateFlow(false)
    private val _packages   = MutableStateFlow<List<Package>>(emptyList())

    val isPro:     StateFlow<Boolean>       = _isPro.asStateFlow()
    val isLoading: StateFlow<Boolean>       = _isLoading.asStateFlow()
    val packages:  StateFlow<List<Package>> = _packages.asStateFlow()

    /**
     * initialize — call once at app startup after user login.
     * Original: initialiseSubscriptions(userId)
     */
    suspend fun initialize(context: Context, userId: String?) {
        _isLoading.value = true
        // TODO_RC:
        // Purchases.configure(
        //     PurchasesConfiguration.Builder(context, BuildConfig.REVENUECAT_API_KEY)
        //         .appUserID(userId)
        //         .build()
        // )
        // val offerings = Purchases.sharedInstance.awaitOfferings()
        // _packages.value = offerings.current?.availablePackages?.map { pkg ->
        //     Package(pkg.identifier, pkg.product.formattedPrice, pkg.product.productId)
        // } ?: emptyList()
        // refresh()
        _isLoading.value = false
    }

    /**
     * purchase — trigger a purchase flow for a given package.
     * Original: purchasePackage(pkg)
     */
    suspend fun purchase(activity: Activity, pkg: Package): Result<Unit> {
        return try {
            // TODO_RC:
            // val rcPackage = Purchases.sharedInstance.awaitOfferings()
            //     .current?.availablePackages?.find { it.identifier == pkg.identifier }
            //     ?: return Result.failure(Exception("Package not found"))
            // Purchases.sharedInstance.awaitPurchase(activity, rcPackage)
            // _isPro.value = true
            AnalyticsManager.trackSubscriptionStarted(pkg.productId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * restorePurchases — restore previous subscriptions.
     * Original: restorePurchases()
     */
    suspend fun restorePurchases(): Result<Unit> {
        return try {
            // TODO_RC:
            // val customerInfo = Purchases.sharedInstance.awaitRestore()
            // _isPro.value = customerInfo.entitlements["pro"]?.isActive == true
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * refresh — sync subscription status from RevenueCat.
     * Original: refresh()
     */
    suspend fun refresh() {
        // TODO_RC:
        // val customerInfo = Purchases.sharedInstance.awaitCustomerInfo()
        // _isPro.value = customerInfo.entitlements["pro"]?.isActive == true
    }

    /**
     * logout — clear RevenueCat identity on user logout.
     */
    suspend fun logout() {
        _isPro.value = false
        // TODO_RC: Purchases.sharedInstance.logOut()
    }
}
