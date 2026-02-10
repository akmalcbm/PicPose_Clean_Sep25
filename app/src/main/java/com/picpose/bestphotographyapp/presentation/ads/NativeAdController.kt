package com.picpose.bestphotographyapp.presentation.ads

import android.content.Context
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class NativeAdController(
    private val placementKey: String,
    private val maxRetries: Int = 3,
    private val logTag: String = AdsLog.TAG_NATIVE
) {

    interface Callbacks {
        fun onLoaded(ad: NativeAd) {}
        fun onFailed(error: LoadAdError) {}
        fun onUnavailable(reason: String) {}
        fun onImpression() {}
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val isLoading = AtomicBoolean(false)
    private var retryAttempt = 0
    private var retryJob: Job? = null
    private var nativeAd: NativeAd? = null

    fun load(context: Context, forceReload: Boolean = false, callbacks: Callbacks? = null) {
        if (!forceReload && nativeAd != null) {
            AdsLog.d(logTag, "[AdMobNative] placement=$placementKey action=load status=REUSE")
            nativeAd?.let { callbacks?.onLoaded(it) }
            return
        }

        if (!AdsManager.canShowAds() || !AdsManager.shouldShowNow(placementKey)) {
            val reason = if (!AdsManager.canShowAds()) "ADS_DISABLED" else "FREQUENCY_BLOCK"
            AdsLog.i(logTag, "[AdMobNative] placement=$placementKey action=load status=SKIP reason=$reason")
            callbacks?.onUnavailable(reason)
            return
        }

        val adUnitId = AdsManager.getAdUnitId(placementKey)
        if (adUnitId.isNullOrBlank()) {
            AdsLog.w(logTag, "[AdMobNative] placement=$placementKey action=load status=SKIP reason=no_unit")
            callbacks?.onUnavailable("NO_UNIT")
            return
        }

        if (!isLoading.compareAndSet(false, true)) {
            AdsLog.d(logTag, "[AdMobNative] placement=$placementKey action=load status=SKIP reason=already_loading")
            return
        }

        if (forceReload) {
            nativeAd?.destroy()
            nativeAd = null
        }

        AdsLog.i(logTag, "[AdMobNative] placement=$placementKey action=load status=REQUESTED unit=${AdsLog.maskAdUnitId(adUnitId)}")
        val loader = AdLoader.Builder(context, adUnitId)
            .forNativeAd { loadedAd ->
                isLoading.set(false)
                retryAttempt = 0
                nativeAd?.destroy()
                nativeAd = loadedAd
                callbacks?.onLoaded(loadedAd)
                AdsManager.markShown(placementKey)
                AdsLog.i(logTag, "[AdMobNative] placement=$placementKey action=load status=LOADED")
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    isLoading.set(false)
                    callbacks?.onFailed(error)
                    AdsLog.w(logTag, "[AdMobNative] placement=$placementKey action=load status=FAIL domain=${error.domain} code=${error.code} message=${error.message}")
                    scheduleRetry(context, callbacks)
                }

                override fun onAdImpression() {
                    callbacks?.onImpression()
                    AdsLog.d(logTag, "[AdMobNative] placement=$placementKey action=impression")
                }
            })
            .withNativeAdOptions(NativeAdOptions.Builder().build())
            .build()

        loader.loadAd(AdRequest.Builder().build())
    }

    private fun scheduleRetry(context: Context, callbacks: Callbacks?) {
        if (!AdsManager.canShowAds()) return
        if (retryAttempt >= maxRetries) return

        retryAttempt += 1
        retryJob?.cancel()
        retryJob = scope.launch {
            val backoffMs = (1_000L * (1 shl (retryAttempt - 1))).coerceAtMost(8_000L)
            AdsLog.d(logTag, "[AdMobNative] placement=$placementKey action=retry attempt=$retryAttempt backoffMs=$backoffMs")
            delay(backoffMs)
            load(context, forceReload = true, callbacks = callbacks)
        }
    }

    fun getCurrentAd(): NativeAd? = nativeAd

    fun clear() {
        retryJob?.cancel()
        isLoading.set(false)
        nativeAd?.destroy()
        nativeAd = null
        AdsLog.d(logTag, "[AdMobNative] placement=$placementKey action=clear")
    }
}
