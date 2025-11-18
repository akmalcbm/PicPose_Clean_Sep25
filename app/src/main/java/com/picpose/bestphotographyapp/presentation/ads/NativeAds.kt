package com.picpose.bestphotographyapp.presentation.ads

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Outline
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView

// Reusable style enum for your screens (List / Grid / Explore)
enum class NativeAdStyle { Compact, LargeMedia }

// 🔑 App-specific constant key for tagging the badge view (NO crash)


/* ----------------------
   Shimmer Placeholder
----------------------- */
@Composable
private fun NativeAdShimmer(
    modifier: Modifier,
    corner: Int = 12
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(corner.dp),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
        )
    }
}

/* ----------------------
   Compact Inline Ad UI
----------------------- */
@Composable
fun InlineNativeAdCard(
    nativeAd: NativeAd?,
    modifier: Modifier = Modifier
) {
    if (nativeAd == null) {
        NativeAdShimmer(modifier.height(90.dp))
        return
    }

    val fade = remember { Animatable(0f) }
    LaunchedEffect(nativeAd) { fade.animateTo(1f, tween(350)) }

    Card(
        modifier = modifier.alpha(fade.value),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(3.dp)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                val density = context.resources.displayMetrics.density
                val adView = NativeAdView(context)

                val wrapper = FrameLayout(context)

                val root = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(14, 14, 14, 14)
                }

                // Row: Icon + Headline
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }

                val icon = ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        42.toPx(context),
                        42.toPx(context)
                    )
                    clipToOutline = true
                    outlineProvider = roundedOutline(10f, context)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }

                val headline = TextView(context).apply {
                    textSize = 14f
                    setTypeface(null, Typeface.BOLD)
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setPadding(12, 0, 0, 0)
                }

                row.addView(icon)
                row.addView(
                    headline,
                    LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                )

                // Full-width CTA
                val cta = Button(context).apply {
                    isAllCaps = false
                    textSize = 12f
                    minHeight = 36.toPx(context)
                }

                val badge = sponsoredChip(context)

                root.addView(row)
                root.addView(
                    cta,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                )

                wrapper.apply {
                    addView(root)
                    addView(badge, badgeLayout())
                }

                adView.apply {
                    addView(wrapper)
                    iconView = icon
                    headlineView = headline
                    callToActionView = cta

                    // ✅ SAFE: Fixed constant key, not view ID
                    setTag(badge)
                }
            },
            update = { adView ->
                updateNativeUI(adView, nativeAd)
            }
        )
    }
}

/* ----------------------
   Large Media Ad UI
----------------------- */
@Composable
fun LargeNativeAdCard(
    nativeAd: NativeAd?,
    modifier: Modifier = Modifier
) {
    if (nativeAd == null) {
        NativeAdShimmer(modifier.height(220.dp), corner = 14)
        return
    }

    val fade = remember { Animatable(0f) }
    LaunchedEffect(nativeAd) { fade.animateTo(1f, tween(350)) }

    Card(
        modifier = modifier.alpha(fade.value),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(3.dp)
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            factory = { context ->
                val density = context.resources.displayMetrics.density
                val adView = NativeAdView(context)

                val wrapper = FrameLayout(context)

                val root = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                }

                val media = MediaView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        160.toPx(context)
                    )
                    clipToOutline = true
                    outlineProvider = roundedOutline(16f, context)
                }

                val headline = TextView(context).apply {
                    setTypeface(null, Typeface.BOLD)
                    textSize = 16f
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setPadding(0, 10, 0, 0)
                }

                val cta = Button(context).apply {
                    isAllCaps = false
                    textSize = 14f
                    minHeight = 44.toPx(context)
                }

                val badge = sponsoredChip(context)

                root.addView(media)
                root.addView(headline)
                root.addView(
                    cta,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                )

                wrapper.apply {
                    addView(root)
                    addView(badge, badgeLayout())
                }

                adView.apply {
                    addView(wrapper)
                    mediaView = media
                    headlineView = headline
                    callToActionView = cta

                    // ✅ SAFE: Fixed constant key again
                    setTag(badge)
                }
            },
            update = { adView ->
                updateNativeUI(adView, nativeAd)
            }
        )
    }
}

/* ----------------------
   Helpers
----------------------- */

private fun sponsoredChip(context: Context): TextView =
    TextView(context).apply {
        text = "Ad"
        textSize = 10f
        setPadding(12, 4, 12, 4)
        backgroundTintList = ColorStateList.valueOf(Color.DarkGray.toArgb())
        setTextColor(Color.White.toArgb())
        clipToOutline = true
        outlineProvider = roundedOutline(50f, context)
    }

private fun badgeLayout() =
    FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.WRAP_CONTENT,
        FrameLayout.LayoutParams.WRAP_CONTENT,
        Gravity.TOP or Gravity.START
    ).apply {
        setMargins(12, 12, 0, 0)
    }

private fun roundedOutline(radiusDp: Float, context: Context) =
    object : ViewOutlineProvider() {
        override fun getOutline(v: View, outline: Outline) {
            val rPx = radiusDp * context.resources.displayMetrics.density
            outline.setRoundRect(0, 0, v.width, v.height, rPx)
        }
    }

/**
 * 🔧 Central function that wires NativeAd → NativeAdView UI
 * (No Compose APIs here → safe for AndroidView.update)
 */
private fun updateNativeUI(
    adView: NativeAdView,
    nativeAd: NativeAd
) {
    // Icon
    (adView.iconView as? ImageView)?.setImageDrawable(nativeAd.icon?.drawable)

    // Headline
    (adView.headlineView as? TextView)?.apply {
        text = nativeAd.headline
        setTextColor(Color.Black.toArgb())
    }

    // CTA
    (adView.callToActionView as? Button)?.apply {
        text = nativeAd.callToAction ?: "Open"
        backgroundTintList =
            ColorStateList.valueOf(Color(0xFF2563EB).toArgb())
        setTextColor(Color.White.toArgb())
    }

    // Sponsored pill (if present)
    val badge = adView.getTag() as? TextView
    badge?.apply {
        backgroundTintList =
            ColorStateList.valueOf(Color(0xFF111827).toArgb())
        setTextColor(Color(0xFFF9FAFB).toArgb())
    }

    adView.setNativeAd(nativeAd)
}

/**
 * Simple px helper – **no `dp` extension used here**
 */
private fun Int.toPx(context: Context): Int =
    (this * context.resources.displayMetrics.density).toInt()
