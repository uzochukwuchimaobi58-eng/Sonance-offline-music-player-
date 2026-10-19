package com.sonance.musicplayer.ui.components

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

const val PRODUCTION_REWARDED_UNIT_ID = "ca-app-pub-6322953088287505/6817514112"

object RewardedAdController {
    private const val TAG = "RewardedAd"

    private var rewardedAd: RewardedAd? = null
    private var isLoading = false

    fun loadRewardedAd(context: Context, adUnitId: String = PRODUCTION_REWARDED_UNIT_ID) {
        if (rewardedAd != null || isLoading) return
        isLoading = true
        val targetUnitId = adUnitId.ifBlank { PRODUCTION_REWARDED_UNIT_ID }
        val adRequest = AdRequest.Builder().build()
        try {
            RewardedAd.load(
                context.applicationContext,
                targetUnitId,
                adRequest,
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        rewardedAd = ad
                        isLoading = false
                        Log.d(TAG, "AdMob Rewarded ad loaded successfully ($targetUnitId)")
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        rewardedAd = null
                        isLoading = false
                        Log.w(TAG, "AdMob Rewarded ad failed to load: ${loadAdError.message} (code ${loadAdError.code})")
                    }
                }
            )
        } catch (t: Throwable) {
            isLoading = false
            Log.e(TAG, "Error initiating RewardedAd load", t)
        }
    }

    fun isAdReady(): Boolean = rewardedAd != null

    fun showRewardedAd(
        activity: Activity,
        adUnitId: String = PRODUCTION_REWARDED_UNIT_ID,
        onUserEarnedReward: () -> Unit,
        onAdUnavailable: () -> Unit
    ) {
        val ad = rewardedAd
        if (ad == null) {
            Log.w(TAG, "RewardedAd not ready yet. Initiating preload.")
            loadRewardedAd(activity, adUnitId)
            onAdUnavailable()
            return
        }

        var rewardGranted = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                // Preload the next rewarded ad immediately
                loadRewardedAd(activity, adUnitId)
                if (rewardGranted) {
                    onUserEarnedReward()
                }
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                rewardedAd = null
                Log.w(TAG, "RewardedAd failed to show: ${adError.message}")
                loadRewardedAd(activity, adUnitId)
                onAdUnavailable()
            }

            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "AdMob RewardedAd showed full screen content.")
            }
        }

        ad.show(activity) { rewardItem ->
            rewardGranted = true
            Log.d(TAG, "User earned reward: ${rewardItem.amount} ${rewardItem.type}")
        }
    }
}
