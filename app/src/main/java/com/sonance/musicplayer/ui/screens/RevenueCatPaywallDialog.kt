package com.sonance.musicplayer.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.models.StoreTransaction
import com.revenuecat.purchases.ui.revenuecatui.PaywallDialog
import com.revenuecat.purchases.ui.revenuecatui.PaywallDialogOptions
import com.revenuecat.purchases.ui.revenuecatui.PaywallListener
import com.revenuecat.purchases.ui.revenuecatui.customercenter.CustomerCenter
import com.revenuecat.purchases.ui.revenuecatui.customercenter.CustomerCenterOptions
import com.sonance.musicplayer.billing.RevenueCatBillingManager

/**
 * Native RevenueCat Paywall Dialog using purchases-ui component.
 * Displays remote Paywall configured on the RevenueCat Dashboard.
 * Supports dismissal, entitlement checking for 'com_sonance_musicplayer_pro',
 * and purchase/restore callbacks.
 */
@Composable
fun RevenueCatPaywallDialog(
    isOpen: Boolean,
    onClose: () -> Unit,
    onProGranted: (CustomerInfo) -> Unit = {}
) {
    if (!isOpen || !Purchases.isConfigured) return

    val context = LocalContext.current
    val billingManager = remember { RevenueCatBillingManager.getInstance(context) }

    val paywallOptions = remember(onClose) {
        PaywallDialogOptions.Builder()
            .setRequiredEntitlementIdentifier(RevenueCatBillingManager.ENTITLEMENT_ID_PRO)
            .setShouldDisplayDismissButton(true)
            .setDismissRequest {
                onClose()
            }
            .setListener(object : PaywallListener {
                override fun onPurchaseCompleted(customerInfo: CustomerInfo, storeTransaction: StoreTransaction) {
                    Toast.makeText(context, "Welcome to Sonance PRO!", Toast.LENGTH_SHORT).show()
                    billingManager.processCustomerInfo(customerInfo)
                    onProGranted(customerInfo)
                    onClose()
                }

                override fun onRestoreCompleted(customerInfo: CustomerInfo) {
                    billingManager.processCustomerInfo(customerInfo)
                    val isPro = customerInfo.entitlements[RevenueCatBillingManager.ENTITLEMENT_ID_PRO]?.isActive == true
                    if (isPro) {
                        Toast.makeText(context, "PRO subscription restored successfully!", Toast.LENGTH_SHORT).show()
                        onProGranted(customerInfo)
                        onClose()
                    } else {
                        Toast.makeText(context, "No active subscription found to restore.", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onPurchaseError(error: PurchasesError) {
                    Toast.makeText(context, "Purchase failed: ${error.message}", Toast.LENGTH_LONG).show()
                }
            })
            .build()
    }

    PaywallDialog(paywallDialogOptions = paywallOptions)
}

/**
 * Native RevenueCat Customer Center Dialog using purchases-ui component.
 * Provides subscribers with self-service subscription management, cancellation,
 * change of plans, refund requests, and purchase history.
 */
@Composable
fun RevenueCatCustomerCenterDialog(
    isOpen: Boolean,
    onClose: () -> Unit
) {
    if (!isOpen || !Purchases.isConfigured) return

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header with Close Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Customer Support & Subscriptions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Customer Center",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // RevenueCat Customer Center Component
                val customerCenterOptions = remember {
                    CustomerCenterOptions.Builder().build()
                }

                CustomerCenter(
                    modifier = Modifier.fillMaxSize(),
                    options = customerCenterOptions,
                    onDismiss = onClose
                )
            }
        }
    }
}
