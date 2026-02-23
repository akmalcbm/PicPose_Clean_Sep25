package com.picpose.bestphotographyapp.presentation.ads

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Outline
import android.graphics.Typeface
import android.text.TextUtils
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.nativead.AdChoicesView
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.picpose.bestphotographyapp.R

enum class NativeAdStyle { Compact, LargeMedia }

private enum class NativeCardVariant {
    PREMIUM,
    COMPACT
}

private data class NativeViewRefs(
    val mediaView: MediaView,
    val headlineView: TextView,
    val bodyView: TextView,
    val advertiserView: TextView,
    val ctaView: Button,
    val iconView: ImageView,
    val adChoicesView: AdChoicesView
)

@Composable
fun AdBadge(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .wrapContentHeight()
            .heightIn(min = 18.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                shape = RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        Text(
            text = androidx.compose.ui.res.stringResource(R.string.sponsored),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip
        )
    }
}

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

@Composable
fun LargeNativeAdCard(
    nativeAd: NativeAd?,
    modifier: Modifier = Modifier
) {
    if (nativeAd == null) {
        NativeAdShimmer(modifier.height(220.dp), corner = 12)
        return
    }

    val fade = remember { Animatable(0f) }
    LaunchedEffect(nativeAd) { fade.animateTo(1f, tween(260)) }

    val ctaBgColor = MaterialTheme.colorScheme.primary.toArgb()
    val ctaTextColor = MaterialTheme.colorScheme.onPrimary.toArgb()
    val headlineColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val bodyColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val advertiserColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()

    Card(
        modifier = modifier
            .alpha(fade.value)
            .fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(3.dp)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { context ->
                createNativeAdView(
                    context = context,
                    variant = NativeCardVariant.PREMIUM,
                    headlineColor = headlineColor,
                    bodyColor = bodyColor,
                    advertiserColor = advertiserColor,
                    ctaBgColor = ctaBgColor,
                    ctaTextColor = ctaTextColor
                )
            },
            update = { adView -> bindNativeAdToView(adView, nativeAd) }
        )
    }
}

@Composable
fun LargeNativeAdCardForGrid(
    nativeAd: NativeAd?,
    modifier: Modifier = Modifier
) {
    if (nativeAd == null) {
        NativeAdShimmer(modifier.height(170.dp), corner = 14)
        return
    }

    val fade = remember { Animatable(0f) }
    LaunchedEffect(nativeAd) { fade.animateTo(1f, tween(260)) }

    val ctaBgColor = MaterialTheme.colorScheme.primary.toArgb()
    val ctaTextColor = MaterialTheme.colorScheme.onPrimary.toArgb()
    val headlineColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val bodyColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val advertiserColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()

    Card(
        modifier = modifier
            .alpha(fade.value)
            .fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { context ->
                createNativeAdView(
                    context = context,
                    variant = NativeCardVariant.COMPACT,
                    headlineColor = headlineColor,
                    bodyColor = bodyColor,
                    advertiserColor = advertiserColor,
                    ctaBgColor = ctaBgColor,
                    ctaTextColor = ctaTextColor
                )
            },
            update = { adView -> bindNativeAdToView(adView, nativeAd) }
        )
    }
}

@Composable
fun InlineNativeAdCard(
    nativeAd: NativeAd?,
    modifier: Modifier = Modifier
) {
    LargeNativeAdCardForGrid(nativeAd = nativeAd, modifier = modifier)
}

