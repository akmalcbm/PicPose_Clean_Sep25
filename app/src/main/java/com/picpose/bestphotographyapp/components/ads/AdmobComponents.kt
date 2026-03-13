/**
 * ---
 * File: AdmobComponents.kt
 * Layer: Presentation (UI)
 * Project: PicPose
 *
 * Purpose:
 * Contains reusable Compose UI building blocks shared across screens.
 *
 * Interactions:
 * Reads immutable state from ViewModels or callbacks and emits user events back up to navigation or state owners.
 *
 * Data Flow:
 * Feature-specific flow; see adjacent ViewModels and repositories for the full path.
 *
 * Maintainer Notes:
 * - Prefer stateless composables and keep side effects inside well-scoped effect APIs.
 * - Document new navigation arguments and remember that recomposition can re-run this code frequently.
 * ---
 */

package com.picpose.bestphotographyapp.components.ads

import android.app.Activity
import android.widget.FrameLayout
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdSize
import com.picpose.bestphotographyapp.R
import com.picpose.bestphotographyapp.components.ads.AdsLog
import com.picpose.bestphotographyapp.components.ads.AdsConfigState
import com.picpose.bestphotographyapp.components.ads.AdsManager
import com.picpose.bestphotographyapp.components.ads.BannerAdController
import com.picpose.bestphotographyapp.components.ads.InterstitialAdController
import com.picpose.bestphotographyapp.components.ads.NativeAdController
import com.picpose.bestphotographyapp.components.ads.RewardedAdController
import com.picpose.bestphotographyapp.components.ads.NativeAdSection

enum class AdType {
    BANNER1, BANNER2, INTERSTITIAL1, INTERSTITIAL2,
    NATIVE1, NATIVE2, NATIVE3, REWARDED1
}

@Composable
fun AdmobBannerAd(
    modifier: Modifier = Modifier,
    placementKey: String = AdsManager.KEY_HOME_BANNER,
    adType: AdType? = null
) {
    val configState by AdsManager.configState.collectAsState()
    val context = LocalContext.current
    val resolvedPlacement = remember(placementKey, adType) {
        adType?.toPlacementKey() ?: placementKey
    }
    val canShowAds = AdsManager.canShowAds()
    LaunchedEffect(resolvedPlacement, canShowAds) {
        AdsLog.i(
            AdsLog.TAG_UI,
            "[AdsUI] component=AdmobBannerAd placement=$resolvedPlacement canShowAds=$canShowAds action=request"
        )
    }
    if (configState is AdsConfigState.Loading) {
        LaunchedEffect(resolvedPlacement) {
            AdsLog.d(
                AdsLog.TAG_UI,
                "[AdsUI] component=AdmobBannerAd placement=$resolvedPlacement action=wait reason=CONFIG_LOADING"
            )
        }
        return
    }
    if (!canShowAds) {
        LaunchedEffect(resolvedPlacement) {
            AdsLog.i(
                AdsLog.TAG_UI,
                "[AdsUI] component=AdmobBannerAd placement=$resolvedPlacement action=skip reason=global_gate"
            )
        }
        return
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        if (widthPx <= 0f) {
            AdsLog.w(
                AdsLog.TAG_UI,
                "[AdsUI] component=AdmobBannerAd placement=$resolvedPlacement action=skip reason=invalid_width"
            )
            return@BoxWithConstraints
        }

        val adaptiveSize = remember(widthPx) {
            val widthDp = (widthPx / density.density).toInt().coerceAtLeast(1)
            AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp)
        }

        val controller = remember(resolvedPlacement, adaptiveSize) {
            BannerAdController(
                placementKey = resolvedPlacement,
                adSize = adaptiveSize,
                logTag = AdsLog.TAG_BANNER
            )
        }

        DisposableEffect(controller) {
            onDispose { controller.clear() }
        }

        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { FrameLayout(it) },
            update = { container ->
                controller.attach(container, context)
            }
        )
    }
}

@Composable
fun AdmobInterstitialTrigger(
    adType: AdType = AdType.INTERSTITIAL1,
    placementKey: String? = null,
    autoTrigger: Boolean = true
) {
    val configState by AdsManager.configState.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity ?: return

    val resolvedPlacement = remember(placementKey, adType) {
        placementKey ?: adType.toPlacementKey()
    }
    val canShowAds = AdsManager.canShowAds()
    LaunchedEffect(resolvedPlacement, autoTrigger, canShowAds) {
        AdsLog.i(
            AdsLog.TAG_UI,
            "[AdsUI] component=AdmobInterstitialTrigger placement=$resolvedPlacement autoTrigger=$autoTrigger canShowAds=$canShowAds action=request"
        )
    }
    if (configState is AdsConfigState.Loading) {
        LaunchedEffect(resolvedPlacement) {
            AdsLog.d(
                AdsLog.TAG_UI,
                "[AdsUI] component=AdmobInterstitialTrigger placement=$resolvedPlacement action=wait reason=CONFIG_LOADING"
            )
        }
        return
    }
    if (!canShowAds) {
        LaunchedEffect(resolvedPlacement) {
            AdsLog.i(
                AdsLog.TAG_UI,
                "[AdsUI] component=AdmobInterstitialTrigger placement=$resolvedPlacement action=skip reason=global_gate"
            )
        }
        return
    }

    val controller = remember(resolvedPlacement) {
        InterstitialAdController(
            placementKey = resolvedPlacement,
            logTag = AdsLog.TAG_INTER
        )
    }

    DisposableEffect(controller) {
        onDispose { controller.clear() }
    }

    LaunchedEffect(resolvedPlacement) {
        AdsLog.d(AdsLog.TAG_UI, "[AdsUI] component=AdmobInterstitialTrigger placement=$resolvedPlacement action=preload")
        controller.preload(context)
    }

    LaunchedEffect(autoTrigger, resolvedPlacement) {
        if (!autoTrigger) return@LaunchedEffect
        kotlinx.coroutines.delay(1_000)
        AdsLog.d(AdsLog.TAG_UI, "[AdsUI] component=AdmobInterstitialTrigger placement=$resolvedPlacement action=show")
        controller.show(activity)
    }
}

