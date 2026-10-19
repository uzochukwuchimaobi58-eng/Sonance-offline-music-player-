package com.sonance.musicplayer.ui.components

import android.util.Log
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
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

    val effectiveUnitId = remember(adUnitId) {
        adUnitId.ifBlank { PRODUCTION_BANNER_UNIT_ID }
    }
    var isAdLoaded by remember { mutableStateOf(false) }

    // Mount AdView in composition so it sends the request. If ad is not loaded or fails, height is 0dp with no fallback banner shown.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(if (isAdLoaded) 52.dp else 0.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isAdLoaded) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp)
                    .height(50.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(0.8.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                    .testTag("admob_banner_container"),
                color = Color(0xFF141416),
                tonalElevation = 2.dp
            ) {}
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
                            isAdLoaded = true
                            Log.d(TAG, "AdMob banner loaded successfully ($effectiveUnitId).")
                        }

                        override fun onAdFailedToLoad(error: LoadAdError) {
                            super.onAdFailedToLoad(error)
                            isAdLoaded = false
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

    val effectiveUnitId = remember(adUnitId) {
        adUnitId.ifBlank { PRODUCTION_BANNER_UNIT_ID }
    }
    var inlineAdLoaded by remember { mutableStateOf(false) }

    // Only render the card container if the real AdMob ad successfully loads
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(if (inlineAdLoaded) 64.dp else 0.dp),
        contentAlignment = Alignment.Center
    ) {
        if (inlineAdLoaded) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp, horizontal = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, Color(0xFFFFB300).copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                    .testTag("inline_track_ad_banner"),
                color = theme.sidebarBg.copy(alpha = 0.95f)
            ) {}
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
                        }
                        override fun onAdFailedToLoad(error: LoadAdError) {
                            super.onAdFailedToLoad(error)
                            inlineAdLoaded = false
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
