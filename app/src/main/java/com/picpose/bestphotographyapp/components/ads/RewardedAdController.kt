/**
 * ---
 * File: RewardedAdController.kt
 * Layer: Presentation (UI)
 * Project: PicPose
 *
 * Purpose:
 * Supports the PicPose app with feature-specific models, helpers, or UI building blocks.
 *
 * Interactions:
 * Works with nearby classes in the same layer to keep responsibilities separated and easier to maintain.
 *
 * Data Flow:
 * Feature-specific flow; see adjacent ViewModels and repositories for the full path.
 *
 * Maintainer Notes:
 * - Keep SDK-specific code isolated here so feature screens remain testable.
 * - TODO: Add analytics and remote-config driven rollout controls where appropriate.
 * ---
 */

package com.picpose.bestphotographyapp.components.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class RewardedAdController(
    private val placementKey: String,
    private val maxRetries: Int = 3,
    private val logTag: String = AdsLog.TAG_REWARD
) {

    interface Callbacks {
        fun onLoaded() {}
        fun onFailed(error: LoadAdError) {}
        fun onShowed() {}
        fun onDismissed() {}
        fun onFailedToShow(error: AdError) {}
        fun onReward(reward: RewardItem) {}
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var rewardedAd: RewardedAd? = null
    private var loadAttempt = 0
    private var retryJob: Job? = null

    fun preload(context: Context, callbacks: Callbacks? = null) {
        if (rewardedAd != null) return
        if (!AdsManager.canShowAds() || !AdsManager.shouldShowNow(placementKey)) {
            AdsLog.i(logTag, "[AdMobReward] placement=$placementKey action=preload status=SKIP reason=gate")
            return
        }

        val adUnitId = AdsManager.getAdUnitId(placementKey)
        if (adUnitId.isNullOrBlank()) {
            AdsLog.w(logTag, "[AdMobReward] placement=$placementKey action=preload status=SKIP reason=no_unit")
            return
        }

        AdsLog.i(logTag, "[AdMobReward] placement=$placementKey action=preload status=REQUESTED unit=${AdsLog.maskAdUnitId(adUnitId)}")
        RewardedAd.load(
            context,
            adUnitId,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    loadAttempt = 0
                    rewardedAd = ad
                    callbacks?.onLoaded()
                    AdsLog.i(logTag, "[AdMobReward] placement=$placementKey action=preload status=LOADED")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                    callbacks?.onFailed(error)
                    AdsLog.w(logTag, "[AdMobReward] placement=$placementKey action=preload status=FAIL domain=${error.domain} code=${error.code} message=${error.message}")
                    retryPreload(context, callbacks)
                }
            }
        )
    }

    private fun retryPreload(context: Context, callbacks: Callbacks?) {
        if (!AdsManager.canShowAds()) return
        if (loadAttempt >= maxRetries) return

        loadAttempt += 1
        retryJob?.cancel()
        retryJob = scope.launch {
            val backoffMs = (1_000L * (1 shl (loadAttempt - 1))).coerceAtMost(8_000L)
            AdsLog.d(logTag, "[AdMobReward] placement=$placementKey action=retry attempt=$loadAttempt backoffMs=$backoffMs")
            delay(backoffMs)
            preload(context, callbacks)
        }
    }

    fun show(activity: Activity, callbacks: Callbacks? = null, onComplete: (() -> Unit)? = null) {
        val ad = rewardedAd
        if (ad == null || !AdsManager.shouldShowNow(placementKey)) {
            AdsLog.i(logTag, "[AdMobReward] placement=$placementKey action=show status=SKIP reason=not_ready")
            onComplete?.invoke()
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                callbacks?.onShowed()
                AdsManager.markShown(placementKey)
                rewardedAd = null
                AdsLog.i(logTag, "[AdMobReward] placement=$placementKey action=show status=SHOWED")
            }

            override fun onAdDismissedFullScreenContent() {
                callbacks?.onDismissed()
                AdsLog.i(logTag, "[AdMobReward] placement=$placementKey action=show status=DISMISSED")
                onComplete?.invoke()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                callbacks?.onFailedToShow(adError)
                rewardedAd = null
                AdsLog.w(logTag, "[AdMobReward] placement=$placementKey action=show status=FAIL domain=${adError.domain} code=${adError.code} message=${adError.message}")
                onComplete?.invoke()
            }
        }

        AdsLog.i(logTag, "[AdMobReward] placement=$placementKey action=show status=REQUESTED")
        ad.show(activity) { reward ->
            AdsLog.i(logTag, "[AdMobReward] placement=$placementKey action=reward amount=${reward.amount} type=${reward.type}")
            callbacks?.onReward(reward)
        }
    }

    fun clear() {
        retryJob?.cancel()
        rewardedAd = null
        AdsLog.d(logTag, "[AdMobReward] placement=$placementKey action=clear")
    }
}
