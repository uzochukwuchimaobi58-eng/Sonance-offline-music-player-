package com.sonance.musicplayer.ui.components

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.sonance.musicplayer.model.ThemeConfig

private const val TAG = "AdMobBanner"
const val PRODUCTION_BANNER_UNIT_ID = "ca-app-pub-6322953088287505/5517813262"
const val TEST_BANNER_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"

@Composable
fun AdMobBanner(
    isPro: Boolean,
    admobEnabled: Boolean = false,
    onOpenProUpgrade: () -> Unit,
    theme: ThemeConfig,
    adUnitId: String = PRODUCTION_BANNER_UNIT_ID,
    modifier: Modifier = Modifier
) {
    if (isPro || !admobEnabled) return

    val context = LocalContext.current
    val effectiveUnitId = remember(adUnitId) {
        adUnitId.ifBlank { PRODUCTION_BANNER_UNIT_ID }
    }
    var isAdLoaded by remember { mutableStateOf(false) }
    var adLoadFailed by remember { mutableStateOf(false) }
    var showAdInfoDialog by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = !isPro && admobEnabled,
        enter = fadeIn() + slideInVertically { it },
        exit = fadeOut() + slideOutVertically { it },
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp)
                .height(52.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(0.8.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                .testTag("admob_banner_container"),
            color = Color(0xFF141416),
            tonalElevation = 2.dp
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (adLoadFailed && !isAdLoaded) {
                    // Clean branded sponsor bar fallback only when ad fails to load or offline
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFFFB300).copy(alpha = 0.2f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB300).copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = "Ad",
                                    color = Color(0xFFFFB300),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Sonance Music Player • Free Edition",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Button(
                            onClick = onOpenProUpgrade,
                            colors = ButtonDefaults.buttonColors(containerColor = theme.accentColor),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier.height(26.dp),
                            shape = RoundedCornerShape(13.dp)
                        ) {
                            Text(
                                text = "Go PRO",
                                color = Color.Black,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Real Google AdMob Banner View (uses production unit ID)
                AndroidView(
                    modifier = Modifier.wrapContentSize(),
                    factory = { ctx ->
                        AdView(ctx).apply {
                            setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
                            setAdSize(AdSize.BANNER)
                            this.adUnitId = effectiveUnitId
                            this.adListener = object : AdListener() {
                                override fun onAdLoaded() {
                                    super.onAdLoaded()
                                    isAdLoaded = true
                                    adLoadFailed = false
                                    Log.d(TAG, "AdMob banner loaded successfully ($effectiveUnitId).")
                                }

                                override fun onAdFailedToLoad(error: LoadAdError) {
                                    super.onAdFailedToLoad(error)
                                    isAdLoaded = false
                                    adLoadFailed = true
                                    Log.w(TAG, "AdMob banner notice: ${error.message} (code ${error.code})")
                                }

                                override fun onAdOpened() {
                                    super.onAdOpened()
                                    Log.d(TAG, "AdMob banner opened by user.")
                                }
                            }
                            loadAd(AdRequest.Builder().build())
                        }
                    },
                    update = { adView ->
                        if (adView.adUnitId != effectiveUnitId) {
                            adView.adUnitId = effectiveUnitId
                            adView.loadAd(AdRequest.Builder().build())
                        }
                    },
                    onRelease = { adView ->
                        try {
                            adView.destroy()
                        } catch (t: Throwable) {
                            Log.e(TAG, "Error destroying AdView", t)
                        }
                    }
                )

                // Info icon on top-right
                IconButton(
                    onClick = { showAdInfoDialog = true },
                    modifier = Modifier
                        .size(20.dp)
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Ad Info",
                        tint = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }

    if (showAdInfoDialog) {
        AlertDialog(
            onDismissRequest = { showAdInfoDialog = false },
            containerColor = theme.sidebarBg,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = theme.accentColor
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Google AdMob Ads", color = theme.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "This ad is served via the official Google AdMob Network to keep Sonance Music Player free for everyone.",
                        color = theme.textSecondary,
                        fontSize = 13.sp
                    )
                    Text(
                        "Unit ID: $adUnitId\nPrivacy: Complies with Google Play Developer Policy and user privacy standards.",
                        color = theme.textSecondary.copy(alpha = 0.7f),
                        fontSize = 11.sp
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        "Enjoy a 100% uninterrupted, zero-ad music experience by upgrading to Sonance PRO.",
                        color = theme.accentColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAdInfoDialog = false
                        onOpenProUpgrade()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = theme.accentColor)
                ) {
                    Text("Go PRO (Remove Ads)", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAdInfoDialog = false }) {
                    Text("Close", color = theme.textSecondary)
                }
            }
        )
    }
}

@Composable
fun InlineTrackAdCard(
    isPro: Boolean,
    admobEnabled: Boolean,
    onOpenProUpgrade: () -> Unit,
    theme: ThemeConfig,
    adUnitId: String = PRODUCTION_BANNER_UNIT_ID,
    modifier: Modifier = Modifier
) {
    if (isPro || !admobEnabled) return

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, Color(0xFFFFB300).copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .testTag("inline_track_ad_banner"),
        color = theme.sidebarBg.copy(alpha = 0.95f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFFFFB300).copy(alpha = 0.2f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB300).copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "AD",
                        color = Color(0xFFFFB300),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }

                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onOpenProUpgrade() },
                    color = theme.accentColor.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = "Remove Ads",
                        color = theme.accentColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            val effectiveUnitId = remember(adUnitId) {
                adUnitId.ifBlank { PRODUCTION_BANNER_UNIT_ID }
            }
            var inlineAdLoaded by remember { mutableStateOf(false) }
            var inlineAdFailed by remember { mutableStateOf(false) }

            if (inlineAdFailed && !inlineAdLoaded) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Sonance Music Player • Free Edition",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp
                    )
                }
            }

            AndroidView(
                modifier = Modifier.wrapContentSize(),
                factory = { ctx ->
                    AdView(ctx).apply {
                        setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
                        setAdSize(AdSize.BANNER)
                        this.adUnitId = effectiveUnitId
                        this.adListener = object : AdListener() {
                            override fun onAdLoaded() {
                                super.onAdLoaded()
                                inlineAdLoaded = true
                                inlineAdFailed = false
                            }
                            override fun onAdFailedToLoad(error: LoadAdError) {
                                super.onAdFailedToLoad(error)
                                inlineAdLoaded = false
                                inlineAdFailed = true
                            }
                        }
                        loadAd(AdRequest.Builder().build())
                    }
                },
                update = { adView ->
                    if (adView.adUnitId != effectiveUnitId) {
                        adView.adUnitId = effectiveUnitId
                        adView.loadAd(AdRequest.Builder().build())
                    }
                },
                onRelease = { adView ->
                    try {
                        adView.destroy()
                    } catch (_: Throwable) {}
                }
            )
        }
    }
}
