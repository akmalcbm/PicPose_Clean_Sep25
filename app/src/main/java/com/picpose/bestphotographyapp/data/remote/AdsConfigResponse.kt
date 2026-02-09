package com.picpose.bestphotographyapp.data.remote

import com.google.gson.annotations.SerializedName
import com.picpose.bestphotographyapp.core.constants.Constants
import com.picpose.bestphotographyapp.data.models.AdsConfig
import com.picpose.bestphotographyapp.data.models.GlobalConfig
import com.picpose.bestphotographyapp.data.models.PlacementConfig
import com.picpose.bestphotographyapp.data.models.UnitConfig
import com.picpose.bestphotographyapp.presentation.ads.AdsLog

/**
 * Backend JSON envelope:
 * {
 *   "success": true,
 *   "data": {
 *     "global": {...},
 *     "placements": [ ... ]
 *   }
 * }
 *
 * Notes for Gson safety:
 * - Fields are nullable in DTO because Gson can bypass Kotlin defaults.
 * - Mapper below applies hard defaults and sanitization.
 */
data class AdsConfigResponse(
    @SerializedName("success")
    val success: Boolean? = false,
    @SerializedName("data")
    val data: AdsConfigDataResponse? = AdsConfigDataResponse()
)

data class AdsConfigDataResponse(
    @SerializedName("global")
    val global: GlobalAdsConfigResponse? = GlobalAdsConfigResponse(),
    @SerializedName("placements")
    val placements: List<AdPlacementResponse>? = emptyList()
)

data class GlobalAdsConfigResponse(
    @SerializedName("ads_enabled")
    val adsEnabled: Boolean? = true,
    @SerializedName("environment")
    val environment: String? = "test",
    @SerializedName("cmp_required")
    val cmpRequired: Boolean? = false,
    @SerializedName("default_frequency_per_hour")
    val defaultFrequencyPerHour: Int? = 2,
    @SerializedName("use_test_ads")
    val useTestAds: Boolean? = true,
    @SerializedName("config_updated_at")
    val configUpdatedAt: String? = null
)

data class AdPlacementResponse(
    @SerializedName("key")
    val key: String? = "",
    @SerializedName("ad_type")
    val adType: String? = "",
    @SerializedName("enabled")
    val enabled: Boolean? = false,
    @SerializedName("refresh_seconds")
    val refreshSeconds: Int? = null,
    @SerializedName("frequency")
    val frequency: Int? = null,
    @SerializedName("auto_disabled")
    val autoDisabled: Boolean? = false,
    @SerializedName("units")
    val units: List<AdUnitResponse>? = emptyList()
)

data class AdUnitResponse(
    @SerializedName("ad_unit_id")
    val adUnitId: String? = "",
    @SerializedName("priority")
    val priority: Int? = Int.MAX_VALUE,
    @SerializedName("is_test")
    val isTest: Boolean? = false,
    @SerializedName("is_live")
    val isLive: Boolean? = true,
    @SerializedName("network")
    val network: String? = "admob",
    @SerializedName("sdk_required")
    val sdkRequired: Boolean? = true
)

fun AdsConfigResponse.toDomainOrNull(): AdsConfig? {
    if (success != true) {
        AdsLog.w(AdsLog.TAG_REPO, "[AdsRepo] parse status=FAIL reason=success_false_or_null")
        return null
    }

    val globalNode = data?.global ?: GlobalAdsConfigResponse()
    val global = GlobalConfig(
        adsEnabled = globalNode.adsEnabled ?: true,
        environment = globalNode.environment?.trim().orEmpty().ifEmpty { "test" },
        cmpRequired = globalNode.cmpRequired ?: false,
        defaultFrequencyPerHour = (globalNode.defaultFrequencyPerHour ?: 2).coerceAtLeast(1),
        useTestAds = globalNode.useTestAds ?: true,
        configUpdatedAt = globalNode.configUpdatedAt
    )

    val placements = data?.placements.orEmpty().mapNotNull { placement ->
        val key = placement.key?.trim().orEmpty()
        val type = placement.adType?.trim().orEmpty()
        if (key.isBlank() || type.isBlank()) {
            AdsLog.w(AdsLog.TAG_REPO, "[AdsRepo] parse placement=SKIP reason=missing_key_or_type")
            return@mapNotNull null
        }

        val mappedUnits = placement.units.orEmpty().mapNotNull { unit ->
            val adUnitId = unit.adUnitId?.trim().orEmpty()
            if (adUnitId.isBlank()) {
                AdsLog.w(AdsLog.TAG_REPO, "[AdsRepo] parse placement=$key unit=SKIP reason=missing_ad_unit_id")
                return@mapNotNull null
            }

            UnitConfig(
                adUnitId = adUnitId,
                priority = unit.priority ?: Int.MAX_VALUE,
                isTest = unit.isTest ?: false,
                isLive = unit.isLive ?: true,
                network = unit.network?.trim().orEmpty().ifEmpty { "admob" },
                sdkRequired = unit.sdkRequired ?: true
            )
        }.sortedBy { it.priority }

        PlacementConfig(
            key = key,
            adType = type,
            enabled = placement.enabled ?: false,
            refreshSeconds = placement.refreshSeconds,
            frequency = placement.frequency,
            autoDisabled = placement.autoDisabled ?: false,
            units = mappedUnits
        )
    }

    AdsLog.i(
        AdsLog.TAG_REPO,
        "[AdsRepo] parse status=OK placements=${placements.size} env=${global.environment} adsEnabled=${global.adsEnabled} useTest=${global.useTestAds} cmpRequired=${global.cmpRequired}"
    )
    return AdsConfig(global = global, placements = placements)
}

