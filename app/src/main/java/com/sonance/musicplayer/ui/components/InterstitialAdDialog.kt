package com.sonance.musicplayer.ui.components

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.sonance.musicplayer.model.ThemeConfig
import kotlinx.coroutines.delay

const val PRODUCTION_INTERSTITIAL_UNIT_ID = "ca-app-pub-6322953088287505/2734992395"

object InterstitialAdController {
    private const val TAG = "InterstitialAd"
    private var lastAdShownTimestamp: Long = 0L
    // 35 seconds cooldown between interstitials to respect user experience
    private const val COOLDOWN_INTERVAL_MS = 35_000L

    var interstitialAd: InterstitialAd? = null
        private set
    private var isLoading = false

    fun loadInterstitial(context: Context, adUnitId: String = PRODUCTION_INTERSTITIAL_UNIT_ID) {
        if (interstitialAd != null || isLoading) return
        if (com.sonance.musicplayer.MusicApplication.isEmulatorDevice()) {
            Log.d(TAG, "Emulator device detected; skipping InterstitialAd load")
            return
        }
        isLoading = true
        val adRequest = AdRequest.Builder().build()
        try {
            InterstitialAd.load(
                context.applicationContext,
                adUnitId,
                adRequest,
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        interstitialAd = ad
                        isLoading = false
                        Log.d(TAG, "Production AdMob Interstitial ad loaded successfully.")
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        interstitialAd = null
                        isLoading = false
                        Log.w(TAG, "Production AdMob Interstitial failed to load: ${loadAdError.message} (code ${loadAdError.code})")
                    }
                }
            )
        } catch (t: Throwable) {
            isLoading = false
            Log.e(TAG, "Error initiating InterstitialAd load", t)
        }
    }

    fun shouldTriggerInterstitial(isPro: Boolean, admobEnabled: Boolean): Boolean {
        if (isPro || !admobEnabled) return false
        val now = System.currentTimeMillis()
        if (now - lastAdShownTimestamp >= COOLDOWN_INTERVAL_MS) {
            return true
        }
        return false
    }

    fun recordAdImpression() {
        lastAdShownTimestamp = System.currentTimeMillis()
    }

    fun showInterstitial(
        activity: Activity,
        adUnitId: String = PRODUCTION_INTERSTITIAL_UNIT_ID,
        onAdDismissed: () -> Unit
    ): Boolean {
        val ad = interstitialAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    interstitialAd = null
                    recordAdImpression()
                    loadInterstitial(activity, adUnitId)
                    onAdDismissed()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    interstitialAd = null
                    Log.w(TAG, "Interstitial ad failed to show: ${adError.message}")
                    loadInterstitial(activity, adUnitId)
                    onAdDismissed()
                }

                override fun onAdShowedFullScreenContent() {
                    recordAdImpression()
                    Log.d(TAG, "AdMob Interstitial showed full screen content.")
                }
            }
            ad.show(activity)
            return true
        } else {
            // Not ready yet, start preload for next action
            loadInterstitial(activity, adUnitId)
            return false
        }
    }
}

@Composable
fun InterstitialAdDialog(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    onOpenPro: () -> Unit,
    theme: ThemeConfig,
    adUnitId: String = PRODUCTION_INTERSTITIAL_UNIT_ID
) {
    if (!isOpen) return

    val context = LocalContext.current
    val activity = context as? Activity

    // Ensure interstitial is preloaded or try to display the native full-screen ad
    LaunchedEffect(isOpen) {
        if (activity != null) {
            val shown = InterstitialAdController.showInterstitial(activity, adUnitId) {
                onDismiss()
            }
            if (shown) {
                // Real Google AdMob full-screen activity is now displaying on top
                return@LaunchedEffect
            } else {
                InterstitialAdController.loadInterstitial(context, adUnitId)
            }
        }
    }

    // Fallback display if ad is still loading or running on device without Play Services
    var countdownSeconds by remember { mutableIntStateOf(3) }
    var canSkip by remember { mutableStateOf(false) }

    LaunchedEffect(isOpen) {
        countdownSeconds = 3
        canSkip = false
        while (countdownSeconds > 0) {
            delay(1000L)
            countdownSeconds--
        }
        canSkip = true
    }

    Dialog(
        onDismissRequest = {
            if (canSkip) onDismiss()
        },
        properties = DialogProperties(
            dismissOnBackPress = canSkip,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.88f))
                .padding(20.dp)
                .testTag("interstitial_ad_dialog"),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.35f), RoundedCornerShape(24.dp)),
                color = Color(0xFF13131A)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header Bar: Ad Indicator & Countdown / Skip Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFFFB300).copy(alpha = 0.2f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB300).copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Ad",
                                    color = Color(0xFFFFB300),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "• Google AdMob Network",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 10.sp
                                )
                            }
                        }

                        // Skip / Close Button
                        if (canSkip) {
                            Surface(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { onDismiss() }
                                    .testTag("interstitial_skip_button"),
                                color = Color.White.copy(alpha = 0.15f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Close",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close Ad",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        } else {
                            Surface(
                                modifier = Modifier.clip(CircleShape),
                                color = Color.White.copy(alpha = 0.08f)
                            ) {
                                Text(
                                    text = "Skip in ${countdownSeconds}s",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Ad Creative Hero Card
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        color = Color(0xFF1E1E2D)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF1E1B4B), Color(0xFF0F172A), Color(0xFF1E293B))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFF38BDF8).copy(alpha = 0.2f),
                                    modifier = Modifier.size(56.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.MusicNote,
                                            contentDescription = null,
                                            tint = Color(0xFF38BDF8),
                                            modifier = Modifier.size(30.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Sonance Music Player PRO",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Upgrade for Lossless Audio & Zero Ads",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Title & Description
                    Text(
                        text = "Support Sonance Free Edition",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Advertisements keep music features free. Upgrade to Sonance PRO to remove all ads forever.",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 17.sp
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Actions: [Remove Ads with PRO]
                    Button(
                        onClick = {
                            onDismiss()
                            onOpenPro()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("btn_interstitial_upgrade_pro")
                    ) {
                        Icon(
                            imageVector = Icons.Default.WorkspacePremium,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Remove All Ads with PRO",
                            color = Color.Black,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (canSkip) {
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Continue to Music",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
