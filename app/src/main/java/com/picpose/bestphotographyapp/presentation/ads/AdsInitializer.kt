package com.picpose.bestphotographyapp.presentation.ads

import android.content.Context
import com.facebook.ads.AudienceNetworkAds
import com.google.android.gms.ads.MobileAds
import com.picpose.bestphotographyapp.data.repository.AdsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Singleton
class AdsInitializer @Inject constructor(
    private val adsManager: AdsManager,
    private val adsRepository: AdsRepository,
    private val adsFrequencyManager: AdsFrequencyManager,
    private val consentGate: ConsentGate
) {

    fun initSdk(context: Context) {
        AdsLog.init(context)
        val thread = Thread.currentThread().name
        val start = System.currentTimeMillis()
        AdsLog.i(AdsLog.TAG_INIT, "[AdsInit] step=initSdk start thread=$thread")
        runCatching {
            MobileAds.initialize(context) { status ->
                val adapterCount = status.adapterStatusMap.size
                AdsLog.i(
                    AdsLog.TAG_INIT,
                    "[AdsInit] step=initSdk mobileAdsInit=callback adapters=$adapterCount thread=${Thread.currentThread().name}"
                )
            }
            AudienceNetworkAds.initialize(context)
            val duration = System.currentTimeMillis() - start
            AdsLog.i(AdsLog.TAG_INIT, "[AdsInit] step=initSdk status=OK durationMs=$duration thread=$thread")
        }.onFailure {
            AdsLog.e(AdsLog.TAG_INIT, "[AdsInit] step=initSdk status=FAIL error=${it.message}", it)
        }
    }

    fun warmUpOnAppStart(
        context: Context,
        appScope: CoroutineScope,
        forceRefresh: Boolean = false
    ) {
        AdsLog.init(context)
        AdsLog.i(
            AdsLog.TAG_INIT,
            "[AdsInit] step=warmUpOnAppStart forceRefresh=$forceRefresh thread=${Thread.currentThread().name}"
        )
        adsFrequencyManager.initialize(context)
        adsManager.configure(context, consentGate)
        adsManager.bindRepository(adsRepository)

        appScope.launch(Dispatchers.IO) {
            runCatching {
                adsManager.warmUp(forceRefresh = forceRefresh)
                AdsLog.i(AdsLog.TAG_INIT, "[AdsInit] step=warmUpOnAppStart status=OK snapshot=${adsManager.debugSnapshot()}")
            }.onFailure {
                AdsLog.e(AdsLog.TAG_INIT, "[AdsInit] step=warmUpOnAppStart status=FAIL error=${it.message}", it)
            }
        }
    }
}
