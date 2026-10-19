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
const val TEST_INTERSTITIAL_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"

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
        isLoading = true
        val targetUnitId = adUnitId.ifBlank { PRODUCTION_INTERSTITIAL_UNIT_ID }
        val adRequest = AdRequest.Builder().build()
        try {
            InterstitialAd.load(
                context.applicationContext,
                targetUnitId,
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

    LaunchedEffect(isOpen) {
        if (activity != null) {
            val shown = InterstitialAdController.showInterstitial(activity, adUnitId) {
                onDismiss()
            }
            if (!shown) {
                // If the real Google AdMob ad is not ready, do not show any blocking test banner or dialog
                InterstitialAdController.loadInterstitial(context, adUnitId)
                onDismiss()
            }
        } else {
            onDismiss()
        }
    }
}

