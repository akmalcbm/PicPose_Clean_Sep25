# AdMob Server Configuration

This document explains the new AdMob server configuration system implemented in the PicPose app.

## Overview

The app now fetches all AdMob configuration settings from the server endpoint `https://picpose.iamakmal.in/api/get_app_settings.php`, allowing for centralized ad management without requiring app updates.

## Features

- **Server-side configuration**: All AdMob IDs are fetched from the server
- **Local caching**: Settings are cached for 24 hours to reduce server requests
- **Fallback mechanism**: Uses Google's test ad IDs if server fetch fails
- **Multiple ad types**: Supports banners, interstitials, native ads, and rewarded ads

## Ad Unit Types

The system supports the following ad unit types:

- **App ID**: Main AdMob app identifier
- **Banner Ads**: `banner1_id`, `banner2_id`
- **Interstitial Ads**: `interstitial1_id`, `interstitial2_id`
- **Native Ads**: `native1_id`, `native2_id`, `native3_id`
- **Rewarded Ads**: `rewarded1_id`

## Server Response Format

The expected JSON response format from the server is:

```json
{
  "success": true,
  "message": "Settings retrieved successfully",
  "data": {
    "app_id": "ca-app-pub-XXXXXXXXXX",
    "banner1_id": "ca-app-pub-XXXXXXXXXX/XXXXXXXXXX",
    "banner2_id": "ca-app-pub-XXXXXXXXXX/XXXXXXXXXX",
    "interstitial1_id": "ca-app-pub-XXXXXXXXXX/XXXXXXXXXX",
    "interstitial2_id": "ca-app-pub-XXXXXXXXXX/XXXXXXXXXX",
    "native1_id": "ca-app-pub-XXXXXXXXXX/XXXXXXXXXX",
    "native2_id": "ca-app-pub-XXXXXXXXXX/XXXXXXXXXX",
    "native3_id": "ca-app-pub-XXXXXXXXXX/XXXXXXXXXX",
    "rewarded1_id": "ca-app-pub-XXXXXXXXXX/XXXXXXXXXX"
  }
}
```

## Usage in Composables

### Banner Ads

```kotlin
@Composable
fun MyScreen() {
    // Use the first banner ad type
    AdmobBannerAd(
        modifier = Modifier.fillMaxWidth(),
        adType = AdType.BANNER1
    )
    
    // Use the second banner ad type
    AdmobBannerAd(
        modifier = Modifier.fillMaxWidth(),
        adType = AdType.BANNER2
    )
}
```

### Interstitial Ads

```kotlin
@Composable
fun MyScreen() {
    // Automatically loads and shows interstitial ad after 30 seconds
    AdmobInterstitialTrigger(adType = AdType.INTERSTITIAL1)
}
```

### Native Ads

```kotlin
@Composable
fun MyScreen() {
    AdmobNativeAd(
        modifier = Modifier.fillMaxWidth(),
        adType = AdType.NATIVE1
    )
}
```

### Rewarded Ads

```kotlin
@Composable
fun MyScreen() {
    AdmobRewardedAd(
        onRewardEarned = { amount ->
            // Handle reward (e.g., give user coins, unlock content)
            println("User earned $amount coins!")
        },
        onAdDismissed = {
            // Handle ad dismissal
            println("Rewarded ad dismissed")
        }
    )
}
```

## Manual Configuration Access

If you need to access ad unit IDs programmatically:

```kotlin
val adMobConfig = AdMobConfigManager.getInstance(context)

// Get specific ad unit IDs
val bannerId = adMobConfig.getBanner1Id()
val interstitialId = adMobConfig.getInterstitial1Id()
val nativeId = adMobConfig.getNative1Id()
val rewardedId = adMobConfig.getRewarded1Id()
val appId = adMobConfig.getAppId()
```

## Fallback Test IDs

If the server is unavailable or returns invalid data, the system automatically falls back to Google's official test ad unit IDs:

- App ID: `ca-app-pub-3940256099942544~3347511713`
- Banner: `ca-app-pub-3940256099942544/6300978111`
- Interstitial: `ca-app-pub-3940256099942544/1033173712`
- Native: `ca-app-pub-3940256099942544/2247696110`
- Rewarded: `ca-app-pub-3940256099942544/5224354917`

## Caching Behavior

- Settings are cached locally for 24 hours
- The app fetches fresh settings on startup in the background
- If the server is unavailable, cached settings are used
- If no cache exists and server fails, fallback test IDs are used

## Implementation Details

### Files Added/Modified

1. **AppSettings.kt**: Data models for server response
2. **AdMobConfigManager.kt**: Main configuration manager with caching
3. **ApiService.kt**: Added `getAppSettings()` endpoint
4. **HomeRepository.kt**: Added repository method for fetching settings
5. **AdmobComponents.kt**: Updated to use server-provided IDs
6. **PicPoseApplication.kt**: Initialize config manager on app startup

### Key Classes

- `AdMobConfigManager`: Singleton that manages all AdMob configuration
- `AppSettings`: Data class representing server response
- `AdType`: Enum defining different ad unit types

This system ensures that AdMob settings can be updated server-side without requiring app store releases, while maintaining reliability through fallback mechanisms.