package com.picpose.bestphotographyapp.presentation.components.ads

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.picpose.bestphotographyapp.R
import com.picpose.bestphotographyapp.data.admob.AdMobConfigManager
import com.picpose.bestphotographyapp.presentation.ads.AdsManager

@Composable
fun AdmobBannerAd(
    modifier: Modifier = Modifier,
    adType: AdType = AdType.BANNER1
) {
    val context = LocalContext.current
    val adMobConfig = remember { AdMobConfigManager.getInstance(context) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            AdView(ctx).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = when (adType) {
                    AdType.BANNER1 -> adMobConfig.bannerId()
                    AdType.BANNER2 -> adMobConfig.bannerId2()
                    AdType.INTERSTITIAL1 -> adMobConfig.interstitialId()
                    AdType.INTERSTITIAL2 -> adMobConfig.interstitialId2()
                    AdType.NATIVE1 -> adMobConfig.nativeId()
                    AdType.NATIVE2 -> adMobConfig.nativeId2()
                    AdType.NATIVE3 -> adMobConfig.nativeId3()
                    AdType.REWARDED1 -> adMobConfig.rewardedId()
                }
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}

enum class AdType {
    BANNER1, BANNER2, INTERSTITIAL1, INTERSTITIAL2, 
    NATIVE1, NATIVE2, NATIVE3, REWARDED1
}

@Composable
fun AdmobInterstitialTrigger(
    adType: AdType = AdType.INTERSTITIAL1
) {
    val context = LocalContext.current
    val adMobConfig = remember { AdMobConfigManager.getInstance(context) }
    var interstitialAd by remember { mutableStateOf<InterstitialAd?>(null) }

    LaunchedEffect(Unit) {
        val adRequest = AdRequest.Builder().build()
        val adUnitId = when (adType) {
            AdType.INTERSTITIAL1 -> AdsManager.interstitialId()
            AdType.INTERSTITIAL2 -> AdsManager.interstitialId2()
            else -> AdsManager.interstitialId()
        }

        InterstitialAd.load(
            context,
            adUnitId,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    interstitialAd = null
                }
            }
        )
    }

    // Trigger ad after some user interactions
    LaunchedEffect(interstitialAd) {
        interstitialAd?.let { ad ->
            // Show ad after delay or user action
            kotlinx.coroutines.delay(1000) // after 1 seconds
            if (context is androidx.activity.ComponentActivity) {
                ad.show(context)
            }
        }
    }
}

@Composable
fun AdmobNativeAd(
    modifier: Modifier = Modifier,
    adType: AdType = AdType.NATIVE1
) {
    val context = LocalContext.current
    val adMobConfig = remember { AdMobConfigManager.getInstance(context) }
    
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

            // Native ad content would go here
            // This requires more complex setup with NativeAd
            // Ad unit ID is available from: 
            // when (adType) {
            //     AdType.NATIVE1 -> adMobConfig.getNative1Id()
            //     AdType.NATIVE2 -> adMobConfig.getNative2Id()
            //     AdType.NATIVE3 -> adMobConfig.getNative3Id()
            //     else -> adMobConfig.getNative1Id()
            // }
        }
    }
}

@Composable
fun AdmobRewardedAd(
    onRewardEarned: (Int) -> Unit = {},
    onAdDismissed: () -> Unit = {}
) {
    val context = LocalContext.current
    val adMobConfig = remember { AdMobConfigManager.getInstance(context) }
    var rewardedAd by remember { mutableStateOf<RewardedAd?>(null) }
    var isAdLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val adRequest = AdRequest.Builder().build()

        RewardedAd.load(
            context,
            AdsManager.rewardedId(),
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    isAdLoaded = true
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    rewardedAd = null
                    isAdLoaded = false
                }
            }
        )
    }

    // You can expose this as a Button or trigger it programmatically
    if (isAdLoaded) {
        Button(
            onClick = {
                if (context is androidx.activity.ComponentActivity) {
                    rewardedAd?.show(context) { rewardItem ->
                        onRewardEarned(rewardItem.amount)
                    }
                }
                onAdDismissed()
                isAdLoaded = false
            }
        ) {
            Text(stringResource(R.string.watch_ad_for_reward))
        }
    }
}
