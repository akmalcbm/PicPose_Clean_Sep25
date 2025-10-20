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
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView

/**
 * NativeAdSection - Composable for displaying native ads
 * 
 * Displays a native ad in a Compose layout using AndroidView
 * Falls back gracefully if ad fails to load
 * 
 * @param adUnitId The ad unit ID to use (can be from server or test ID)
 * @param modifier Optional modifier for styling
 */
@Composable
fun NativeAdSection(
    adUnitId: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    
    // Load native ad
    DisposableEffect(adUnitId) {
        isLoading = true
        hasError = false
        
        val adLoader = AdLoader.Builder(context, adUnitId)
            .forNativeAd { ad ->
                // Clean up old ad if exists
                nativeAd?.destroy()
                nativeAd = ad
                isLoading = false
                hasError = false
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    isLoading = false
                    hasError = true
                }
            })
            .withNativeAdOptions(
                NativeAdOptions.Builder()
                    .build()
            )
            .build()
        
        adLoader.loadAd(AdRequest.Builder().build())
        
        onDispose {
            nativeAd?.destroy()
            nativeAd = null
        }
    }
    
    // Only show ad if loaded successfully
    if (nativeAd != null && !hasError) {
        AndroidView(
            factory = { context ->
                createNativeAdView(context, nativeAd!!)
            },
            update = { adView ->
                bindNativeAdToView(adView, nativeAd!!)
            },
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )
    }
    // If loading or error, don't show anything (graceful fallback)
}

/**
 * Create the native ad view layout
 */
private fun createNativeAdView(context: android.content.Context, ad: NativeAd): NativeAdView {
    val adView = NativeAdView(context)
    
    // Create layout container
    val root = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(32, 32, 32, 32)
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
    
    // Media view for ad image/video
    var mediaView = MediaView(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (180 * context.resources.displayMetrics.density).toInt()
        )
    }
    
    // Headline text
    var headlineView = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, 16, 0, 8)
    }
    
    // Body text
    var bodyView = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setPadding(0, 0, 0, 16)
    }
    
    // Call to action button
    val ctaButton = Button(context).apply {
        setPadding(32, 16, 32, 16)
    }
    
    // Add views to layout
    root.addView(mediaView)
    root.addView(headlineView)
    root.addView(bodyView)
    root.addView(ctaButton)
    
    // Set up the NativeAdView
    adView.apply {
        addView(root)
        mediaView = mediaView
        headlineView = headlineView
        bodyView = bodyView
        callToActionView = ctaButton
    }
    
    return adView
}

/**
 * Bind native ad data to the view
 */
private fun bindNativeAdToView(adView: NativeAdView, ad: NativeAd) {
    // Set headline
    (adView.headlineView as? TextView)?.text = ad.headline
    
    // Set body (hide if empty)
    (adView.bodyView as? TextView)?.apply {
        text = ad.body ?: ""
        visibility = if (ad.body.isNullOrBlank()) View.GONE else View.VISIBLE
    }
    
    // Set call to action button (hide if empty)
    (adView.callToActionView as? Button)?.apply {
        text = ad.callToAction ?: "Learn More"
        visibility = if (ad.callToAction.isNullOrBlank()) View.GONE else View.VISIBLE
    }
    
    // Set media content
    adView.mediaView?.mediaContent = ad.mediaContent
    
    // Register the native ad
    adView.setNativeAd(ad)
}
