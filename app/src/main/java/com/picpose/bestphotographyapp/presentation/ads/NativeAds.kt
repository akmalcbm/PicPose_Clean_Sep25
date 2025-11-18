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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView

/*-----------------------------------------------------------
   ENUM FOR AD STYLES
-----------------------------------------------------------*/
enum class NativeAdStyle { Compact, LargeMedia }

/*-----------------------------------------------------------
   SHIMMER PLACEHOLDER (Universal)
-----------------------------------------------------------*/
@Composable
fun NativeAdShimmer(modifier: Modifier, corner: Int = 12) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(corner.dp),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                )
        )
    }
}

/*-----------------------------------------------------------
   LARGE MEDIA NATIVE AD  (Policy-Safe)
-----------------------------------------------------------*/
@Composable
fun LargeNativeAdCard(
    nativeAd: NativeAd?,
    modifier: Modifier = Modifier
) {
    if (nativeAd == null) {
        NativeAdShimmer(modifier.height(180.dp).padding(top = 8.dp), corner = 14)
        return
    }

    // Extract Compose colors before AndroidView
    val headlineColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val bodyColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val advertiserColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val sponsoredTextColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val sponsoredBackground = MaterialTheme.colorScheme.primary.toArgb()

    val fade = remember { Animatable(0f) }
    LaunchedEffect(nativeAd) { fade.animateTo(1f, tween(320)) }

    Card(
        modifier = modifier
            .alpha(fade.value)
            .padding(top = 8.dp),   // 🔥 Extra top margin for whole card
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(3.dp),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface)
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            factory = { context ->

                val adView = NativeAdView(context)

                val wrapper = FrameLayout(context)

                val root = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                }

                /** MediaView (180dp height + top margin 8dp) */
                val media = MediaView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        200.toPx(context)
                    ).apply {
                        topMargin = 14.toPx(context)   // 🔥 Add ~14dp top margin
                    }
                }


                /** Headline */
                val headline = TextView(context).apply {
                    textSize = 17f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(headlineColor)
                    maxLines = 2
                }

                /** Body */
                val body = TextView(context).apply {
                    textSize = 14f
                    setTextColor(bodyColor)
                    maxLines = 3
                }

                /** Advertiser */
                val advertiser = TextView(context).apply {
                    textSize = 12f
                    setTypeface(null, Typeface.ITALIC)
                    setTextColor(advertiserColor)
                }

                /** CTA */
                val cta = Button(context).apply { isAllCaps = false }

                /** ⭐ Sponsored badge (TOP-LEFT) */
                val badge = TextView(context).apply {
                    text = "Sponsored"
                    textSize = 11f
                    setPadding(10, -12, 10, 4)
                    backgroundTintList = ColorStateList.valueOf(sponsoredBackground)
                    setTextColor(sponsoredTextColor)
                    clipToOutline = true
                    outlineProvider = roundedOutline(30f, context)
                }

                /** Add content */
                root.apply {
                    addView(media)
                    addView(headline)
                    addView(body)
                    addView(advertiser)
                    addView(cta)
                }

                /** ⭐ Position badge TOP-LEFT above MediaView */
                wrapper.apply {
                    addView(root)
                    addView(
                        badge,
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            Gravity.TOP or Gravity.START
                        ).apply {
                            setMargins(6, 6, 0, 0)   // 🔥 Very small margin from top-left
                        }
                    )
                }

                adView.apply {
                    addView(wrapper)
                    mediaView = media
                    headlineView = headline
                    bodyView = body
                    advertiserView = advertiser
                    callToActionView = cta
                    tag = badge
                }

                adView
            },

            update = { adView ->

                (adView.headlineView as? TextView)?.text = nativeAd.headline

                nativeAd.body?.let {
                    (adView.bodyView as TextView).text = it
                    adView.bodyView?.visibility = View.VISIBLE
                } ?: run { adView.bodyView?.visibility = View.GONE }

                nativeAd.advertiser?.let {
                    (adView.advertiserView as TextView).text = it
                    adView.advertiserView?.visibility = View.VISIBLE
                } ?: run { adView.advertiserView?.visibility = View.GONE }

                adView.mediaView?.setMediaContent(nativeAd.mediaContent)

                (adView.callToActionView as? Button)?.text = nativeAd.callToAction

                adView.setNativeAd(nativeAd)
            }
        )
    }
}



