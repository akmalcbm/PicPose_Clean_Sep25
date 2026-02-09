package com.picpose.bestphotographyapp.presentation.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class InterstitialAdController(
    private val placementKey: String,
    private val maxRetries: Int = 3,
    private val logTag: String = AdsLog.TAG_INTER
) {

    interface Callbacks {
        fun onLoaded() {}
        fun onFailed(error: LoadAdError) {}
        fun onShowed() {}
        fun onDismissed() {}
        fun onFailedToShow(error: AdError) {}
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var interstitialAd: InterstitialAd? = null
    private var loadAttempt = 0
    private var retryJob: Job? = null

    fun preload(context: Context, callbacks: Callbacks? = null) {
        if (interstitialAd != null) {
            AdsLog.d(logTag, "[AdMobInter] placement=$placementKey action=preload status=SKIP reason=already_loaded")
            return
        }
        if (!AdsManager.canShowAds() || !AdsManager.shouldShowNow(placementKey)) {
            AdsLog.i(logTag, "[AdMobInter] placement=$placementKey action=preload status=SKIP reason=gate")
            return
        }

        val adUnitId = AdsManager.getAdUnitId(placementKey)
        if (adUnitId.isNullOrBlank()) {
            AdsLog.w(logTag, "[AdMobInter] placement=$placementKey action=preload status=SKIP reason=no_unit")
            return
        }

        AdsLog.i(logTag, "[AdMobInter] placement=$placementKey action=preload status=REQUESTED unit=${AdsLog.maskAdUnitId(adUnitId)}")
        InterstitialAd.load(
            context,
            adUnitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    loadAttempt = 0
                    interstitialAd = ad
                    AdsLog.i(logTag, "[AdMobInter] placement=$placementKey action=preload status=LOADED")
                    callbacks?.onLoaded()
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                    callbacks?.onFailed(error)
                    AdsLog.w(logTag, "[AdMobInter] placement=$placementKey action=preload status=FAIL domain=${error.domain} code=${error.code} message=${error.message}")
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
            AdsLog.d(logTag, "[AdMobInter] placement=$placementKey action=retry attempt=$loadAttempt backoffMs=$backoffMs")
            delay(backoffMs)
            preload(context, callbacks)
        }
    }

    fun show(activity: Activity, callbacks: Callbacks? = null, onComplete: (() -> Unit)? = null) {
        val ad = interstitialAd
        if (ad == null || !AdsManager.shouldShowNow(placementKey)) {
            AdsLog.i(logTag, "[AdMobInter] placement=$placementKey action=show status=SKIP reason=not_ready")
            onComplete?.invoke()
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                callbacks?.onShowed()
                AdsManager.markShown(placementKey)
                interstitialAd = null
                AdsLog.i(logTag, "[AdMobInter] placement=$placementKey action=show status=SHOWED")
            }

            override fun onAdDismissedFullScreenContent() {
                callbacks?.onDismissed()
                AdsLog.i(logTag, "[AdMobInter] placement=$placementKey action=show status=DISMISSED")
                onComplete?.invoke()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                callbacks?.onFailedToShow(adError)
                interstitialAd = null
                AdsLog.w(logTag, "[AdMobInter] placement=$placementKey action=show status=FAIL domain=${adError.domain} code=${adError.code} message=${adError.message}")
                onComplete?.invoke()
            }
        }

        AdsLog.i(logTag, "[AdMobInter] placement=$placementKey action=show status=REQUESTED")
        ad.show(activity)
    }

    fun clear() {
        retryJob?.cancel()
        interstitialAd = null
        AdsLog.d(logTag, "[AdMobInter] placement=$placementKey action=clear")
    }
}
