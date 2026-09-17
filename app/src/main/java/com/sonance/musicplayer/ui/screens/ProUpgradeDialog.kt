package com.sonance.musicplayer.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
    onSignIn: (email: String, name: String, provider: String) -> Unit,
    onSignOut: () -> Unit,
    onSetDevProState: (Boolean) -> Unit,
    theme: ThemeConfig
) {
    if (!isOpen) return

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 0: Yearly ($1.00/yr), 1: Lifetime ($2.00)
    var selectedPlanIndex by remember { mutableIntStateOf(1) }
    var inputEmail by remember { mutableStateOf("") }
    var isSigningIn by remember { mutableStateOf(false) }
    var showGooglePlaySheet by remember { mutableStateOf(false) }
    var isProcessingPayment by remember { mutableStateOf(false) }
    var showSuccessBanner by remember { mutableStateOf(false) }

    val goldAccent = Color(0xFFFFD700)
    val goldDark = Color(0xFFFFA000)

    val selectedPlan = if (selectedPlanIndex == 0) "yearly" else "lifetime"
    val selectedPrice = if (selectedPlanIndex == 0) "$1.00/yr" else "$2.00"

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(24.dp))
                .border(1.5.dp, Brush.verticalGradient(listOf(goldAccent, Color(0xFF1E293B))), RoundedCornerShape(24.dp))
                .testTag("dialog_pro_upgrade"),
            color = theme.sidebarBg
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header: Close Button & Title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top Left Pro Badge in modal
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = goldAccent.copy(alpha = 0.2f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, goldAccent)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.WorkspacePremium,
                                contentDescription = null,
                                tint = goldAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (subscription.isPro) "PRO ACTIVE" else "SONANCE PRO",
                                color = goldAccent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.testTag("btn_close_pro_dialog")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = theme.textSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Luxury Crown Icon
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(goldAccent.copy(alpha = 0.35f), Color.Transparent)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.WorkspacePremium,
                        contentDescription = null,
                        tint = goldAccent,
                        modifier = Modifier.size(44.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (subscription.isPro) "You Are a Sonance PRO" else "Upgrade to Sonance PRO",
                    color = theme.textPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = if (subscription.isPro)
                        "Active Plan: ${subscription.plan.uppercase()} (${subscription.price}) • All Ads Removed"
                    else
                        "Pure High-Fidelity Music. 100% Ad-Free Experience.",
                    color = if (subscription.isPro) Color(0xFF10B981) else theme.textSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Feature Comparison List
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = theme.sidebarBg.copy(alpha = 0.85f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ProFeatureRow(
                            icon = Icons.Default.Block,
                            title = "100% Zero Ads",
                            subtitle = "Completely disables all Google AdMob banners and prompts",
                            theme = theme
                        )
                        ProFeatureRow(
                            icon = Icons.Default.GraphicEq,
                            title = "10-Band Studio Equalizer & Bass Boost",
                            subtitle = "Full hardware DSP access with unlimited custom presets",
                            theme = theme
                        )
                        ProFeatureRow(
                            icon = Icons.Default.CloudSync,
                            title = "Cloud Firebase Synchronization",
                            subtitle = "Sync your playlists, themes, and Pro license to Firestore",
                            theme = theme
                        )
                        ProFeatureRow(
                            icon = Icons.Default.Star,
                            title = "Golden VIP Badge",
                            subtitle = "Exclusive Pro indicator in top bar and audio player",
                            theme = theme
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // --- Two Payment Pricing Options: $1/yr vs $2 one-time ---
                Text(
                    text = "CHOOSE YOUR PLAN",
                    color = goldAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Plan 1: $1.00 / Year
                    PlanCard(
                        title = "1 Year Pass",
                        price = "$1.00",
                        period = "/ year",
                        badge = "AFFORDABLE",
                        badgeColor = Color(0xFF3B82F6),
                        isSelected = selectedPlanIndex == 0,
                        onClick = { selectedPlanIndex = 0 },
                        theme = theme,
                        modifier = Modifier.weight(1f)
                    )

                    // Plan 2: $2.00 One-Time
                    PlanCard(
                        title = "Lifetime VIP",
                        price = "$2.00",
                        period = "one-time",
                        badge = "BEST VALUE",
                        badgeColor = goldAccent,
                        isSelected = selectedPlanIndex == 1,
                        onClick = { selectedPlanIndex = 1 },
                        theme = theme,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // --- Sign-In / Account Section ---
                // User can use the app without signing in, but signs in here to remove ads or upgrade
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = theme.sidebarBg,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (subscription.isGuest) theme.accentColor.copy(alpha = 0.3f) else Color(0xFF10B981).copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (subscription.isGuest) Icons.Default.AccountCircle else Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = if (subscription.isGuest) goldAccent else Color(0xFF10B981),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (subscription.isGuest) "Account Sign-In (Optional for Play)" else "Connected Account",
                                    color = theme.textPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (!subscription.isGuest) {
                                TextButton(
                                    onClick = { onSignOut() },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("Sign Out", color = theme.textSecondary, fontSize = 11.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        if (subscription.isGuest) {
                            Text(
                                text = "Guest users play local music without signing in. To remove ads and secure your Pro license to your Google/Email account, connect below:",
                                color = theme.textSecondary,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // One-tap Google Sign-in Button
                            Button(
                                onClick = {
                                    isSigningIn = true
                                    coroutineScope.launch {
                                        delay(700)
                                        val defaultGoogleEmail = "uzochukwuchimaobi58@gmail.com"
                                        onSignIn(defaultGoogleEmail, "Uzochukwu", "google")
                                        isSigningIn = false
                                        Toast.makeText(context, "Signed in with Google ($defaultGoogleEmail)", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp)
                                    .testTag("btn_google_signin"),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4285F4)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Login,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isSigningIn) "Connecting Google Account..." else "Sign in with Google Account",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Or email input
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = inputEmail,
                                    onValueChange = { inputEmail = it },
                                    placeholder = { Text("Or enter email...", fontSize = 12.sp, color = theme.textSecondary) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = {
                                        if (inputEmail.contains("@")) {
                                            onSignIn(inputEmail.trim(), "", "email")
                                            Toast.makeText(context, "Signed in as $inputEmail", Toast.LENGTH_SHORT).show()
                                        }
                                    }),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = theme.textPrimary,
                                        unfocusedTextColor = theme.textPrimary,
                                        focusedBorderColor = theme.accentColor,
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp)
                                        .testTag("input_signin_email")
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                Button(
                                    onClick = {
                                        if (inputEmail.contains("@")) {
                                            onSignIn(inputEmail.trim(), "", "email")
                                            Toast.makeText(context, "Signed in as $inputEmail", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Please enter a valid email", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.height(50.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = theme.accentColor),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Link", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        } else {
                            // Already signed in
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = theme.accentColor.copy(alpha = 0.2f),
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = subscription.userEmail.take(1).uppercase(),
                                            color = theme.accentColor,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = subscription.userEmail,
                                        color = theme.textPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Sync Status: ${subscription.syncStatus}",
                                        color = Color(0xFF10B981),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Main CTA Button
                Button(
                    onClick = {
                        val email = if (subscription.userEmail.isNotBlank()) subscription.userEmail else "user@sonance.pro"
                        if (subscription.isGuest) {
                            onSignIn(email, "Guest Subscriber", "google")
                        }
                        showGooglePlaySheet = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("btn_checkout_pro"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = goldAccent
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ShoppingCart,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (subscription.isPro) "Switch / Renew Plan ($selectedPrice)" else "Upgrade & Remove Ads ($selectedPrice)",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // --- Developer / Reviewer Backend Test Integration Section ---
                // "integrate it at backend both true and false"
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.04f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Backend Pro Test Mode",
                                    color = theme.textPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Test app behavior when Pro is TRUE vs FALSE",
                                    color = theme.textSecondary,
                                    fontSize = 10.sp
                                )
                            }

                            // Switch to test true and false
                            Switch(
                                checked = subscription.isPro,
                                onCheckedChange = { targetPro ->
                                    onSetDevProState(targetPro)
                                    val msg = if (targetPro) "Pro set to TRUE (All ads removed)" else "Pro set to FALSE (AdMob active)"
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = goldAccent,
                                    checkedTrackColor = goldDark.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier.testTag("switch_test_backend_pro")
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Current State: ${if (subscription.isPro) "PRO = TRUE (AdMob Hidden)" else "PRO = FALSE (AdMob Visible)"}",
                                color = if (subscription.isPro) Color(0xFF10B981) else Color(0xFFFF9800),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Firestore Synced",
                                color = theme.textSecondary.copy(alpha = 0.7f),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }

    // Google Play Payment Simulation Modal
    if (showGooglePlaySheet) {
        AlertDialog(
            onDismissRequest = { if (!isProcessingPayment) showGooglePlaySheet = false },
            containerColor = Color(0xFF1E293B),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ShoppingBag,
                        contentDescription = null,
                        tint = Color(0xFF34A853)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Google Play Billing", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Sonance Music Player • Pro Access",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "Plan: ${if (selectedPlanIndex == 0) "Yearly Subscription ($1.00/year)" else "Lifetime Purchase ($2.00)"}",
                        color = goldAccent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Account: ${if (subscription.userEmail.isNotBlank()) subscription.userEmail else "uzochukwuchimaobi58@gmail.com"}",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                    Text(
                        text = "Payment: Google Play Balance (Visa •••• 4242)",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )

                    if (isProcessingPayment) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CircularProgressIndicator(color = goldAccent, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Confirming with Google Play & Firestore...", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isProcessingPayment = true
                        coroutineScope.launch {
                            delay(1200)
                            val userEmail = if (subscription.userEmail.isNotBlank()) subscription.userEmail else "uzochukwuchimaobi58@gmail.com"
                            onSubscribe(selectedPlan, selectedPrice, userEmail, "google_play")
                            isProcessingPayment = false
                            showGooglePlaySheet = false
                            Toast.makeText(context, "Purchase Successful! All ads removed.", Toast.LENGTH_LONG).show()
                            onClose()
                        }
                    },
                    enabled = !isProcessingPayment,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34A853))
                ) {
                    Text(
                        text = if (isProcessingPayment) "Processing..." else "1-Tap Buy ($selectedPrice)",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                if (!isProcessingPayment) {
                    TextButton(onClick = { showGooglePlaySheet = false }) {
                        Text("Cancel", color = Color(0xFF94A3B8))
                    }
                }
            }
        )
    }
}

@Composable
private fun PlanCard(
    title: String,
    price: String,
    period: String,
    badge: String,
    badgeColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    theme: ThemeConfig,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) Color(0xFFFFD700) else Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(14.dp)
            ),
        color = if (isSelected) Color(0xFFFFD700).copy(alpha = 0.12f) else theme.sidebarBg
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = badgeColor.copy(alpha = 0.2f),
                border = androidx.compose.foundation.BorderStroke(0.5.dp, badgeColor)
            ) {
                Text(
                    text = badge,
                    color = badgeColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = title,
                color = theme.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = price,
                    color = if (isSelected) Color(0xFFFFD700) else theme.textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = period,
                    color = theme.textSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun ProFeatureRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    theme: ThemeConfig
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = CircleShape,
            color = Color(0xFFFFD700).copy(alpha = 0.15f),
            modifier = Modifier.size(32.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFFFFD700),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                color = theme.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                color = theme.textSecondary,
                fontSize = 11.sp,
                lineHeight = 14.sp
            )
        }
    }
}
