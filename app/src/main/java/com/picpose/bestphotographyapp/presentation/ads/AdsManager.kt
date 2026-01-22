package com.picpose.bestphotographyapp.presentation.ads

import com.picpose.bestphotographyapp.data.models.AdConfig
import com.picpose.bestphotographyapp.utils.Constants

object AdsManager {

    private var liveConfig: AdConfig? = null

    // Call once after API success
    fun init(config: AdConfig) {
        liveConfig = config
    }

    fun isAdsEnabled(): Boolean = true   // future flag possible

    fun appId(): String =
        if (Constants.IS_TEST_ADS)
            Constants.TEST_APP_ID
        else
            liveConfig?.appId ?: Constants.TEST_APP_ID

    fun bannerId(): String =
        if (Constants.IS_TEST_ADS)
            Constants.TEST_BANNER_ID
        else
            liveConfig?.bannerId ?: Constants.TEST_BANNER_ID

    fun bannerId2(): String =
        if (Constants.IS_TEST_ADS)
            Constants.TEST_BANNER_ID_2
        else
            liveConfig?.bannerId2 ?: Constants.TEST_BANNER_ID_2

    fun interstitialId(): String =
        if (Constants.IS_TEST_ADS)
            Constants.TEST_INTERSTITIAL_ID
        else
            liveConfig?.interstitialId ?: Constants.TEST_INTERSTITIAL_ID

    fun interstitialId2(): String =
        if (Constants.IS_TEST_ADS)
            Constants.TEST_INTERSTITIAL_ID_2
        else
            liveConfig?.interstitialId2 ?: Constants.TEST_INTERSTITIAL_ID_2


    fun nativeId(): String =
        if (Constants.IS_TEST_ADS)
            Constants.TEST_NATIVE_ID
        else
            liveConfig?.nativeId ?: Constants.TEST_NATIVE_ID

    fun nativeId2(): String =
        if (Constants.IS_TEST_ADS)
            Constants.TEST_NATIVE_ID_2
        else
            liveConfig?.nativeId2 ?: Constants.TEST_NATIVE_ID_2

    fun nativeId3(): String =
        if (Constants.IS_TEST_ADS)
            Constants.TEST_NATIVE_ID_3
        else
            liveConfig?.nativeId3 ?: Constants.TEST_NATIVE_ID_3

    fun rewardedId(): String =
        if (Constants.IS_TEST_ADS)
            Constants.TEST_REWARDED_ID
        else
            liveConfig?.rewardedId ?: Constants.TEST_REWARDED_ID
}
