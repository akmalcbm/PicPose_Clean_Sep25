package com.picpose.bestphotographyapp.presentation.ads

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Outline
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.Button
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
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.picpose.bestphotographyapp.R

/*-----------------------------------------------------------
   ENUM FOR AD STYLES
-----------------------------------------------------------*/
enum class NativeAdStyle { Compact, LargeMedia }

@Composable
fun AdBadge(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .wrapContentHeight()
            .heightIn(min = 20.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        Text(
            text = androidx.compose.ui.res.stringResource(R.string.sponsored),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip
        )
    }
}

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
        NativeAdShimmer(modifier.height(180.dp).padding(top = 1.dp), corner = 14)
        return
    }

    // Extract Compose colors before AndroidView
    val headlineColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val bodyColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val advertiserColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    // 🎨 Match app Primary Button
    val ctaBgColor = MaterialTheme.colorScheme.primary.toArgb()
    val ctaTextColor = MaterialTheme.colorScheme.onPrimary.toArgb()


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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            AdBadge(modifier = Modifier.padding(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 6.dp))
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(start = 12.dp, end = 12.dp, top = 1.dp, bottom = 6.dp),
                factory = { context ->

                    val adView = NativeAdView(context)

                    val root = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        clipToPadding = false
                        clipChildren = false
                    }

                    /** MediaView */
                    val media = MediaView(context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            200.toPx(context)
                        )
                    }


                /** Headline */
                val headline = TextView(context).apply {
                    textSize = 17f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(headlineColor)
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    //includeFontPadding = false
                }

                /** Body */
                val body = TextView(context).apply {
                    textSize = 14f
                    setTextColor(bodyColor)
                    maxLines = 3
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    //includeFontPadding = false
                }

                /** Advertiser */
                val advertiser = TextView(context).apply {
                    textSize = 12f
                    setTypeface(null, Typeface.ITALIC)
                    setTextColor(advertiserColor)
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    includeFontPadding = false
                }


                /** CTA (Clean, Flat, Compose-like) */
                val cta = Button(context).apply {
                    stylePrimaryCta(context, ctaBgColor, ctaTextColor, radiusDp = 14f)
                }

                /** ✅ ADD MARGIN TOP (6–8dp) */
                cta.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 6.toPx(context)   // 👈 6–8dp yahin
                }


                /** Add content */
                root.apply {
                    addView(media)
                    addView(headline)
                    addView(body)
                    addView(advertiser)
                    addView(cta)
                }

                adView.apply {
                    addView(root)
                    mediaView = media
                    headlineView = headline
                    bodyView = body
                    advertiserView = advertiser
                    callToActionView = cta
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

                adView.mediaView?.mediaContent = nativeAd.mediaContent

                (adView.callToActionView as? Button)?.text = nativeAd.callToAction

                    adView.setNativeAd(nativeAd)
                }
            )
        }
    }
}


@Composable
fun LargeNativeAdCardForGrid(
    nativeAd: NativeAd?,
    modifier: Modifier = Modifier
) {
    if (nativeAd == null) {
        NativeAdShimmer(modifier.height(160.dp).padding(top = 0.dp), corner = 12)
        return
    }

    // Extract Compose colors before AndroidView
    val headlineColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val bodyColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val advertiserColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val ctaBgColor = MaterialTheme.colorScheme.primary.toArgb()
    val ctaTextColor = MaterialTheme.colorScheme.onPrimary.toArgb()
    val fade = remember { Animatable(0f) }
    LaunchedEffect(nativeAd) { fade.animateTo(1f, tween(320)) }

    Card(
        modifier = modifier
            .alpha(fade.value)
            .padding(top = 4.dp),   // 🔥 Extra top margin for whole card
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(3.dp),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            AdBadge(modifier = Modifier.padding(start = 10.dp, top = 10.dp, end = 10.dp, bottom = 2.dp))
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(start = 6.dp, end = 6.dp, top = 2.dp, bottom = 4.dp),
                factory = { context ->

                    val adView = NativeAdView(context)

                    val root = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        clipToPadding = false
                        clipChildren = false
                    }

                    /** MediaView */
                    val media = MediaView(context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            160.toPx(context)
                        )
                    }


                /** Headline */
                val headline = TextView(context).apply {
                    textSize = 15f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(headlineColor)
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    //includeFontPadding = false
                }

                /** Body */
                val body = TextView(context).apply {
                    textSize = 12f
                    setTextColor(bodyColor)
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    includeFontPadding = false
                }

                /** Advertiser */
                val advertiser = TextView(context).apply {
                    textSize = 11f
                    setTypeface(null, Typeface.ITALIC)
                    setTextColor(advertiserColor)
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    includeFontPadding = false
                }

                /** CTA */
                val cta = Button(context).apply {
                    stylePrimaryCta(context, ctaBgColor, ctaTextColor, radiusDp = 12f, horizontalDp = 12, verticalDp = 8)
                    textSize = 12f
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }
                cta.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 4.toPx(context)
                }

                /** Add content */
                root.apply {
                    addView(media)
                    addView(headline)
                    addView(body)
                    addView(advertiser)
                    addView(cta)
                }

                adView.apply {
                    addView(root)
                    mediaView = media
                    headlineView = headline
                    bodyView = body
                    advertiserView = advertiser
                    callToActionView = cta
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

                adView.mediaView?.mediaContent = nativeAd.mediaContent

                (adView.callToActionView as? Button)?.text = nativeAd.callToAction

                    adView.setNativeAd(nativeAd)
                }
            )
        }
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

    val ctaBackground = MaterialTheme.colorScheme.primary.toArgb()
    val ctaTextColor = MaterialTheme.colorScheme.onPrimary.toArgb()

    Card(
        modifier = modifier.alpha(fade.value),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(3.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            AdBadge(modifier = Modifier.padding(start = 10.dp, top = 10.dp, end = 10.dp, bottom = 6.dp))
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
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
                    includeFontPadding = false
                }

                val body = TextView(context).apply {
                    textSize = 13f
                    setTextColor(bodyColor)       // 🎨 Adaptive
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    includeFontPadding = false
                }

                val advertiser = TextView(context).apply {
                    textSize = 12f
                    setTypeface(null, Typeface.ITALIC)
                    setTextColor(advertiserColor) // 🎨 Adaptive
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    includeFontPadding = false
                }

                val cta = Button(context).apply {
                    stylePrimaryCta(context, ctaBackground, ctaTextColor, radiusDp = 12f, horizontalDp = 12, verticalDp = 8)
                    textSize = 13f
                }

                textColumn.addView(headline)
                textColumn.addView(body)
                textColumn.addView(advertiser)
                textColumn.addView(cta)

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

                adView.apply {
                    addView(root)
                    headlineView = headline
                    bodyView = body
                    advertiserView = advertiser
                    callToActionView = cta
                    this.iconView = iconView
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
}




/*-----------------------------------------------------------
   HELPERS
-----------------------------------------------------------*/
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

private fun Button.stylePrimaryCta(
    context: Context,
    bgColor: Int,
    textColor: Int,
    radiusDp: Float,
    horizontalDp: Int = 16,
    verticalDp: Int = 12
) {
    isAllCaps = false
    typeface = Typeface.DEFAULT_BOLD
    backgroundTintList = ColorStateList.valueOf(bgColor)
    setTextColor(textColor)
    elevation = 0f
    stateListAnimator = null
    clipToOutline = true
    outlineProvider = roundedOutline(radiusDp, context)
    setPadding(
        horizontalDp.toPx(context),
        verticalDp.toPx(context),
        horizontalDp.toPx(context),
        verticalDp.toPx(context)
    )
}
