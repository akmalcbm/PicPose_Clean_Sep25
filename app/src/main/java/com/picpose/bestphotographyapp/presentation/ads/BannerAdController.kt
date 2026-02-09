package com.picpose.bestphotographyapp.presentation.ads

import android.content.Context
import android.widget.FrameLayout
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BannerAdController(
    private val placementKey: String,
    private val adSize: AdSize = AdSize.BANNER,
    private val maxRetries: Int = 3,
    private val logTag: String = AdsLog.TAG_BANNER
) {

    interface Callbacks {
        fun onLoaded() {}
        fun onFailed(error: LoadAdError) {}
        fun onClicked() {}
        fun onImpression() {}
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var currentAdView: AdView? = null
    private var retryJob: Job? = null
    private var refreshJob: Job? = null
    private var retryAttempt: Int = 0

    fun attach(container: FrameLayout, context: Context, callbacks: Callbacks? = null) {
        AdsLog.d(logTag, "[AdMobBanner] placement=$placementKey action=attach")

        if (!AdsManager.canShowAds()) {
            AdsLog.i(logTag, "[AdMobBanner] placement=$placementKey action=attach status=SKIP reason=global_gate")
            return
        }
        if (!AdsManager.shouldShowNow(placementKey)) {
            AdsLog.i(logTag, "[AdMobBanner] placement=$placementKey action=attach status=SKIP reason=frequency_or_placement_gate")
            return
        }

        val adUnitId = AdsManager.getAdUnitId(placementKey)
        if (adUnitId.isNullOrBlank()) {
            AdsLog.w(logTag, "[AdMobBanner] placement=$placementKey action=attach status=SKIP reason=no_unit")
            return
        }

        val existing = currentAdView
        if (existing != null && existing.adUnitId == adUnitId) {
            container.removeAllViews()
            container.addView(existing)
            AdsLog.d(logTag, "[AdMobBanner] placement=$placementKey action=attach status=REUSE unit=${AdsLog.maskAdUnitId(adUnitId)}")
            return
        }

        existing?.destroy()

        val adView = AdView(context).apply {
            this.adUnitId = adUnitId
            setAdSize(this@BannerAdController.adSize)
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    retryAttempt = 0
                    callbacks?.onLoaded()
                    AdsManager.markShown(placementKey)
                    AdsLog.i(logTag, "[AdMobBanner] placement=$placementKey action=load status=LOADED unit=${AdsLog.maskAdUnitId(adUnitId)}")
                    scheduleRefreshIfNeeded(container, context, callbacks)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    callbacks?.onFailed(error)
                    AdsLog.w(
                        logTag,
                        "[AdMobBanner] placement=$placementKey action=load status=FAIL domain=${error.domain} code=${error.code} message=${error.message}"
                    )
                    scheduleRetry(container, context, callbacks)
                }

                override fun onAdClicked() {
                    callbacks?.onClicked()
                    AdsLog.d(logTag, "[AdMobBanner] placement=$placementKey action=clicked")
                }

                override fun onAdImpression() {
                    callbacks?.onImpression()
                    AdsLog.d(logTag, "[AdMobBanner] placement=$placementKey action=impression")
                }
            }
        }

        currentAdView = adView
        container.removeAllViews()
        container.addView(adView)
        AdsLog.i(logTag, "[AdMobBanner] placement=$placementKey action=load status=REQUESTED unit=${AdsLog.maskAdUnitId(adUnitId)}")
        adView.loadAd(AdRequest.Builder().build())
    }

    private fun scheduleRetry(
        container: FrameLayout,
        context: Context,
        callbacks: Callbacks?
    ) {
        retryAttempt += 1
        if (retryAttempt > maxRetries) {
            AdsLog.w(logTag, "[AdMobBanner] placement=$placementKey action=retry status=SKIP reason=max_retries")
            return
        }
        if (!AdsManager.canShowAds()) {
            AdsLog.w(logTag, "[AdMobBanner] placement=$placementKey action=retry status=SKIP reason=global_gate")
            return
        }

        retryJob?.cancel()
        retryJob = scope.launch {
            val backoffMs = (1_000L * (1 shl (retryAttempt - 1))).coerceAtMost(8_000L)
            AdsLog.d(logTag, "[AdMobBanner] placement=$placementKey action=retry attempt=$retryAttempt backoffMs=$backoffMs")
            delay(backoffMs)
            attach(container, context, callbacks)
        }
    }

    private fun scheduleRefreshIfNeeded(
        container: FrameLayout,
        context: Context,
        callbacks: Callbacks?
    ) {
        refreshJob?.cancel()
        val refreshSeconds = AdsManager.getPlacement(placementKey)?.refreshSeconds ?: return
        if (refreshSeconds <= 0) return

        refreshJob = scope.launch {
            AdsLog.d(logTag, "[AdMobBanner] placement=$placementKey action=refresh_schedule refreshSeconds=$refreshSeconds")
            delay(refreshSeconds * 1_000L)
            if (!AdsManager.canShowAds()) return@launch
            attach(container, context, callbacks)
        }
    }

    fun clear() {
        retryJob?.cancel()
        refreshJob?.cancel()
        currentAdView?.destroy()
        currentAdView = null
        AdsLog.d(logTag, "[AdMobBanner] placement=$placementKey action=clear")
    }
}
