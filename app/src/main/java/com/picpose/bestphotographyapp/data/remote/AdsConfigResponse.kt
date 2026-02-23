package com.picpose.bestphotographyapp.data.remote

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
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
 *     "placements": {...} // v2 map
 *     or
 *     "placements": [...] // legacy list
 *   }
 * }
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
    val placements: JsonElement? = null,
    @SerializedName("placements_list")
    val placementsList: List<AdPlacementResponse>? = emptyList(),
    @SerializedName("ads_enabled")
    val adsEnabled: Boolean? = null,
    @SerializedName("env")
    val env: String? = null,
    @SerializedName("use_test_ads")
    val useTestAds: Boolean? = null,
    @SerializedName("admob_app_id")
    val admobAppId: String? = null,
    @SerializedName("admob_app_id_test")
    val admobAppIdTest: String? = null,
    @SerializedName("admob_app_id_live")
    val admobAppIdLive: String? = null,
    @SerializedName("interstitial_cooldown_seconds")
    val interstitialCooldownSeconds: Int? = null,
    @SerializedName("interstitial_show_every_n_actions")
    val interstitialShowEveryNActions: Int? = null
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
    val configUpdatedAt: String? = null,
    @SerializedName("admob_app_id")
    val admobAppId: String? = null,
    @SerializedName("admob_app_id_test")
    val admobAppIdTest: String? = null,
    @SerializedName("admob_app_id_live")
    val admobAppIdLive: String? = null,
    @SerializedName("interstitial_cooldown_seconds")
    val interstitialCooldownSeconds: Int? = null,
    @SerializedName("interstitial_show_every_n_actions")
    val interstitialShowEveryNActions: Int? = null
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
        safeLogW("[AdsRepo] parse status=FAIL reason=success_false_or_null")
        return null
    }

    val dataNode = data ?: AdsConfigDataResponse()
    val globalNode = dataNode.global ?: GlobalAdsConfigResponse()

    val resolvedEnv = dataNode.env?.trim().takeUnless { it.isNullOrBlank() }
        ?: globalNode.environment?.trim().takeUnless { it.isNullOrBlank() }
        ?: "test"

    val global = GlobalConfig(
        adsEnabled = dataNode.adsEnabled ?: (globalNode.adsEnabled ?: true),
        environment = normalizeEnvironment(resolvedEnv),
        cmpRequired = globalNode.cmpRequired ?: false,
        defaultFrequencyPerHour = (globalNode.defaultFrequencyPerHour ?: 2).coerceAtLeast(1),
        useTestAds = dataNode.useTestAds ?: (globalNode.useTestAds ?: true),
        configUpdatedAt = globalNode.configUpdatedAt,
        admobAppId = dataNode.admobAppId ?: globalNode.admobAppId,
        admobAppIdTest = dataNode.admobAppIdTest ?: globalNode.admobAppIdTest,
        admobAppIdLive = dataNode.admobAppIdLive ?: globalNode.admobAppIdLive,
        interstitialCooldownSeconds = (dataNode.interstitialCooldownSeconds
            ?: globalNode.interstitialCooldownSeconds
            ?: 60).coerceAtLeast(0),
        interstitialShowEveryNActions = (dataNode.interstitialShowEveryNActions
            ?: globalNode.interstitialShowEveryNActions
            ?: 3).coerceAtLeast(1)
    )

    val placements = parsePlacements(dataNode)

    safeLogI("[AdsRepo] parse status=OK placements=${placements.size} env=${global.environment} adsEnabled=${global.adsEnabled} useTest=${global.useTestAds} cmpRequired=${global.cmpRequired}")
    return AdsConfig(global = global, placements = placements)
}

private fun parsePlacements(dataNode: AdsConfigDataResponse): List<PlacementConfig> {
    val fromArray = dataNode.placements?.let { placementsNode ->
        if (placementsNode.isJsonArray) {
            parsePlacementArray(placementsNode.asJsonArray)
        } else {
            emptyList()
        }
    }.orEmpty()

    if (fromArray.isNotEmpty()) {
        return fromArray
    }

    val fromLegacyList = dataNode.placementsList.orEmpty().mapNotNull { it.toPlacementOrNull() }
    if (fromLegacyList.isNotEmpty()) {
        return fromLegacyList
    }

    val fromMap = dataNode.placements?.let { placementsNode ->
        if (placementsNode.isJsonObject) {
            parsePlacementMap(placementsNode.asJsonObject)
        } else {
            emptyList()
        }
    }.orEmpty()

    return fromMap
}

private fun parsePlacementArray(array: JsonArray): List<PlacementConfig> {
    return array.mapNotNull { element ->
        if (!element.isJsonObject) return@mapNotNull null
        parsePlacementObject(element.asJsonObject)
    }
}

