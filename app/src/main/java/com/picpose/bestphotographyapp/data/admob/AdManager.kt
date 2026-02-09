package com.picpose.bestphotographyapp.data.admob

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.picpose.bestphotographyapp.presentation.ads.AdsLog
import com.picpose.bestphotographyapp.presentation.ads.AdsManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

class AdManager private constructor() {

    companion object {
        @Volatile
        private var INSTANCE: AdManager? = null

        fun getInstance(): AdManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AdManager().also { INSTANCE = it }
            }
        }
    }

    private var interstitial1Id: String = ""
    private var interstitial2Id: String = ""

    private val clickCounter = AtomicInteger(0)
    private var showAdEveryNClicks: Int = 3

    private var interstitialAd1: InterstitialAd? = null
    private var interstitialAd2: InterstitialAd? = null

    private var useAd1Next = true

    fun initialize(clickFrequency: Int = 3) {
        interstitial1Id = AdsManager.interstitialIdOrNull().orEmpty()
        interstitial2Id = AdsManager.interstitialId2OrNull().orEmpty()
        showAdEveryNClicks = clickFrequency.coerceAtLeast(1)

        AdsLog.d(
            AdsLog.TAG_INTER,
            "[AdMobInter] action=initialize hasUnit1=${interstitial1Id.isNotBlank()} hasUnit2=${interstitial2Id.isNotBlank()} clickFrequency=$showAdEveryNClicks"
        )
    }

    fun preloadAds(context: Context?) {
        if (context == null) return
        if (!AdsManager.canLoadAnyAd()) return

        interstitial1Id = AdsManager.interstitialIdOrNull().orEmpty()
        interstitial2Id = AdsManager.interstitialId2OrNull().orEmpty()

        if (interstitial1Id.isBlank() && interstitial2Id.isBlank()) {
            AdsLog.w(AdsLog.TAG_INTER, "[AdMobInter] action=preload status=SKIP reason=no_units")
            return
        }

        loadInterstitialAd1(context)
        loadInterstitialAd2(context)
    }

    fun shouldShowInterstitial(): Boolean {
        val count = clickCounter.get()
        if (count <= 0 || count % showAdEveryNClicks != 0) return false

        val canShowPrimary = AdsManager.canShowPlacement(
            placementKey = AdsManager.KEY_INTERSTITIAL_1,
            expectedType = "interstitial"
        )
        val canShowSecondary = AdsManager.canShowPlacement(
            placementKey = AdsManager.KEY_INTERSTITIAL_2,
            expectedType = "interstitial"
        )

        return canShowPrimary || canShowSecondary
    }

    fun incrementClickCount() {
        clickCounter.incrementAndGet()
    }

    fun resetClickCount() {
        clickCounter.set(0)
    }

    suspend fun showInterstitialAndWait(activity: Activity): Boolean =
        withContext(Dispatchers.Main) {
            val deferred = CompletableDeferred<Boolean>()

            try {
                val adToShow = if (useAd1Next) {
                    interstitialAd1.also { useAd1Next = false }
                } else {
                    interstitialAd2.also { useAd1Next = true }
                }

                if (adToShow == null) {
                    AdsLog.w(AdsLog.TAG_INTER, "[AdMobInter] action=show status=SKIP reason=ad_not_loaded")
                    deferred.complete(false)
                    return@withContext false
                }

                adToShow.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        val shownPlacementKey = if (useAd1Next) {
                            AdsManager.KEY_INTERSTITIAL_2
                        } else {
                            AdsManager.KEY_INTERSTITIAL_1
                        }
                        AdsManager.markPlacementShown(shownPlacementKey, "interstitial")

                        if (useAd1Next) {
                            loadInterstitialAd2(activity)
                        } else {
                            loadInterstitialAd1(activity)
                        }
                        deferred.complete(true)
                    }

                    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                        AdsLog.w(AdsLog.TAG_INTER, "[AdMobInter] action=show status=FAIL domain=${adError.domain} code=${adError.code} message=${adError.message}")
                        deferred.complete(false)
                    }

                    override fun onAdShowedFullScreenContent() {
                        if (useAd1Next) {
                            interstitialAd2 = null
                        } else {
                            interstitialAd1 = null
                        }
                    }
                }

                adToShow.show(activity)
                AdsLog.i(AdsLog.TAG_INTER, "[AdMobInter] action=show status=REQUESTED")
            } catch (e: Exception) {
                AdsLog.e(AdsLog.TAG_INTER, "[AdMobInter] action=show status=EXCEPTION error=${e.message}", e)
                deferred.complete(false)
            }

            deferred.await()
        }

    private fun loadInterstitialAd1(context: Context) {
        if (interstitialAd1 != null) return
        interstitial1Id = AdsManager.interstitialIdOrNull().orEmpty()
        if (interstitial1Id.isBlank()) return
        if (!AdsManager.canShowPlacement(AdsManager.KEY_INTERSTITIAL_1, "interstitial")) return

        InterstitialAd.load(
            context,
            interstitial1Id,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd1 = ad
                    AdsLog.i(AdsLog.TAG_INTER, "[AdMobInter] action=preload placement=interstitial_1 status=LOADED unit=${AdsLog.maskAdUnitId(interstitial1Id)}")
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    interstitialAd1 = null
                    AdsLog.w(AdsLog.TAG_INTER, "[AdMobInter] action=preload placement=interstitial_1 status=FAIL domain=${loadAdError.domain} code=${loadAdError.code} message=${loadAdError.message}")
                }
            }
        )
    }

    private fun loadInterstitialAd2(context: Context) {
        if (interstitialAd2 != null) return
        interstitial2Id = AdsManager.interstitialId2OrNull().orEmpty()
        if (interstitial2Id.isBlank()) return
        if (!AdsManager.canShowPlacement(AdsManager.KEY_INTERSTITIAL_2, "interstitial")) return

        InterstitialAd.load(
            context,
            interstitial2Id,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd2 = ad
                    AdsLog.i(AdsLog.TAG_INTER, "[AdMobInter] action=preload placement=interstitial_2 status=LOADED unit=${AdsLog.maskAdUnitId(interstitial2Id)}")
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    interstitialAd2 = null
                    AdsLog.w(AdsLog.TAG_INTER, "[AdMobInter] action=preload placement=interstitial_2 status=FAIL domain=${loadAdError.domain} code=${loadAdError.code} message=${loadAdError.message}")
                }
            }
        )
    }

    fun cleanup() {
        interstitialAd1 = null
        interstitialAd2 = null
    }
}
