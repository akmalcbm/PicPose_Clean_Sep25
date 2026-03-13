/**
 * ---
 * File: NativeAdComposable.kt
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
 * UI (Compose) -> ViewModel -> Repository -> Local/Remote Data Source -> Room/API
 *
 * Maintainer Notes:
 * - Prefer stateless composables and keep side effects inside well-scoped effect APIs.
 * - Document new navigation arguments and remember that recomposition can re-run this code frequently.
 * ---
 */

package com.picpose.bestphotographyapp.components.ads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.picpose.bestphotographyapp.components.ads.AdsConfigState
import com.picpose.bestphotographyapp.components.ads.AdsLog
import com.picpose.bestphotographyapp.components.ads.AdsManager
import com.picpose.bestphotographyapp.components.ads.LargeNativeAdCard
import com.picpose.bestphotographyapp.components.ads.NativeAdController

/**
 * Placement-key based native ad composable.
 * Loads once per placement key, reuses ad, and disposes safely.
 */
@Composable
fun NativeAdSection(
    placementKey: String = AdsManager.KEY_NATIVE_AD,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configState by AdsManager.configState.collectAsState()
    val canShowAds = AdsManager.canShowAds()
    if (configState is AdsConfigState.Loading) {
        AdsLog.d(
            AdsLog.TAG_UI,
            "[AdsUI] component=NativeAdSection placement=$placementKey action=wait reason=CONFIG_LOADING"
        )
        return
    }

    val controller = remember(placementKey) {
        NativeAdController(
            placementKey = placementKey,
            logTag = AdsLog.TAG_NATIVE
        )
    }

    var nativeAd by remember(placementKey) { mutableStateOf<NativeAd?>(null) }
    var hasError by remember(placementKey) { mutableStateOf(false) }

    DisposableEffect(controller, placementKey, configState, canShowAds) {
        if (!canShowAds) {
            AdsLog.i(
                AdsLog.TAG_UI,
                "[AdsUI] component=NativeAdSection placement=$placementKey action=skip reason=global_gate_or_policy"
            )
            onDispose { }
        } else {
            AdsLog.i(AdsLog.TAG_UI, "[AdsUI] component=NativeAdSection placement=$placementKey action=request_load")
            controller.load(
                context = context,
                forceReload = false,
                callbacks = object : NativeAdController.Callbacks {
                    override fun onLoaded(ad: NativeAd) {
                        nativeAd = ad
                        hasError = false
                        AdsLog.i(AdsLog.TAG_UI, "[AdsUI] component=NativeAdSection placement=$placementKey action=loaded")
                    }

                    override fun onFailed(error: LoadAdError) {
                        hasError = true
                        AdsLog.w(
                            AdsLog.TAG_UI,
                            "[AdsUI] component=NativeAdSection placement=$placementKey action=failed domain=${error.domain} code=${error.code} message=${error.message}"
                        )
                    }
                }
            )

            onDispose {
                AdsLog.d(AdsLog.TAG_UI, "[AdsUI] component=NativeAdSection placement=$placementKey action=dispose")
                nativeAd = null
                controller.clear()
            }
        }
    }

    if (nativeAd != null && !hasError) {
        LargeNativeAdCard(
            nativeAd = nativeAd,
            modifier = modifier
        )
    }
}