private fun createNativeAdView(
    context: Context,
    variant: NativeCardVariant,
    headlineColor: Int,
    bodyColor: Int,
    advertiserColor: Int,
    ctaBgColor: Int,
    ctaTextColor: Int
): NativeAdView {
    val adView = NativeAdView(context)

    val root = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
    }

    val mediaContainer = FrameLayout(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            if (variant == NativeCardVariant.PREMIUM) 210.toPx(context) else 220.toPx(context)
        )
        setBackgroundResource(R.drawable.native_ad_media_top_bg)
        clipChildren = true
        clipToPadding = true
        clipToOutline = true
        outlineProvider = roundedTopOutline(if (variant == NativeCardVariant.PREMIUM) 16f else 14f, context)
    }

    val mediaView = MediaView(context).apply {
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
    }

    val adBadge = TextView(context).apply {
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.START or Gravity.TOP
        ).apply {
            setMargins(if (variant == NativeCardVariant.PREMIUM) 1.toPx(context) else 2.toPx(context), if (variant == NativeCardVariant.PREMIUM) 1.toPx(context) else 2.toPx(context), 2, 2)
        }
        setBackgroundResource(R.drawable.native_ad_badge_bg)
        setPadding(4.toPx(context), 3.toPx(context), 4.toPx(context), 3.toPx(context))
        text = context.getString(R.string.sponsored)
        textSize = if (variant == NativeCardVariant.PREMIUM) 13f else 11f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(0xFF232531.toInt())
    }

    val adChoices = AdChoicesView(context).apply {
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.END or Gravity.TOP
        ).apply {
            setMargins(
                0,
                if (variant == NativeCardVariant.PREMIUM) 14.toPx(context) else 10.toPx(context),
                if (variant == NativeCardVariant.PREMIUM) 14.toPx(context) else 10.toPx(context),
                0
            )
        }
        setPadding(12.toPx(context), 8.toPx(context), 12.toPx(context), 8.toPx(context))
    }

    mediaContainer.apply {
        addView(mediaView)
        addView(adBadge)
        addView(adChoices)
    }

    if (variant == NativeCardVariant.PREMIUM) {
        val infoRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(14.toPx(context), 12.toPx(context), 14.toPx(context), 8.toPx(context))
        }

        val appIcon = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(44.toPx(context), 44.toPx(context))
            setBackgroundResource(R.drawable.native_ad_icon_bg)
            contentDescription = context.getString(R.string.app_logo)
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            outlineProvider = roundedOutline(10f, context)
            visibility = View.GONE
        }

        val textColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = 10.toPx(context)
            }
        }

        val headline = TextView(context).apply {
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(headlineColor)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }

        val body = TextView(context).apply {
            textSize = 13f
            setTextColor(bodyColor)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            visibility = View.GONE
        }

        val advertiser = TextView(context).apply {
            textSize = 12f
            setTypeface(typeface, Typeface.ITALIC)
            setTextColor(advertiserColor)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            visibility = View.GONE
        }

        val cta = Button(context).apply {
            stylePrimaryCta(context, ctaBgColor, ctaTextColor, radiusDp = 10f)
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                48.toPx(context)
            ).apply {
                setMargins(14.toPx(context), 8.toPx(context), 14.toPx(context), 14.toPx(context))
            }
            visibility = View.GONE
        }

        textColumn.apply {
            addView(headline)
            addView(body)
            addView(advertiser)
        }

        infoRow.apply {
            addView(appIcon)
            addView(textColumn)
        }

        root.apply {
            addView(mediaContainer)
            addView(infoRow)
            addView(cta)
        }

        attachViews(adView, NativeViewRefs(mediaView, headline, body, advertiser, cta, appIcon, adChoices))
    } else {
        val contentRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(10.toPx(context), 10.toPx(context), 10.toPx(context), 6.toPx(context))
        }

        val appIcon = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(36.toPx(context), 36.toPx(context))
            setBackgroundResource(R.drawable.native_ad_icon_bg)
            contentDescription = context.getString(R.string.app_logo)
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            outlineProvider = roundedOutline(10f, context)
            visibility = View.GONE
        }

        val textColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = 8.toPx(context)
            }
        }

        val headline = TextView(context).apply {
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(headlineColor)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }

        val body = TextView(context).apply {
            textSize = 12f
            setTextColor(bodyColor)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            visibility = View.GONE
        }

        val advertiser = TextView(context).apply {
            textSize = 11f
            setTypeface(typeface, Typeface.ITALIC)
            setTextColor(advertiserColor)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            visibility = View.GONE
        }

        val cta = Button(context).apply {
            stylePrimaryCta(context, ctaBgColor, ctaTextColor, radiusDp = 10f, horizontalDp = 14, verticalDp = 8)
            textSize = 14f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                36.toPx(context)
            ).apply { setMargins(10.toPx(context), 0, 10.toPx(context), 10.toPx(context)) }
            visibility = View.GONE
        }

        textColumn.apply {
            addView(headline)
            addView(body)
            addView(advertiser)
        }

        contentRow.apply {
            addView(appIcon)
            addView(textColumn)
        }

        root.apply {
            addView(mediaContainer)
            addView(contentRow)
            addView(cta)
        }

        attachViews(adView, NativeViewRefs(mediaView, headline, body, advertiser, cta, appIcon, adChoices))
    }

    adView.addView(root)
    return adView
}

private fun attachViews(adView: NativeAdView, refs: NativeViewRefs) {
    adView.mediaView = refs.mediaView
    adView.headlineView = refs.headlineView
    adView.bodyView = refs.bodyView
    adView.advertiserView = refs.advertiserView
    adView.callToActionView = refs.ctaView
    adView.iconView = refs.iconView
    adView.adChoicesView = refs.adChoicesView
}

private fun bindNativeAdToView(adView: NativeAdView, nativeAd: NativeAd) {
    (adView.headlineView as? TextView)?.text = nativeAd.headline

    (adView.bodyView as? TextView)?.let { bodyView ->
        val subtitleText = nativeAd.body?.takeIf { it.isNotBlank() }
            ?: nativeAd.advertiser?.takeIf { it.isNotBlank() }
        if (subtitleText.isNullOrBlank()) {
            bodyView.visibility = View.GONE
        } else {
            bodyView.visibility = View.VISIBLE
            bodyView.text = subtitleText
        }
    }

    (adView.advertiserView as? TextView)?.let { advertiserView ->
        val advertiserText = nativeAd.advertiser
        if (advertiserText.isNullOrBlank()) {
            advertiserView.visibility = View.GONE
        } else {
            advertiserView.visibility = View.VISIBLE
            advertiserView.text = advertiserText
        }
    }

    (adView.iconView as? ImageView)?.let { iconView ->
        val iconDrawable = nativeAd.icon?.drawable
        if (iconDrawable == null) {
            iconView.visibility = View.GONE
        } else {
            iconView.visibility = View.VISIBLE
            iconView.setImageDrawable(iconDrawable)
        }
    }

    (adView.callToActionView as? Button)?.let { ctaButton ->
        val ctaText = nativeAd.callToAction
        if (ctaText.isNullOrBlank()) {
            ctaButton.visibility = View.GONE
        } else {
            ctaButton.visibility = View.VISIBLE
            ctaButton.text = ctaText
        }
    }

    adView.mediaView?.mediaContent = nativeAd.mediaContent
    adView.setNativeAd(nativeAd)
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

private fun roundedTopOutline(radiusDp: Float, context: Context) =
    object : ViewOutlineProvider() {
        override fun getOutline(v: View, outline: Outline) {
            val r = radiusDp * context.resources.displayMetrics.density
            outline.setRoundRect(0, 0, v.width, v.height + r.toInt(), r)
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
    isAllCaps = true
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
