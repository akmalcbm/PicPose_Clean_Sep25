package com.picpose.bestphotographyapp.presentation.ads

import com.picpose.bestphotographyapp.data.remote.AdPlacementConfig
import com.picpose.bestphotographyapp.data.remote.AdUnitConfig
import com.picpose.bestphotographyapp.data.remote.AdsConfigResponse

object AdsManager {

    private var config: AdsConfigResponse? = null

    fun init(configResponse: AdsConfigResponse) {
        config = configResponse
    }

    fun isAdsEnabled(): Boolean {
        return config?.global?.ads_enabled == true
    }

    fun getPlacement(key: String): AdPlacementConfig? {
        return config?.placements?.get(key)
    }

    fun getSortedUnits(key: String): List<AdUnitConfig> {
        return getPlacement(key)?.units
            ?.sortedBy { it.priority }
            ?: emptyList()
    }
}

/*
7️⃣ Example: Native Ad Loader (Failover Ready)
loadNativeAd.kt
fun loadNativeAd(
    context: Context,
    placementKey: String,
    onLoaded: (Any) -> Unit
) {
    if (!AdsManager.isAdsEnabled()) return

    val placement = AdsManager.getPlacement(placementKey) ?: return
    val frequency = placement.frequency ?: 1

    if (!AdsFrequencyManager.canShow(placementKey, frequency)) return

    for (unit in AdsManager.getSortedUnits(placementKey)) {

        if (unit.network == "admob") {
            loadAdmobNative(context, unit.ad_unit_id, {
                AdsFrequencyManager.markShown(placementKey)
                onLoaded(it)
                return
            })
        }

        if (unit.network == "meta") {
            loadMetaNative(context, unit.ad_unit_id, {
                AdsFrequencyManager.markShown(placementKey)
                onLoaded(it)
                return
            })
        }
    }
}

8️⃣ Usage in Compose (Example)
LaunchedEffect(Unit) {
    loadNativeAd(
        context = context,
        placementKey = "home_native_1"
    ) { ad ->
        nativeAdState.value = ad
    }
}
*/

