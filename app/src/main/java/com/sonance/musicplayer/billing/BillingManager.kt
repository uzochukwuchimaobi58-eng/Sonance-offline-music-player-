package com.sonance.musicplayer.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Official Google Play Billing Manager for Gee Music / Sonance Music Player.
 * Uses Google Play Billing Library SDK (v7/8) with coroutines.
 *
 * Supported Product IDs:
 * - Lifetime in-app purchase: "gee_music_lifetime"
 * - Yearly subscription: "gee_music_yearly"
 */
class BillingManager private constructor(private val context: Context) : PurchasesUpdatedListener {

    companion object {
        private const val TAG = "BillingManager"

        // Official requested Product IDs
        const val PRODUCT_LIFETIME = "gee_music_lifetime"
        const val PRODUCT_YEARLY = "gee_music_yearly"

        // Legacy / fallback product IDs for backwards compatibility
        const val LEGACY_PRODUCT_LIFETIME = "sonance_pro_lifetime"
        const val LEGACY_PRODUCT_YEARLY = "sonance_pro_yearly"

        val INAPP_PRODUCT_IDS = listOf(
            PRODUCT_LIFETIME,
            LEGACY_PRODUCT_LIFETIME,
            "pro_lifetime",
            "lifetime"
        )

        val SUBS_PRODUCT_IDS = listOf(
            PRODUCT_YEARLY,
            LEGACY_PRODUCT_YEARLY,
            "pro_yearly",
            "yearly"
        )

        @Volatile
        private var instance: BillingManager? = null

        fun getInstance(context: Context): BillingManager {
            return instance ?: synchronized(this) {
                instance ?: BillingManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Billing coroutine error safely handled: ${throwable.message}", throwable)
    }
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + coroutineExceptionHandler)

    private var billingClient: BillingClient? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    private val _products = MutableStateFlow<Map<String, ProductDetails>>(emptyMap())
    val products: StateFlow<Map<String, ProductDetails>> = _products.asStateFlow()

    private val _statusMessage = MutableStateFlow("Google Play Billing Ready")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _activePlan = MutableStateFlow<String?>("free")
    val activePlan: StateFlow<String?> = _activePlan.asStateFlow()

    // Callbacks for UI updates
    var onPurchaseCompleted: ((plan: String, price: String, orderId: String, purchaseToken: String) -> Unit)? = null
    var onPurchaseFailed: ((reason: String) -> Unit)? = null
    var onPurchasesRestored: ((success: Boolean, message: String) -> Unit)? = null

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
            Log.e(TAG, "Error initializing BillingClient", e)
            _statusMessage.value = "Billing initialization error: ${e.message}"
        }
    }

    fun startConnection(onConnected: (() -> Unit)? = null) {
        val client = billingClient ?: return
        if (client.isReady) {
            _isConnected.value = true
            queryProductDetails()
            queryPurchases()
            onConnected?.invoke()
            return
        }

        try {
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        Log.d(TAG, "Google Play Billing setup succeeded")
                        _isConnected.value = true
                        _statusMessage.value = "Connected to Google Play Billing"
                        queryProductDetails()
                        queryPurchases()
                        onConnected?.invoke()
                    } else {
                        Log.w(TAG, "Google Play Billing setup failed: ${billingResult.responseCode}, ${billingResult.debugMessage}")
                        _isConnected.value = false
                        _statusMessage.value = "Google Play setup failed: ${billingResult.debugMessage.ifBlank { "Code ${billingResult.responseCode}" }}"
                    }
                }

                override fun onBillingServiceDisconnected() {
                    Log.w(TAG, "Google Play Billing service disconnected")
                    _isConnected.value = false
                    _statusMessage.value = "Google Play Billing disconnected"
                }
            })
        } catch (t: Throwable) {
            Log.e(TAG, "Error connecting to BillingClient", t)
            _isConnected.value = false
            _statusMessage.value = "Billing connection error: ${t.message}"
        }
    }

    /**
     * Query product details for lifetime (INAPP) and yearly (SUBS)
     */
    fun queryProductDetails() {
        val client = billingClient ?: return
        if (!client.isReady) {
            startConnection()
            return
        }

        coroutineScope.launch {
            try {
                // 1. Query INAPP products (Lifetime)
                val inAppList = INAPP_PRODUCT_IDS.distinct().map { id ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(id)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                }
                val inAppParams = QueryProductDetailsParams.newBuilder()
                    .setProductList(inAppList)
                    .build()

                client.queryProductDetailsAsync(inAppParams) { billingResult, queryProductDetailsResult ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        val list = queryProductDetailsResult.productDetailsList ?: emptyList()
                        val current = _products.value.toMutableMap()
                        list.forEach { current[it.productId] = it }
                        _products.value = current
                        Log.d(TAG, "Queried ${list.size} INAPP products from Google Play: ${list.map { it.productId }}")
                    } else {
                        Log.w(TAG, "Failed querying INAPP products: ${billingResult.debugMessage}")
                    }
                }

                // 2. Query SUBS products (Yearly)
                val subsList = SUBS_PRODUCT_IDS.distinct().map { id ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(id)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                }
                val subsParams = QueryProductDetailsParams.newBuilder()
                    .setProductList(subsList)
                    .build()

                client.queryProductDetailsAsync(subsParams) { billingResult, queryProductDetailsResult ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        val list = queryProductDetailsResult.productDetailsList ?: emptyList()
                        val current = _products.value.toMutableMap()
                        list.forEach { current[it.productId] = it }
                        _products.value = current
                        Log.d(TAG, "Queried ${list.size} SUBS products from Google Play: ${list.map { it.productId }}")
                    } else {
                        Log.w(TAG, "Failed querying SUBS products: ${billingResult.debugMessage}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error querying product details", e)
            }
        }
    }

    /**
     * Launch official Google Play purchase flow for a specified product ID or plan ("lifetime" / "yearly")
     */
    fun launchPurchaseFlow(
        activity: Activity,
        productIdOrPlan: String,
        onFallbackSimulation: (() -> Unit)? = null
    ) {
        val client = billingClient
        if (client == null || !client.isReady) {
            Log.w(TAG, "BillingClient not ready. Reconnecting...")
            startConnection {
                launchPurchaseFlow(activity, productIdOrPlan, onFallbackSimulation)
            }
            return
        }

        coroutineScope.launch {
            val isYearly = productIdOrPlan == "yearly" || productIdOrPlan == PRODUCT_YEARLY || productIdOrPlan == LEGACY_PRODUCT_YEARLY
            val candidateIds = if (isYearly) SUBS_PRODUCT_IDS else INAPP_PRODUCT_IDS

            // Look up cached product details
            val cachedProduct = candidateIds.firstNotNullOfOrNull { _products.value[it] }

            if (cachedProduct != null) {
                launchBillingFlowWithProduct(activity, cachedProduct)
            } else {
                // Not cached yet: query on-demand
                val productType = if (isYearly) BillingClient.ProductType.SUBS else BillingClient.ProductType.INAPP
                val queryProducts = candidateIds.map { id ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(id)
                        .setProductType(productType)
                        .build()
                }
                val params = QueryProductDetailsParams.newBuilder().setProductList(queryProducts).build()

                client.queryProductDetailsAsync(params) { billingResult, queryResult ->
                    val productList = queryResult.productDetailsList ?: emptyList()
                    val targetProduct = candidateIds.firstNotNullOfOrNull { cid -> productList.find { it.productId == cid } }
                        ?: productList.firstOrNull()

                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && targetProduct != null) {
                        launchBillingFlowWithProduct(activity, targetProduct)
                    } else {
                        Log.w(TAG, "Products not registered yet on Google Play Console (${billingResult.debugMessage}).")
                        if (onFallbackSimulation != null) {
                            activity.runOnUiThread { onFallbackSimulation() }
                        } else {
                            activity.runOnUiThread {
                                onPurchaseFailed?.invoke("Google Play Console: Product not found (${targetProduct?.productId ?: productIdOrPlan}). Please configure in Play Console.")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun launchBillingFlowWithProduct(activity: Activity, productDetails: ProductDetails) {
        val client = billingClient ?: return
        try {
            val productDetailsParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)

            // For subscriptions, offer token is mandatory
            if (productDetails.productType == BillingClient.ProductType.SUBS) {
                val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken
                if (!offerToken.isNullOrBlank()) {
                    productDetailsParamsBuilder.setOfferToken(offerToken)
                }
            }

            val flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(productDetailsParamsBuilder.build()))
                .build()

            activity.runOnUiThread {
                val result = client.launchBillingFlow(activity, flowParams)
                Log.d(TAG, "Launched Google Play billing flow: code=${result.responseCode}")
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    onPurchaseFailed?.invoke("Google Play error: ${result.debugMessage.ifBlank { "Code ${result.responseCode}" }}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception launching Google Play Billing flow", e)
            activity.runOnUiThread {
                onPurchaseFailed?.invoke("Launch error: ${e.message}")
            }
        }
    }

    /**
     * PurchasesUpdatedListener callback invoked by Google Play when a purchase flow completes
     */
    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (!purchases.isNullOrEmpty()) {
                    Log.d(TAG, "Purchases updated: ${purchases.size} items received")
                    for (purchase in purchases) {
                        handlePurchase(purchase)
                    }
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                Log.i(TAG, "Purchase canceled by user")
                onPurchaseFailed?.invoke("Purchase canceled")
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                Log.i(TAG, "Item already owned, querying existing purchases to restore")
                _isPremium.value = true
                queryPurchases()
                onPurchaseFailed?.invoke("Item already owned. Purchase restored!")
            }
            else -> {
                Log.w(TAG, "Purchase update error: ${billingResult.responseCode}, ${billingResult.debugMessage}")
                onPurchaseFailed?.invoke("Purchase error: ${billingResult.debugMessage.ifBlank { "Code ${billingResult.responseCode}" }}")
            }
        }
    }

    /**
     * Verify and acknowledge purchase, update premium state
     */
    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) {
            Log.w(TAG, "Purchase in pending state (${purchase.purchaseState})")
            return
        }

        coroutineScope.launch {
            val client = billingClient ?: return@launch

            // Acknowledge if needed
            if (!purchase.isAcknowledged) {
                val ackParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()

                client.acknowledgePurchase(ackParams) { ackResult ->
                    if (ackResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        Log.d(TAG, "Purchase successfully acknowledged: ${purchase.orderId}")
                    } else {
                        Log.w(TAG, "Failed acknowledging purchase: ${ackResult.debugMessage}")
                    }
                }
            }

            // Determine plan
            val productId = purchase.products.firstOrNull() ?: ""
            val isYearly = SUBS_PRODUCT_IDS.contains(productId)
            val plan = if (isYearly) "yearly" else "lifetime"

            _isPremium.value = true
            _activePlan.value = plan

            val priceStr = if (isYearly) "$1.00/yr" else "$2.00"

            withContext(Dispatchers.Main) {
                onPurchaseCompleted?.invoke(plan, priceStr, purchase.orderId ?: "", purchase.purchaseToken)
            }
        }
    }

    /**
     * Query existing purchases on startup or restore
     */
    fun queryPurchases(onComplete: ((Boolean, String) -> Unit)? = null) {
        val client = billingClient ?: run {
            onComplete?.invoke(false, "Billing not initialized")
            return
        }
        if (!client.isReady) {
            startConnection {
                queryPurchases(onComplete)
            }
            return
        }

        coroutineScope.launch {
            try {
                var foundActivePurchase = false
                var activePlanName = "free"

                // 1. Query INAPP purchases
                val inAppParams = QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()

                client.queryPurchasesAsync(inAppParams) { inAppResult, inAppPurchases ->
                    if (inAppResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        val validInApps = inAppPurchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                        if (validInApps.isNotEmpty()) {
                            foundActivePurchase = true
                            activePlanName = "lifetime"
                            validInApps.forEach { handlePurchase(it) }
                        }
                    }

                    // 2. Query SUBS purchases
                    val subsParams = QueryPurchasesParams.newBuilder()
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()

                    client.queryPurchasesAsync(subsParams) { subsResult, subsPurchases ->
                        if (subsResult.responseCode == BillingClient.BillingResponseCode.OK) {
                            val validSubs = subsPurchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                            if (validSubs.isNotEmpty()) {
                                foundActivePurchase = true
                                activePlanName = "yearly"
                                validSubs.forEach { handlePurchase(it) }
                            }
                        }

                        if (foundActivePurchase) {
                            _isPremium.value = true
                            _activePlan.value = activePlanName
                            _statusMessage.value = "Active Google Play Premium: $activePlanName"
                            onComplete?.invoke(true, "Restored active $activePlanName subscription successfully!")
                            onPurchasesRestored?.invoke(true, "Restored active $activePlanName subscription!")
                        } else {
                            _statusMessage.value = "No active Google Play purchases found"
                            onComplete?.invoke(false, "No previous purchases found on this Google account.")
                            onPurchasesRestored?.invoke(false, "No active purchases found.")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring purchases", e)
                onComplete?.invoke(false, "Error: ${e.message}")
            }
        }
    }

    /**
     * Restore previous purchases
     */
    fun restorePurchases(onResult: (Boolean, String) -> Unit) {
        queryPurchases(onResult)
    }
}
