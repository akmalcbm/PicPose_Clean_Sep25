package com.picpose.bestphotographyapp.data.remote

data class AdsConfigResponse(
    val success: Boolean,
    val config_version: Int,
    val global: GlobalAdsConfig,
    val placements: Map<String, AdPlacementConfig>
)

data class GlobalAdsConfig(
    val ads_enabled: Boolean,
    val environment: String, // test | production
    val cmp_required: Boolean,
    val default_frequency_per_hour: Int
)

data class AdPlacementConfig(
    val ad_type: String,              // banner | native | interstitial | rewarded
    val enabled: Boolean,
    val refresh_seconds: Int?,
    val frequency: Int?,
    val units: List<AdUnitConfig>
)

data class AdUnitConfig(
    val network: String,              // admob | meta
    val ad_unit_id: String,
    val priority: Int,
    val is_test: Boolean
)