/** Compatibility aliases to avoid broad refactors in current codebase. */
typealias EffectiveAdsConfig = AdsConfig
typealias EffectiveGlobalAdsConfig = GlobalConfig
typealias EffectiveAdPlacement = PlacementConfig
typealias EffectiveAdUnit = UnitConfig

fun AdsConfigResponse.toEffectiveOrNull(): EffectiveAdsConfig? = toDomainOrNull()

fun localFallbackAdsConfig(): AdsConfig {
    return AdsConfig(
        global = GlobalConfig(
            adsEnabled = true,
            environment = "test",
            cmpRequired = false,
            defaultFrequencyPerHour = 2,
            useTestAds = true,
            configUpdatedAt = null
        ),
        placements = listOf(
            PlacementConfig(
                key = "banner_1",
                adType = "banner",
                enabled = true,
                refreshSeconds = null,
                frequency = null,
                autoDisabled = false,
                units = listOf(UnitConfig(adUnitId = Constants.TEST_BANNER_ID, priority = 1, isTest = true))
            ),
            PlacementConfig(
                key = "banner_2",
                adType = "banner",
                enabled = true,
                refreshSeconds = null,
                frequency = null,
                autoDisabled = false,
                units = listOf(UnitConfig(adUnitId = Constants.TEST_BANNER_ID_2, priority = 1, isTest = true))
            ),
            PlacementConfig(
                key = "interstitial_1",
                adType = "interstitial",
                enabled = true,
                refreshSeconds = null,
                frequency = null,
                autoDisabled = false,
                units = listOf(UnitConfig(adUnitId = Constants.TEST_INTERSTITIAL_ID, priority = 1, isTest = true))
            ),
            PlacementConfig(
                key = "interstitial_2",
                adType = "interstitial",
                enabled = true,
                refreshSeconds = null,
                frequency = null,
                autoDisabled = false,
                units = listOf(UnitConfig(adUnitId = Constants.TEST_INTERSTITIAL_ID_2, priority = 1, isTest = true))
            ),
            PlacementConfig(
                key = "native_1",
                adType = "native",
                enabled = true,
                refreshSeconds = null,
                frequency = null,
                autoDisabled = false,
                units = listOf(UnitConfig(adUnitId = Constants.TEST_NATIVE_ID, priority = 1, isTest = true))
            ),
            PlacementConfig(
                key = "native_2",
                adType = "native",
                enabled = true,
                refreshSeconds = null,
                frequency = null,
                autoDisabled = false,
                units = listOf(UnitConfig(adUnitId = Constants.TEST_NATIVE_ID_2, priority = 1, isTest = true))
            ),
            PlacementConfig(
                key = "native_3",
                adType = "native",
                enabled = true,
                refreshSeconds = null,
                frequency = null,
                autoDisabled = false,
                units = listOf(UnitConfig(adUnitId = Constants.TEST_NATIVE_ID_3, priority = 1, isTest = true))
            ),
            PlacementConfig(
                key = "rewarded_1",
                adType = "rewarded",
                enabled = true,
                refreshSeconds = null,
                frequency = null,
                autoDisabled = false,
                units = listOf(UnitConfig(adUnitId = Constants.TEST_REWARDED_ID, priority = 1, isTest = true))
            )
        )
    )
}