private fun parsePlacementMap(obj: JsonObject): List<PlacementConfig> {
    return obj.entrySet().mapNotNull { entry ->
        val key = entry.key.trim()
        if (key.isBlank()) return@mapNotNull null
        val value = entry.value
        if (!value.isJsonObject) return@mapNotNull null
        val placement = value.asJsonObject

        val adType = placement.optString("ad_type")
            .ifBlank { inferAdType(key) }

        val selectedUnit = placement.optString("ad_unit_id")
        val testUnit = placement.optString("ad_unit_id_test")
        val liveUnit = placement.optString("ad_unit_id_live")

        val units = mutableListOf<UnitConfig>()
        if (selectedUnit.isNotBlank()) {
            units += UnitConfig(
                adUnitId = selectedUnit,
                priority = 1,
                isTest = selectedUnit == testUnit,
                isLive = selectedUnit == liveUnit,
                network = "admob",
                sdkRequired = true
            )
        }
        if (testUnit.isNotBlank() && testUnit != selectedUnit) {
            units += UnitConfig(testUnit, 2, isTest = true, isLive = false, network = "admob", sdkRequired = true)
        }
        if (liveUnit.isNotBlank() && liveUnit != selectedUnit) {
            units += UnitConfig(liveUnit, 3, isTest = false, isLive = true, network = "admob", sdkRequired = true)
        }

        PlacementConfig(
            key = key,
            adType = adType,
            enabled = placement.optBoolean("enabled", true),
            refreshSeconds = null,
            frequency = null,
            autoDisabled = false,
            units = units
        )
    }
}

private fun parsePlacementObject(obj: JsonObject): PlacementConfig? {
    val key = obj.optString("key")
    val adType = obj.optString("ad_type")
    if (key.isBlank() || adType.isBlank()) {
        safeLogW("[AdsRepo] parse placement=SKIP reason=missing_key_or_type")
        return null
    }

    val mappedUnits = obj.optArray("units").mapNotNull { item ->
        if (!item.isJsonObject) return@mapNotNull null
        val unitObj = item.asJsonObject
        val adUnitId = unitObj.optString("ad_unit_id")
        if (adUnitId.isBlank()) {
            safeLogW("[AdsRepo] parse placement=$key unit=SKIP reason=missing_ad_unit_id")
            return@mapNotNull null
        }
        UnitConfig(
            adUnitId = adUnitId,
            priority = unitObj.optInt("priority", Int.MAX_VALUE),
            isTest = unitObj.optBoolean("is_test", false),
            isLive = unitObj.optBoolean("is_live", true),
            network = unitObj.optString("network").ifBlank { "admob" },
            sdkRequired = unitObj.optBoolean("sdk_required", true)
        )
    }.sortedBy { it.priority }

    return PlacementConfig(
        key = key,
        adType = adType,
        enabled = obj.optBoolean("enabled", false),
        refreshSeconds = obj.optIntOrNull("refresh_seconds"),
        frequency = obj.optIntOrNull("frequency"),
        autoDisabled = obj.optBoolean("auto_disabled", false),
        units = mappedUnits
    )
}

private fun AdPlacementResponse.toPlacementOrNull(): PlacementConfig? {
    val keyValue = key?.trim().orEmpty()
    val typeValue = adType?.trim().orEmpty()
    if (keyValue.isBlank() || typeValue.isBlank()) {
        return null
    }

    val mappedUnits = units.orEmpty().mapNotNull { unit ->
        val adUnitId = unit.adUnitId?.trim().orEmpty()
        if (adUnitId.isBlank()) {
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

    return PlacementConfig(
        key = keyValue,
        adType = typeValue,
        enabled = enabled ?: false,
        refreshSeconds = refreshSeconds,
        frequency = frequency,
        autoDisabled = autoDisabled ?: false,
        units = mappedUnits
    )
}

private fun normalizeEnvironment(value: String): String {
    return when (value.trim().lowercase()) {
        "live", "production", "prod" -> "production"
        else -> "test"
    }
}

private fun inferAdType(placementKey: String): String {
    val key = placementKey.lowercase()
    return when {
        key.contains("reward") -> "rewarded"
        key.contains("interstitial") -> "interstitial"
        key.contains("native") -> "native"
        else -> "banner"
    }
}

private fun JsonObject.optString(name: String): String =
    get(name)?.takeIf { !it.isJsonNull }?.asString?.trim().orEmpty()

private fun JsonObject.optBoolean(name: String, fallback: Boolean): Boolean =
    get(name)?.takeIf { !it.isJsonNull }?.asBoolean ?: fallback

private fun JsonObject.optInt(name: String, fallback: Int): Int =
    get(name)?.takeIf { !it.isJsonNull }?.asInt ?: fallback

private fun JsonObject.optIntOrNull(name: String): Int? =
    get(name)?.takeIf { !it.isJsonNull }?.asInt

private fun JsonObject.optArray(name: String): JsonArray =
    get(name)?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()

private fun safeLogI(message: String) {
    runCatching { AdsLog.i(AdsLog.TAG_REPO, message) }
}

private fun safeLogW(message: String) {
    runCatching { AdsLog.w(AdsLog.TAG_REPO, message) }
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
            configUpdatedAt = null,
            admobAppId = Constants.TEST_APP_ID,
            admobAppIdTest = Constants.TEST_APP_ID,
            admobAppIdLive = null,
            interstitialCooldownSeconds = 60,
            interstitialShowEveryNActions = 3
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