@Composable
fun AdmobNativeAd(
    modifier: Modifier = Modifier,
    placementKey: String = AdsManager.KEY_NATIVE_AD,
    adType: AdType? = null
) {
    val configState by AdsManager.configState.collectAsState()
    val resolvedPlacement = remember(placementKey, adType) {
        adType?.toPlacementKey() ?: placementKey
    }
    val canShowAds = AdsManager.canShowAds()
    LaunchedEffect(resolvedPlacement, canShowAds) {
        AdsLog.i(
            AdsLog.TAG_UI,
            "[AdsUI] component=AdmobNativeAd placement=$resolvedPlacement canShowAds=$canShowAds action=request"
        )
    }
    if (configState is AdsConfigState.Loading) {
        LaunchedEffect(resolvedPlacement) {
            AdsLog.d(
                AdsLog.TAG_UI,
                "[AdsUI] component=AdmobNativeAd placement=$resolvedPlacement action=wait reason=CONFIG_LOADING"
            )
        }
        return
    }
    if (!canShowAds) {
        LaunchedEffect(resolvedPlacement) {
            AdsLog.i(
                AdsLog.TAG_UI,
                "[AdsUI] component=AdmobNativeAd placement=$resolvedPlacement action=skip reason=global_gate"
            )
        }
        return
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.sponsored_content),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            NativeAdSection(
                placementKey = resolvedPlacement,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun AdmobRewardedAd(
    placementKey: String = AdsManager.KEY_REWARDED_AD,
    onRewardEarned: (Int) -> Unit = {},
    onAdDismissed: () -> Unit = {}
) {
    val configState by AdsManager.configState.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity ?: return
    val canShowAds = AdsManager.canShowAds()
    LaunchedEffect(placementKey, canShowAds) {
        AdsLog.i(
            AdsLog.TAG_UI,
            "[AdsUI] component=AdmobRewardedAd placement=$placementKey canShowAds=$canShowAds action=request"
        )
    }
    if (configState is AdsConfigState.Loading) {
        LaunchedEffect(placementKey) {
            AdsLog.d(
                AdsLog.TAG_UI,
                "[AdsUI] component=AdmobRewardedAd placement=$placementKey action=wait reason=CONFIG_LOADING"
            )
        }
        return
    }
    if (!canShowAds) {
        LaunchedEffect(placementKey) {
            AdsLog.i(
                AdsLog.TAG_UI,
                "[AdsUI] component=AdmobRewardedAd placement=$placementKey action=skip reason=global_gate"
            )
        }
        return
    }

    val controller = remember(placementKey) {
        RewardedAdController(
            placementKey = placementKey,
            logTag = AdsLog.TAG_REWARD
        )
    }

    var isReady by remember(placementKey) { mutableStateOf(false) }

    DisposableEffect(controller) {
        onDispose {
            isReady = false
            controller.clear()
        }
    }

    LaunchedEffect(placementKey) {
        AdsLog.d(AdsLog.TAG_UI, "[AdsUI] component=AdmobRewardedAd placement=$placementKey action=preload")
        controller.preload(
            context,
            object : RewardedAdController.Callbacks {
                override fun onLoaded() {
                    isReady = true
                    AdsLog.i(AdsLog.TAG_UI, "[AdsUI] component=AdmobRewardedAd placement=$placementKey status=ready")
                }

                override fun onDismissed() {
                    isReady = false
                    AdsLog.d(AdsLog.TAG_UI, "[AdsUI] component=AdmobRewardedAd placement=$placementKey status=dismissed")
                }
            }
        )
    }

    if (isReady) {
        Button(
            onClick = {
                AdsLog.d(AdsLog.TAG_UI, "[AdsUI] component=AdmobRewardedAd placement=$placementKey action=show_click")
                controller.show(
                    activity,
                    object : RewardedAdController.Callbacks {
                        override fun onReward(reward: com.google.android.gms.ads.rewarded.RewardItem) {
                            AdsLog.i(
                                AdsLog.TAG_UI,
                                "[AdsUI] component=AdmobRewardedAd placement=$placementKey action=reward amount=${reward.amount} type=${reward.type}"
                            )
                            onRewardEarned(reward.amount)
                        }

                        override fun onDismissed() {
                            AdsLog.d(AdsLog.TAG_UI, "[AdsUI] component=AdmobRewardedAd placement=$placementKey action=dismiss")
                            onAdDismissed()
                            isReady = false
                            controller.preload(context)
                        }
                    }
                )
            }
        ) {
            Text(stringResource(R.string.watch_ad_for_reward))
        }
    }
}

private fun AdType.toPlacementKey(): String {
    return when (this) {
        AdType.BANNER1 -> AdsManager.KEY_HOME_BANNER
        AdType.BANNER2 -> AdsManager.KEY_HOME_BANNER
        AdType.INTERSTITIAL1 -> AdsManager.KEY_HOME_INTERSTITIAL
        AdType.INTERSTITIAL2 -> AdsManager.KEY_DETAIL_INTERSTITIAL
        AdType.NATIVE1 -> AdsManager.KEY_NATIVE_AD
        AdType.NATIVE2 -> AdsManager.KEY_NATIVE_AD
        AdType.NATIVE3 -> AdsManager.KEY_NATIVE_AD
        AdType.REWARDED1 -> AdsManager.KEY_REWARDED_AD
    }
}
