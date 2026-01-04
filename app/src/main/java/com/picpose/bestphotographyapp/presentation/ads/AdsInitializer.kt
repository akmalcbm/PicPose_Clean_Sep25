package com.picpose.bestphotographyapp.presentation.ads

import android.content.Context
import com.facebook.ads.AudienceNetworkAds
import com.google.android.gms.ads.MobileAds

object AdsInitializer {

    fun initAdMob(context: Context) {
        MobileAds.initialize(context)
    }

    fun initMeta(context: Context) {
        AudienceNetworkAds.initialize(context)
    }
}
