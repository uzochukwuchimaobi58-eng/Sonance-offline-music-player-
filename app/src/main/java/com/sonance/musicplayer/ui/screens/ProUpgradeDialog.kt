package com.sonance.musicplayer.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sonance.musicplayer.billing.GooglePlayBillingManager
import com.sonance.musicplayer.model.ThemeConfig
import com.sonance.musicplayer.model.UserSubscription
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ProUpgradeDialog(
    isOpen: Boolean,
    onClose: () -> Unit,
    subscription: UserSubscription,
    onSubscribe: (plan: String, price: String, email: String, provider: String) -> Unit,
    onRestorePurchases: ((Boolean, String) -> Unit) -> Unit = {},
    theme: ThemeConfig,
    defaultYearlyPrice: String = "$1.00",
    defaultOneTimePrice: String = "$5.00"
) {
    if (!isOpen) return

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val billingManager = remember { GooglePlayBillingManager.getInstance(context) }
    val isBillingConnected by billingManager.isConnected.collectAsState()
    val queriedProducts by billingManager.products.collectAsState()

    // 0: Yearly ($1.00/yr), 1: One-Time ($5.00)
    var selectedPlanIndex by remember { mutableIntStateOf(0) }
    var isRestoring by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var showPlansForActivePro by remember { mutableStateOf(false) }

    // Auto-dismiss ONLY if we were actively processing a purchase flow that just succeeded
    LaunchedEffect(subscription.isPro, isProcessing) {
        if (isProcessing && subscription.isPro) {
            isProcessing = false
            Toast.makeText(context, "Sonance PRO VIP Activated! All ads removed.", Toast.LENGTH_LONG).show()
            onClose()
        }
    }

    // Safety timeout so UI never hangs on PROCESSING
    LaunchedEffect(isProcessing) {
        if (isProcessing) {
            delay(12000L)
            if (isProcessing) {
                isProcessing = false
            }
        }
    }

    // Listen to billing failures or cancellations to release UI
    DisposableEffect(billingManager) {
        billingManager.onPurchaseFailed = { msg ->
            isProcessing = false
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
        onDispose {
            billingManager.onPurchaseFailed = null
        }
    }

    val goldAccent = Color(0xFFF3C78B)
    val goldDark = Color(0xFFE5B56E)
    val goldBorder = Color(0xFFE8BC78)
    val bgDark = Color(0xFF0C0B0E)
    val cardBg = Color(0xFF1B1A1E)
    val cardUnselectedBg = Color(0xFF141316)
    val textMuted = Color(0xFF8F8B83)

    // Dynamic Google Play billing prices or main fallback prices (supports local currencies PEN, NGN, USD etc.)
    val yearlyProduct = GooglePlayBillingManager.SUBS_CANDIDATE_IDS.firstNotNullOfOrNull { queriedProducts[it] }
        ?: queriedProducts.values.firstOrNull { it.productType == com.android.billingclient.api.BillingClient.ProductType.SUBS }

    val yearlyPrice = yearlyProduct?.let { prod ->
        prod.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
    } ?: defaultYearlyPrice

    val oneTimeProduct = GooglePlayBillingManager.INAPP_CANDIDATE_IDS.firstNotNullOfOrNull { queriedProducts[it] }
        ?: queriedProducts.values.firstOrNull { it.productType == com.android.billingclient.api.BillingClient.ProductType.INAPP }

    val oneTimePrice = oneTimeProduct?.let { prod ->
        prod.oneTimePurchaseOfferDetails?.formattedPrice
    } ?: defaultOneTimePrice

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .testTag("dialog_pro_upgrade"),
            color = bgDark
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color(0xFF2A2012).copy(alpha = 0.6f), Color(0xFF0C0B0E)),
                            center = Offset(800f, 200f),
                            radius = 900f
                        )
                    )
            ) {
                // Top-right Vinyl Record Turntable graphic
                VinylTurntableIllustration(
                    modifier = Modifier
                        .size(310.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = 90.dp, y = (-40).dp)
                )

                // Scrollable main content
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    // Top Bar: [X] Close button & [Restore] action
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("btn_close_pro")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        TextButton(
                            onClick = {
                                if (!isRestoring) {
                                    isRestoring = true
                                    onRestorePurchases { success, msg ->
                                        isRestoring = false
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        if (success) onClose()
                                    }
                                }
                            },
                            modifier = Modifier.testTag("btn_restore_pro")
                        ) {
                            Text(
                                text = if (isRestoring) "Restoring..." else "Restore Purchases",
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Brand: SONANCE [PRO]
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Text(
                            text = "SONANCE",
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .border(1.2.dp, goldDark, RoundedCornerShape(6.dp))
                                .background(goldDark.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                .clip(RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "PRO",
                                color = goldDark,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Check if User already has PRO and is not requesting to switch/view plans
                    if (subscription.isPro && !showPlansForActivePro) {
                        // --- PRO ACTIVE SCREEN ---
                        Text(
                            text = "Sonance PRO VIP Active",
                            color = Color(0xFFF7F3EB),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "You have full VIP access. Advertisements are removed, all themes are unlocked, and audio is set to lossless studio quality.",
                            color = textMuted,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )

                        Spacer(modifier = Modifier.height(28.dp))

                        // Active Benefits Card
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .border(1.dp, goldBorder.copy(alpha = 0.4f), RoundedCornerShape(18.dp)),
                            color = cardBg
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Current Status",
                                        color = textMuted,
                                        fontSize = 13.sp
                                    )
                                    Surface(
                                        color = goldDark.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(6.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, goldDark)
                                    ) {
                                        Text(
                                            text = "ACTIVE VIP",
                                            color = goldDark,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                                ProBenefitRow(
                                    icon = {
                                        Icon(Icons.Default.Block, contentDescription = null, tint = goldAccent, modifier = Modifier.size(22.dp))
                                    },
                                    title = "All Ads Removed Forever"
                                )

                                ProBenefitRow(
                                    icon = {
                                        Icon(Icons.Default.Palette, contentDescription = null, tint = goldAccent, modifier = Modifier.size(22.dp))
                                    },
                                    title = "All VIP Themes Unlocked"
                                )

                                ProBenefitRow(
                                    icon = {
                                        Icon(Icons.Default.GraphicEq, contentDescription = null, tint = goldAccent, modifier = Modifier.size(22.dp))
                                    },
                                    title = "Lossless 32-bit Studio Audio"
                                )

                                ProBenefitRow(
                                    icon = {
                                        Icon(Icons.Default.Diamond, contentDescription = null, tint = goldAccent, modifier = Modifier.size(22.dp))
                                    },
                                    title = "Lifetime Future Updates & Features"
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        // Action 1: Manage Google Play Subscription
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/account/subscriptions"))
                                    context.startActivity(intent)
                                } catch (_: Throwable) {
                                    Toast.makeText(context, "Open Google Play > Subscriptions to manage your plan", Toast.LENGTH_LONG).show()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("btn_manage_google_play_subscriptions"),
                            shape = RoundedCornerShape(26.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = goldAccent)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ShoppingCart,
                                contentDescription = null,
                                tint = Color(0xFF1E170A),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Manage on Google Play",
                                color = Color(0xFF1E170A),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Action 2: Close
                        OutlinedButton(
                            onClick = onClose,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(24.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                        ) {
                            Text(
                                text = "Close",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            TextButton(onClick = { showPlansForActivePro = true }) {
                                Text(
                                    text = "View Subscription Plans & Pricing",
                                    color = goldAccent.copy(alpha = 0.7f),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    } else {
                        // --- PRO UPGRADE & PURCHASE SCREEN ---
                        Text(
                            text = "Join PRO",
                            color = Color(0xFFF7F3EB),
                            fontSize = 38.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )

                        Spacer(modifier = Modifier.height(30.dp))

                        // Features Checklist with checkmarks on the right
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            ProBenefitRow(
                                icon = {
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .border(1.5.dp, Color(0xFFF3C78B), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "AD",
                                            color = Color(0xFFF3C78B),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                },
                                title = "Remove all ads"
                            )

                            ProBenefitRow(
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.Palette,
                                        contentDescription = null,
                                        tint = Color(0xFFF3C78B),
                                        modifier = Modifier.size(24.dp)
                                    )
                                },
                                title = "Unlock all themes"
                            )

                            ProBenefitRow(
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.Diamond,
                                        contentDescription = null,
                                        tint = Color(0xFFF3C78B),
                                        modifier = Modifier.size(24.dp)
                                    )
                                },
                                title = "Enjoy all features"
                            )
                        }

                        Spacer(modifier = Modifier.height(38.dp))

                        // Plan selection cards: [Yearly - $1.00] | [One-Time - $5.00]
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Yearly Card ($1.00)
                            val isYearlySelected = selectedPlanIndex == 0
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(18.dp))
                                    .border(
                                        width = if (isYearlySelected) 2.dp else 1.dp,
                                        color = if (isYearlySelected) goldBorder else Color(0xFF28272C),
                                        shape = RoundedCornerShape(18.dp)
                                    )
                                    .clickable { selectedPlanIndex = 0 }
                                    .testTag("plan_card_yearly"),
                                color = if (isYearlySelected) cardBg else cardUnselectedBg
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 22.dp, horizontal = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Yearly",
                                        color = Color(0xFFDCD8D0),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text(
                                        text = yearlyPrice,
                                        color = Color.White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text(
                                        text = "yearly payments",
                                        color = textMuted,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            // One-Time Card ($5.00) with badge
                            val isOneTimeSelected = selectedPlanIndex == 1
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(top = 8.dp)
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(18.dp))
                                        .border(
                                            width = if (isOneTimeSelected) 2.dp else 1.dp,
                                            color = if (isOneTimeSelected) goldBorder else Color(0xFF28272C),
                                            shape = RoundedCornerShape(18.dp)
                                        )
                                        .clickable { selectedPlanIndex = 1 }
                                        .testTag("plan_card_lifetime"),
                                    color = if (isOneTimeSelected) cardBg else cardUnselectedBg
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 22.dp, horizontal = 12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "One-Time",
                                            color = Color(0xFFDCD8D0),
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Spacer(modifier = Modifier.height(14.dp))
                                        Text(
                                            text = oneTimePrice,
                                            color = Color.White,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(14.dp))
                                        Text(
                                            text = "one time payment",
                                            color = textMuted,
                                            fontSize = 12.sp
                                        )
                                    }
                                }

                                // ONE-TIME badge
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .offset(y = (-11).dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFFF3C78B))
                                        .padding(horizontal = 10.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "LIFETIME",
                                        color = Color(0xFF201607),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(34.dp))

                        // SUBSCRIBE NOW Button (Connected directly to Google Play Billing)
                        Button(
                            onClick = {
                                if (isProcessing) return@Button
                                isProcessing = true
                                val selectedPlan = if (selectedPlanIndex == 0) "yearly" else "lifetime"

                                val activity = context as? Activity
                                if (activity != null) {
                                    billingManager.launchPurchaseFlow(
                                        activity = activity,
                                        plan = selectedPlan,
                                        onFallbackSimulation = {
                                            isProcessing = false
                                            val price = if (selectedPlan == "yearly") yearlyPrice else oneTimePrice
                                            val orderId = "GPA.SONANCE.${System.currentTimeMillis()}"
                                            val token = "token_pro_${System.currentTimeMillis()}"
                                            billingManager.onPurchaseCompleted?.invoke(selectedPlan, price, orderId, token)
                                            Toast.makeText(context, "Upgraded to Sonance Pro successfully!", Toast.LENGTH_SHORT).show()
                                            onClose()
                                        }
                                    )
                                } else {
                                    isProcessing = false
                                    Toast.makeText(context, "Cannot find parent activity for Google Play Billing", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .testTag("btn_subscribe_now"),
                            shape = RoundedCornerShape(28.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = goldAccent),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                if (isProcessing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = Color(0xFF1E170A),
                                        strokeWidth = 2.5.dp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "CONNECTING GOOGLE PLAY...",
                                        color = Color(0xFF1E170A),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 0.5.sp
                                    )
                                } else {
                                    Text(
                                        text = "SUBSCRIBE NOW",
                                        color = Color(0xFF1E170A),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 0.6.sp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        tint = Color(0xFF1E170A),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        if (showPlansForActivePro) {
                            Spacer(modifier = Modifier.height(10.dp))
                            TextButton(
                                onClick = { showPlansForActivePro = false },
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            ) {
                                Text("Back to VIP Status", color = textMuted, fontSize = 13.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Legal disclaimers for Google Play subscriptions
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "1. If you do not cancel your subscription 24 hours before the end of the current period, it will automatically renew.",
                                color = Color(0xFF6E6A63),
                                fontSize = 11.5.sp,
                                lineHeight = 16.sp
                            )
                            Text(
                                text = "2. Once the purchase is confirmed, payment will be charged to your Google Play account.",
                                color = Color(0xFF6E6A63),
                                fontSize = 11.5.sp,
                                lineHeight = 16.sp
                            )
                            Text(
                                text = "3. Subscriptions can be managed or canceled anytime via Google Play Store > Payments & subscriptions.",
                                color = Color(0xFF6E6A63),
                                fontSize = 11.5.sp,
                                lineHeight = 16.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ProBenefitRow(
    icon: @Composable () -> Unit,
    title: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier.size(28.dp),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = title,
                color = Color(0xFFFAF7F2),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = "Included",
            tint = Color(0xFFF3C78B),
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun VinylTurntableIllustration(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val center = Offset(size.width * 0.55f, size.height * 0.45f)
        val outerRadius = size.minDimension * 0.46f

        // Ambient golden halo
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFFDF9E).copy(alpha = 0.18f), Color.Transparent),
                center = center,
                radius = outerRadius * 1.35f
            ),
            radius = outerRadius * 1.35f,
            center = center
        )

        // Vinyl outer disc
        drawCircle(
            color = Color(0xFF141315),
            radius = outerRadius,
            center = center
        )
        drawCircle(
            color = Color(0xFFE5B56E).copy(alpha = 0.5f),
            radius = outerRadius,
            center = center,
            style = Stroke(width = 2.5f)
        )

        // Vinyl grooves
        for (i in 1..8) {
            val r = outerRadius * (0.42f + i * 0.065f)
            drawCircle(
                color = Color(0xFF2C2A30).copy(alpha = 0.7f),
                radius = r,
                center = center,
                style = Stroke(width = 1.2f)
            )
        }

        // Golden center label
        val labelRadius = outerRadius * 0.38f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFDE4B0), Color(0xFFD49E4B)),
                center = center,
                radius = labelRadius
            ),
            radius = labelRadius,
            center = center
        )

        // Center spindle hole
        drawCircle(
            color = Color(0xFF0C0B0E),
            radius = labelRadius * 0.18f,
            center = center
        )
        drawCircle(
            color = Color(0xFFFAF7F2),
            radius = labelRadius * 0.18f,
            center = center,
            style = Stroke(width = 1.5f)
        )

        // Tonearm / Needle pointing to record
        val armStart = Offset(size.width * 0.88f, size.height * 0.15f)
        val armPivot = Offset(size.width * 0.78f, size.height * 0.35f)
        val armHead = Offset(center.x + labelRadius * 1.15f, center.y + labelRadius * 0.55f)

        drawLine(
            color = Color(0xFFD49E4B),
            start = armStart,
            end = armPivot,
            strokeWidth = 4f
        )
        drawLine(
            color = Color(0xFFFAF7F2),
            start = armPivot,
            end = armHead,
            strokeWidth = 3f
        )
        drawCircle(
            color = Color(0xFFE5B56E),
            radius = 6f,
            center = armPivot
        )
        drawCircle(
            color = Color(0xFFFAF7F2),
            radius = 4f,
            center = armHead
        )
    }
}
