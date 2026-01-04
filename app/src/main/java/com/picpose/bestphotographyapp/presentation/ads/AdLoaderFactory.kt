package com.picpose.bestphotographyapp.presentation.ads

import android.content.Context
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions

object AdLoaderFactory {

    fun loadNative(
        context: Context,
        placement: AdzPlacement,
        onLoaded: (NativeAd) -> Unit
    ) {
        val unit = placement.units.sortedBy { it.priority }.firstOrNull() ?: return

        val adLoader = AdLoader.Builder(context, unit.ad_unit_id)
            .forNativeAd(onLoaded)
            .withNativeAdOptions(NativeAdOptions.Builder().build())
            .build()

        adLoader.loadAd(AdRequest.Builder().build())
    }
}
