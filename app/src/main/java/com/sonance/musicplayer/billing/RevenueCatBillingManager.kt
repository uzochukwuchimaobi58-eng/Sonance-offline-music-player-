package com.sonance.musicplayer.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.revenuecat.purchases.*
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.interfaces.PurchaseCallback
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener
import com.revenuecat.purchases.models.StoreTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Manages RevenueCat SDK for Sonance Music Player.
 * Handles initialization, entitlement checks for 'com_sonance_musicplayer_pro',
 * offerings, packages, customer info listener, purchase flows, and restores.
 */
class RevenueCatBillingManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "RevenueCatBilling"

        // RevenueCat API Keys provided by developer
        const val API_KEY_GOOGLE_PUBLIC = "goog_jYjeYuiZFeIIqfGVotNCATfDzsm"
        const val API_KEY_TEST = "test_IrSefzSdacSgjhZVQOxVWfeGisB"

        // Primary entitlement to grant Pro VIP status
        const val ENTITLEMENT_ID_PRO = "com_sonance_musicplayer_pro"

        // Product IDs configured for Sonance Music Player
        const val PRODUCT_SONANCE_LIFETIME = "sonance_lifetime_payment"
        const val PRODUCT_SONANCE_YEARLY = "sonance_yearly_payment"
        const val PLAN_SONANCE_YEARLY_RENEW = "sonace-yearly-renew"
        const val PRODUCT_LIFETIME = "lifetime"
        const val PRODUCT_YEARLY = "yearly"
        const val PRODUCT_MONTHLY = "monthly"

        @Volatile
        private var instance: RevenueCatBillingManager? = null

        fun getInstance(context: Context): RevenueCatBillingManager {
            return instance ?: synchronized(this) {
                instance ?: RevenueCatBillingManager(context.applicationContext).also { instance = it }
            }
        }

        fun isGooglePlayStoreInstalled(context: Context): Boolean {
            return try {
                context.packageManager.getPackageInfo("com.android.vending", 0) != null
            } catch (_: Throwable) {
                false
            }
        }

        fun initialize(context: Context, preferredApiKey: String? = null) {
            try {
                if (Purchases.isConfigured) {
                    Log.d(TAG, "RevenueCat already configured.")
                    return
                }

                val hasPlayStore = isGooglePlayStoreInstalled(context)
                val apiKey = preferredApiKey ?: if (hasPlayStore) API_KEY_GOOGLE_PUBLIC else API_KEY_TEST

                // Custom LogHandler to prevent expected emulator environment differences
                // (e.g. no Google Play Store package) from spamming ERROR logs
                Purchases.logHandler = object : LogHandler {
                    override fun v(tag: String, message: String) {}
                    override fun d(tag: String, message: String) { Log.d(tag, message) }
                    override fun i(tag: String, message: String) { Log.i(tag, message) }
                    override fun w(tag: String, message: String) { Log.w(tag, message) }
                    override fun e(tag: String, message: String, throwable: Throwable?) {
                        if (message.contains("BILLING_UNAVAILABLE", ignoreCase = true) ||
                            message.contains("PurchaseNotAllowedError", ignoreCase = true) ||
                            message.contains("Billing service unavailable on device", ignoreCase = true) ||
                            message.contains("no Play Store products registered", ignoreCase = true) ||
                            message.contains("Error fetching offerings", ignoreCase = true)
                        ) {
                            Log.i("RevenueCat", "[Device Status]: $message")
                        } else {
                            Log.e(tag, message, throwable)
                        }
                    }
                }

                Purchases.logLevel = LogLevel.INFO

                val builder = PurchasesConfiguration.Builder(context, apiKey)
                    .showInAppMessagesAutomatically(false)

                if (apiKey.startsWith("test_")) {
                    builder.store(Store.TEST_STORE)
                    builder.dangerousSettings(DangerousSettings(autoSyncPurchases = false))
                } else if (!hasPlayStore) {
                    builder.dangerousSettings(DangerousSettings(autoSyncPurchases = false))
                }

                Purchases.configure(builder.build())
                Log.d(TAG, "RevenueCat initialized successfully with key: ${apiKey.take(7)}... (PlayStore: $hasPlayStore)")
            } catch (e: Exception) {
                Log.w(TAG, "Graceful catch during RevenueCat init: ${e.message}")
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main)

    private val _isPro = MutableStateFlow(false)
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    private val _customerInfo = MutableStateFlow<CustomerInfo?>(null)
    val customerInfo: StateFlow<CustomerInfo?> = _customerInfo.asStateFlow()

    private val _currentOffering = MutableStateFlow<Offering?>(null)
    val currentOffering: StateFlow<Offering?> = _currentOffering.asStateFlow()

    private val _availablePackages = MutableStateFlow<List<Package>>(emptyList())
    val availablePackages: StateFlow<List<Package>> = _availablePackages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _statusMessage = MutableStateFlow("RevenueCat Ready")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    // Callbacks for repository & UI listeners
    var onProStatusChanged: ((Boolean) -> Unit)? = null
    var onPurchaseSuccess: ((CustomerInfo) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    init {
        setupCustomerInfoListener()
        fetchCustomerInfo()
        fetchOfferings()
    }

    private fun setupCustomerInfoListener() {
        if (!Purchases.isConfigured) return
        try {
            Purchases.sharedInstance.updatedCustomerInfoListener = UpdatedCustomerInfoListener { info ->
                processCustomerInfo(info)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Customer info listener setup: ${e.message}")
        }
    }

    fun fetchCustomerInfo() {
        if (!Purchases.isConfigured) return
        _isLoading.value = true
        scope.launch {
            try {
                val info = Purchases.sharedInstance.awaitCustomerInfo()
                _isLoading.value = false
                processCustomerInfo(info)
            } catch (error: PurchasesException) {
                _isLoading.value = false
                Log.i(TAG, "CustomerInfo notice: ${error.message}")
                _statusMessage.value = "Status: ${error.message}"
            } catch (e: Exception) {
                _isLoading.value = false
                Log.i(TAG, "Notice getting CustomerInfo: ${e.message}")
            }
        }
    }

    fun fetchOfferings() {
        if (!Purchases.isConfigured) return
        scope.launch {
            try {
                val offerings = Purchases.sharedInstance.awaitOfferings()
                val current = offerings.current
                _currentOffering.value = current
                _availablePackages.value = current?.availablePackages ?: emptyList()
                Log.d(TAG, "Offerings fetched. Current: ${current?.identifier}, packages: ${current?.availablePackages?.size}")
            } catch (error: PurchasesException) {
                Log.i(TAG, "Offerings notice (normal if dashboard products not yet active): ${error.message}")
                _statusMessage.value = "Offerings notice: ${error.message}"
            } catch (e: Exception) {
                Log.i(TAG, "Notice fetching offerings: ${e.message}")
            }
        }
    }

    fun processCustomerInfo(info: CustomerInfo) {
        _customerInfo.value = info
        val hasProEntitlement = info.entitlements[ENTITLEMENT_ID_PRO]?.isActive == true
        val previousState = _isPro.value
        _isPro.value = hasProEntitlement
        Log.d(TAG, "CustomerInfo updated. Has entitlement '$ENTITLEMENT_ID_PRO': $hasProEntitlement")
        if (previousState != hasProEntitlement) {
            onProStatusChanged?.invoke(hasProEntitlement)
        }
    }

    /**
     * Purchase a RevenueCat package (Monthly, Yearly, Lifetime, etc.)
     */
    fun purchasePackage(
        activity: Activity,
        rcPackage: Package,
        onSuccess: (CustomerInfo) -> Unit = {},
        onErrorCallback: (String) -> Unit = {}
    ) {
        if (!Purchases.isConfigured) {
            val err = "RevenueCat is not initialized."
            onErrorCallback(err)
            return
        }

        _isLoading.value = true
        val purchaseParams = PurchaseParams.Builder(activity, rcPackage).build()
        Purchases.sharedInstance.purchase(
            purchaseParams,
            object : PurchaseCallback {
                override fun onCompleted(storeTransaction: StoreTransaction, customerInfo: CustomerInfo) {
                    _isLoading.value = false
                    processCustomerInfo(customerInfo)
                    onPurchaseSuccess?.invoke(customerInfo)
                    onSuccess(customerInfo)
                }

                override fun onError(error: PurchasesError, userCancelled: Boolean) {
                    _isLoading.value = false
                    if (!userCancelled) {
                        Log.i(TAG, "Purchase callback: ${error.message}")
                        onErrorCallback(error.message)
                        onError?.invoke(error.message)
                    } else {
                        Log.d(TAG, "Purchase cancelled by user.")
                    }
                }
            }
        )
    }

    /**
     * Restore previous purchases across reinstalls or device transfers
     */
    fun restorePurchases(
        onResult: (isPro: Boolean, message: String) -> Unit
    ) {
        if (!Purchases.isConfigured) {
            onResult(false, "RevenueCat is not initialized.")
            return
        }

        _isLoading.value = true
        scope.launch {
            try {
                val customerInfo = Purchases.sharedInstance.awaitRestore()
                _isLoading.value = false
                processCustomerInfo(customerInfo)
                val pro = customerInfo.entitlements[ENTITLEMENT_ID_PRO]?.isActive == true
                val msg = if (pro) {
                    "Purchases restored successfully! Pro entitlement active."
                } else {
                    "No active Pro subscription or purchase found."
                }
                onResult(pro, msg)
            } catch (error: PurchasesException) {
                _isLoading.value = false
                Log.i(TAG, "Restore notice: ${error.message}")
                onResult(false, "Restore notice: ${error.message}")
            } catch (e: Exception) {
                _isLoading.value = false
                Log.i(TAG, "Unexpected notice restoring purchases: ${e.message}")
                onResult(false, "Restore status: ${e.message}")
            }
        }
    }
}
