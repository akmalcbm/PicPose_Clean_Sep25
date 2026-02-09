package com.picpose.bestphotographyapp.ui.ads

import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.picpose.bestphotographyapp.R
import com.picpose.bestphotographyapp.presentation.ads.AdsLog
import com.picpose.bestphotographyapp.presentation.ads.AdsConfigState
import com.picpose.bestphotographyapp.presentation.ads.AdsManager
import com.picpose.bestphotographyapp.presentation.ads.NativeAdController

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
        AndroidView(
            factory = { ctx -> createNativeAdView(ctx) },
            update = { adView ->
                nativeAd?.let {
                    AdsLog.d(AdsLog.TAG_UI, "[AdsUI] component=NativeAdSection placement=$placementKey action=bind")
                    bindNativeAdToView(adView, it)
                }
            },
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )
    }
}

private fun createNativeAdView(context: android.content.Context): NativeAdView {
    val adView = NativeAdView(context)

    val root = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(32, 32, 32, 32)
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    val mediaView = MediaView(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (180 * context.resources.displayMetrics.density).toInt()
        )
    }

    val headlineView = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, 16, 0, 8)
    }

    val bodyView = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setPadding(0, 0, 0, 16)
    }

    val ctaButton = Button(context).apply {
        setPadding(32, 16, 32, 16)
    }

    root.addView(mediaView)
    root.addView(headlineView)
    root.addView(bodyView)
    root.addView(ctaButton)

    adView.apply {
        addView(root)
        this.mediaView = mediaView
        this.headlineView = headlineView
        this.bodyView = bodyView
        this.callToActionView = ctaButton
    }

    return adView
}

private fun bindNativeAdToView(adView: NativeAdView, ad: NativeAd) {
    (adView.headlineView as? TextView)?.text = ad.headline

    (adView.bodyView as? TextView)?.apply {
        text = ad.body ?: ""
        visibility = if (ad.body.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    (adView.callToActionView as? Button)?.apply {
        text = ad.callToAction ?: adView.context.getString(R.string.learn_more)
        visibility = if (ad.callToAction.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    adView.mediaView?.mediaContent = ad.mediaContent
    adView.setNativeAd(ad)
}
