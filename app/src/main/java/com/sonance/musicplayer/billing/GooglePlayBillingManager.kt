package com.sonance.musicplayer.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manages Google Play In-App Billing for Sonance Music Player.
 * Supports:
 * - One-time In-App Purchase: "sonance_pro_lifetime" ($2.00)
 * - Auto-renewing Subscription: "sonance_pro_yearly" ($1.00/year)
 *
 * Implements Google Play Billing Library 7.x with connection retries,
 * product detail querying, purchase flow launching, purchase acknowledgment,
 * and purchase restoration.
 */
class GooglePlayBillingManager private constructor(private val context: Context) : PurchasesUpdatedListener {

    companion object {
        private const val TAG = "GooglePlayBilling"

        // Google Play Console Product IDs
        const val PRODUCT_LIFETIME = "sonance_pro_lifetime"
        const val PRODUCT_YEARLY = "sonance_pro_yearly"

        @Volatile
        private var instance: GooglePlayBillingManager? = null

        fun getInstance(context: Context): GooglePlayBillingManager {
            return instance ?: synchronized(this) {
                instance ?: GooglePlayBillingManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    private var billingClient: BillingClient? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _products = MutableStateFlow<Map<String, ProductDetails>>(emptyMap())
    val products: StateFlow<Map<String, ProductDetails>> = _products.asStateFlow()

    private val _statusMessage = MutableStateFlow("Google Play Billing Ready")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    // Callback when a purchase succeeds and is verified
    var onPurchaseCompleted: ((plan: String, price: String, orderId: String, purchaseToken: String) -> Unit)? = null

    init {
        initBillingClient()
    }

    private fun initBillingClient() {
        try {
            val pendingParams = PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()

            billingClient = BillingClient.newBuilder(context)
                .setListener(this)
                .enablePendingPurchases(pendingParams)
                .enableAutoServiceReconnection()
                .build()

            startConnection()
        } catch (e: Exception) {
            Log.e(TAG, "Error creating BillingClient", e)
            _statusMessage.value = "Billing Init Error: ${e.message}"
        }
    }

    fun startConnection() {
        val client = billingClient ?: return
        if (client.isReady) {
            _isConnected.value = true
            queryProducts()
            return
        }

        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "BillingClient connected successfully")
                    _isConnected.value = true
                    _statusMessage.value = "Connected to Google Play Billing"
                    queryProducts()
                    queryExistingPurchases()
                } else {
                    Log.w(TAG, "BillingClient setup failed with code: ${billingResult.responseCode}, ${billingResult.debugMessage}")
                    _isConnected.value = false
                    _statusMessage.value = "Google Play setup: ${billingResult.debugMessage.ifBlank { "Code ${billingResult.responseCode}" }}"
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Billing service disconnected")
                _isConnected.value = false
                _statusMessage.value = "Google Play Billing disconnected"
            }
        })
    }

    /**
     * Query product details from Google Play Console for lifetime (INAPP) and yearly (SUBS)
     */
    fun queryProducts() {
        val client = billingClient ?: return
        if (!client.isReady) {
            startConnection()
            return
        }

        coroutineScope.launch {
            val productList = listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(PRODUCT_LIFETIME)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build(),
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(PRODUCT_YEARLY)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            )

            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(productList)
                .build()

            client.queryProductDetailsAsync(params) { billingResult, queryProductDetailsResult ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    val list = queryProductDetailsResult.productDetailsList ?: emptyList()
                    val map = list.associateBy { it.productId }
                    _products.value = map
                    Log.d(TAG, "Products queried: ${map.keys}")
                } else {
                    Log.w(TAG, "queryProductDetailsAsync failed: ${billingResult.debugMessage}")
                }
            }
        }
    }

    /**
     * Launch the native Google Play purchase sheet
     */
    fun launchPurchaseFlow(
        activity: Activity,
        isYearly: Boolean,
        onFallbackSimulation: () -> Unit
    ) {
        val client = billingClient
        val targetProductId = if (isYearly) PRODUCT_YEARLY else PRODUCT_LIFETIME
        val productDetails = _products.value[targetProductId]

        if (client != null && client.isReady && productDetails != null) {
            try {
                val productDetailsParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(productDetails)

                // For subscriptions, select the first offer token if available
                if (isYearly) {
                    val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken
                    if (offerToken != null) {
                        productDetailsParamsBuilder.setOfferToken(offerToken)
                    }
                }

                val flowParams = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(listOf(productDetailsParamsBuilder.build()))
                    .build()

                val result = client.launchBillingFlow(activity, flowParams)
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    Log.w(TAG, "launchBillingFlow failed: ${result.debugMessage}")
                    onFallbackSimulation()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception launching billing flow", e)
                onFallbackSimulation()
            }
        } else {
            // When running on emulator or before Google Play Console is linked to active merchant
            Log.i(TAG, "Google Play productDetails not active yet; running fallback transaction flow")
            onFallbackSimulation()
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (purchases != null) {
                    for (purchase in purchases) {
                        handlePurchase(purchase)
                    }
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                Log.d(TAG, "User canceled the purchase")
                _statusMessage.value = "Purchase canceled"
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                Log.d(TAG, "Item already owned, querying purchases")
                _statusMessage.value = "Item already owned. Restoring access..."
                queryExistingPurchases()
            }
            else -> {
                Log.w(TAG, "Purchase failed: ${billingResult.debugMessage}")
                _statusMessage.value = "Payment result: ${billingResult.debugMessage.ifBlank { "Code ${billingResult.responseCode}" }}"
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            val isYearly = purchase.products.contains(PRODUCT_YEARLY)
            val plan = if (isYearly) "yearly" else "lifetime"
            val price = if (isYearly) "$1.00/yr" else "$2.00"
            val orderId = purchase.orderId ?: "GPA.${System.currentTimeMillis()}"
            val token = purchase.purchaseToken

            // Acknowledge the purchase if not already acknowledged
            if (!purchase.isAcknowledged) {
                val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()

                coroutineScope.launch {
                    val client = billingClient ?: return@launch
                    client.acknowledgePurchase(acknowledgePurchaseParams) { ackResult ->
                        if (ackResult.responseCode == BillingClient.BillingResponseCode.OK) {
                            Log.d(TAG, "Purchase acknowledged successfully")
                            coroutineScope.launch(Dispatchers.Main) {
                                onPurchaseCompleted?.invoke(plan, price, orderId, token)
                            }
                        } else {
                            Log.w(TAG, "Acknowledge failed: ${ackResult.debugMessage}")
                        }
                    }
                }
            } else {
                coroutineScope.launch(Dispatchers.Main) {
                    onPurchaseCompleted?.invoke(plan, price, orderId, token)
                }
            }
        }
    }

    /**
     * Restores existing purchases for user
     */
    fun queryExistingPurchases(onResult: ((Boolean, String) -> Unit)? = null) {
        val client = billingClient ?: return
        if (!client.isReady) {
            onResult?.invoke(false, "Google Play Billing not connected")
            return
        }

        coroutineScope.launch {
            var foundPro = false
            var details = "No active Google Play purchases found"

            // Check In-App (Lifetime)
            val inAppParams = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()

            client.queryPurchasesAsync(inAppParams) { res, purchases ->
                if (res.responseCode == BillingClient.BillingResponseCode.OK) {
                    val valid = purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                    val lifetime = valid.find { it.products.contains(PRODUCT_LIFETIME) }
                    if (lifetime != null) {
                        foundPro = true
                        details = "Restored Lifetime Pro license"
                        handlePurchase(lifetime)
                    }
                }

                // Check Subs (Yearly)
                val subsParams = QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()

                client.queryPurchasesAsync(subsParams) { subRes, subPurchases ->
                    if (subRes.responseCode == BillingClient.BillingResponseCode.OK) {
                        val validSubs = subPurchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                        val yearly = validSubs.find { it.products.contains(PRODUCT_YEARLY) }
                        if (yearly != null) {
                            foundPro = true
                            details = "Restored Yearly Pro subscription"
                            handlePurchase(yearly)
                        }
                    }

                    coroutineScope.launch(Dispatchers.Main) {
                        onResult?.invoke(foundPro, details)
                    }
                }
            }
        }
    }
}
