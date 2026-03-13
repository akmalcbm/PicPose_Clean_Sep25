/**
 * ---
 * File: AdsConfig.kt
 * Layer: Data
 * Project: PicPose
 *
 * Purpose:
 * Defines DTOs and domain-facing models that represent prompt, user, stats, or settings data.
 *
 * Interactions:
 * Works with nearby classes in the same layer to keep responsibilities separated and easier to maintain.
 *
 * Data Flow:
 * Feature-specific flow; see adjacent ViewModels and repositories for the full path.
 *
 * Maintainer Notes:
 * - Keep responsibilities narrow and update this header when the file grows into a larger abstraction.
 * ---
 */

package com.picpose.bestphotographyapp.data.models

/** Domain model consumed by ads policy/manager layers. */
data class AdsConfig(
    val global: GlobalConfig = GlobalConfig(),
    val placements: List<PlacementConfig> = emptyList()
) {
    fun findPlacement(key: String): PlacementConfig? = placements.firstOrNull { it.key == key }
}

data class GlobalConfig(
    val adsEnabled: Boolean = true,
    val environment: String = "test", // test | production
    val cmpRequired: Boolean = false,
    val defaultFrequencyPerHour: Int = 2,
    val useTestAds: Boolean = true,
    val configUpdatedAt: String? = null,
    val admobAppId: String? = null,
    val admobAppIdTest: String? = null,
    val admobAppIdLive: String? = null,
    val interstitialCooldownSeconds: Int = 60,
    val interstitialShowEveryNActions: Int = 3
)

data class PlacementConfig(
    val key: String = "",
    val adType: String = "", // banner | native | interstitial | rewarded
    val enabled: Boolean = false,
    val refreshSeconds: Int? = null,
    val frequency: Int? = null,
    val autoDisabled: Boolean = false,
    val units: List<UnitConfig> = emptyList()
)

data class UnitConfig(
    val adUnitId: String = "",
    val priority: Int = Int.MAX_VALUE,
    val isTest: Boolean = false,
    val isLive: Boolean = true,
    val network: String = "admob",
    val sdkRequired: Boolean = true
)