/*-----------------------------------------------------------
   COMPACT INLINE NATIVE AD  (Policy-Safe)
-----------------------------------------------------------*/
@Composable
fun InlineNativeAdCard(
    nativeAd: NativeAd?,
    modifier: Modifier = Modifier
) {
    if (nativeAd == null) {
        NativeAdShimmer(modifier.height(110.dp))
        return
    }

    val fade = remember { Animatable(0f) }
    LaunchedEffect(nativeAd) { fade.animateTo(1f, tween(320)) }

    // 🎨 Adaptive Material Colors
    val headlineColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val bodyColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val advertiserColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()

    val sponsoredBackground = MaterialTheme.colorScheme.primary.toArgb()
    val sponsoredTextColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()

    val ctaBackground = MaterialTheme.colorScheme.primary.toArgb()
    val ctaTextColor = MaterialTheme.colorScheme.onPrimary.toArgb()

    Card(
        modifier = modifier.alpha(fade.value),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(3.dp)
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            factory = { context ->

                val adView = NativeAdView(context)

                // Main horizontal row
                val root = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }

                /** ICON */
                val iconView = ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        48.toPx(context),
                        48.toPx(context)
                    ).apply { rightMargin = 12.toPx(context) }

                    clipToOutline = true
                    outlineProvider = roundedOutline(12f, context)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }

                /** TEXT COLUMN */
                val textColumn = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                }

                val headline = TextView(context).apply {
                    textSize = 15f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(headlineColor)   // 🎨 Adaptive
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }

                val body = TextView(context).apply {
                    textSize = 13f
                    setTextColor(bodyColor)       // 🎨 Adaptive
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }

                val advertiser = TextView(context).apply {
                    textSize = 12f
                    setTypeface(null, Typeface.ITALIC)
                    setTextColor(advertiserColor) // 🎨 Adaptive
                }

                val cta = Button(context).apply {
                    isAllCaps = false
                    setTextColor(ctaTextColor)  // 🔥 Auto Color
                    backgroundTintList = ColorStateList.valueOf(ctaBackground)
                }

                textColumn.addView(headline)
                textColumn.addView(body)
                textColumn.addView(advertiser)
                textColumn.addView(cta)

                /** Sponsored badge */
                val badge = TextView(context).apply {
                    text = "Sponsored"
                    textSize = 11f
                    setPadding(10, -12, 10, 4)
                    backgroundTintList = ColorStateList.valueOf(sponsoredBackground)
                    setTextColor(sponsoredTextColor) // 🔥 Auto color
                    clipToOutline = true
                    outlineProvider = roundedOutline(30f, context)
                }

                val wrapper = FrameLayout(context).apply {
                    addView(
                        root.apply {
                            addView(iconView)
                            addView(
                                textColumn,
                                LinearLayout.LayoutParams(
                                    0,
                                    LinearLayout.LayoutParams.WRAP_CONTENT,
                                    1f
                                )
                            )
                        }
                    )
                    addView(
                        badge,
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            Gravity.TOP or Gravity.END
                        )
                    )
                }

                adView.apply {
                    addView(wrapper)
                    headlineView = headline
                    bodyView = body
                    advertiserView = advertiser
                    callToActionView = cta
                    this.iconView = iconView
                    tag = badge
                }

                adView
            },

            update = { adView ->

                // Headline
                (adView.headlineView as? TextView)?.text = nativeAd.headline

                // Body
                nativeAd.body?.let {
                    (adView.bodyView as TextView).text = it
                    adView.bodyView?.visibility = View.VISIBLE
                } ?: run { adView.bodyView?.visibility = View.GONE }

                // Advertiser
                nativeAd.advertiser?.let {
                    (adView.advertiserView as TextView).text = it
                    adView.advertiserView?.visibility = View.VISIBLE
                } ?: run { adView.advertiserView?.visibility = View.GONE }

                // Icon
                nativeAd.icon?.let {
                    (adView.iconView as ImageView).setImageDrawable(it.drawable)
                    adView.iconView?.visibility = View.VISIBLE
                } ?: run { adView.iconView?.visibility = View.GONE }

                // CTA
                (adView.callToActionView as? Button)?.text = nativeAd.callToAction

                adView.setNativeAd(nativeAd)
            }
        )
    }
}




/*-----------------------------------------------------------
   HELPERS
-----------------------------------------------------------*/
private fun badgeLayout() =
    FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.WRAP_CONTENT,
        FrameLayout.LayoutParams.WRAP_CONTENT,
        Gravity.TOP or Gravity.END
    ).apply {
        setMargins(12, 12, 12, 12)
    }

private fun roundedOutline(radiusDp: Float, context: Context) =
    object : ViewOutlineProvider() {
        override fun getOutline(v: View, outline: Outline) {
            outline.setRoundRect(
                0,
                0,
                v.width,
                v.height,
                radiusDp * context.resources.displayMetrics.density
            )
        }
    }

private fun Int.toPx(context: Context): Int =
    (this * context.resources.displayMetrics.density).toInt()
