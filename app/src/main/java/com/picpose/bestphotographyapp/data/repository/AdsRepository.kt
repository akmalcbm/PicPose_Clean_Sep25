package com.picpose.bestphotographyapp.data.repository

import com.google.gson.Gson
import com.picpose.bestphotographyapp.BuildConfig
import com.picpose.bestphotographyapp.core.constants.Constants
import com.picpose.bestphotographyapp.data.models.GlobalConfig
import com.picpose.bestphotographyapp.data.models.PlacementConfig
import com.picpose.bestphotographyapp.data.models.UnitConfig
import com.picpose.bestphotographyapp.data.datastore.DeviceIdStore
import com.picpose.bestphotographyapp.data.models.AdsConfig
import com.picpose.bestphotographyapp.data.network.AdsApiService
import com.picpose.bestphotographyapp.data.network.RetrofitClient
import com.picpose.bestphotographyapp.data.remote.toDomainOrNull
import com.picpose.bestphotographyapp.presentation.ads.AdsConfigCache
import com.picpose.bestphotographyapp.presentation.ads.AdsLog

enum class AdsConfigSource {
    NETWORK,
    CACHE,
    FALLBACK
}

data class AdsConfigResult(
    val config: AdsConfig,
    val source: AdsConfigSource,
    val timestamp: Long,
    val configVersion: String
)

class AdsRepository(
    private val api: AdsApiService,
    private val cache: AdsConfigCache,
    private val deviceIdStore: DeviceIdStore
) {

    suspend fun getAdsConfig(forceRefresh: Boolean = false): AdsConfigResult {
        AdsLog.i(AdsLog.TAG_REPO, "[AdsRepo] action=getAdsConfig forceRefresh=$forceRefresh")
        val cached = cache.get()
        if (!forceRefresh && cached != null) {
            val ageMs = System.currentTimeMillis() - cached.timestamp
            val fresh = cache.isFresh(cached.timestamp)
            AdsLog.d(
                AdsLog.TAG_REPO,
                "[AdsRepo] cacheCheck fresh=$fresh ageMs=$ageMs ttlMs=${AdsConfigCache.TTL_MS} version=${cached.configVersion} updatedAt=${cached.config.global.configUpdatedAt}"
            )
            if (fresh) {
            AdsLog.i(
                AdsLog.TAG_REPO,
                "[AdsRepo] source=CACHE reason=fresh version=${cached.configVersion} ts=${cached.timestamp} updatedAt=${cached.config.global.configUpdatedAt} ageMs=$ageMs ttlMs=${AdsConfigCache.TTL_MS}"
            )
            return AdsConfigResult(
                config = cached.config,
                source = AdsConfigSource.CACHE,
                timestamp = cached.timestamp,
                configVersion = cached.configVersion
            )
        }
        }

        val networkResult = fetchFromNetwork()
        if (networkResult != null) {
            AdsLog.i(
                AdsLog.TAG_REPO,
                "[AdsRepo] source=NETWORK version=${networkResult.configVersion} ts=${networkResult.timestamp} updatedAt=${networkResult.config.global.configUpdatedAt}"
            )
            return networkResult
        }

        if (cached != null) {
            val ageMs = System.currentTimeMillis() - cached.timestamp
            AdsLog.w(
                AdsLog.TAG_REPO,
                "[AdsRepo] source=CACHE reason=network_failed stale=true version=${cached.configVersion} ts=${cached.timestamp} ageMs=$ageMs ttlMs=${AdsConfigCache.TTL_MS}"
            )
            return AdsConfigResult(
                config = cached.config,
                source = AdsConfigSource.CACHE,
                timestamp = cached.timestamp,
                configVersion = cached.configVersion
            )
        }

        val now = System.currentTimeMillis()
        val fallback = buildFallbackConfig()
        AdsLog.w(
            AdsLog.TAG_REPO,
            "[AdsRepo] source=FALLBACK reason=network_and_cache_failed ts=$now placements=${fallback.placements.size}"
        )
        return AdsConfigResult(
            config = fallback,
            source = AdsConfigSource.FALLBACK,
            timestamp = now,
            configVersion = fallback.global.configUpdatedAt ?: "fallback"
        )
    }

    private suspend fun fetchFromNetwork(): AdsConfigResult? {
        return runCatching {
            val deviceId = deviceIdStore.getOrCreateDeviceId()
            val start = System.currentTimeMillis()
            AdsLog.i(
                AdsLog.TAG_REPO,
                "[AdsRepo] request=GET endpoint=/${AdsApiService.ADS_CONFIG_PATH} appVersion=${BuildConfig.VERSION_NAME} platform=android deviceId=${AdsLog.maskDeviceId(deviceId)}"
            )
            val response = api.getAdsConfig(
                deviceId = deviceId,
                appVersion = BuildConfig.VERSION_NAME,
                platform = "android",
                apiKey = RetrofitClient.defaultApiKey
            )
            val duration = System.currentTimeMillis() - start
            val requestUrl = response.raw().request.url.toString()
            AdsLog.i(
                AdsLog.TAG_REPO,
                "[AdsRepo] response url=$requestUrl http=${response.code()} durationMs=$duration successful=${response.isSuccessful}"
            )

            if (!response.isSuccessful) {
                AdsLog.w(AdsLog.TAG_REPO, "[AdsRepo] network status=FAIL code=${response.code()} durationMs=$duration")
                return null
            }

            val body = response.body()
            if (body == null) {
                AdsLog.w(AdsLog.TAG_REPO, "[AdsRepo] parse status=FAIL reason=empty_body")
                return null
            }
            val domainConfig = runCatching { body.toDomainOrNull() }
                .onFailure { AdsLog.e(AdsLog.TAG_REPO, "[AdsRepo] parse status=FAIL reason=${it.message}", it) }
                .getOrNull() ?: return null

            val rawJson = Gson().toJson(body)
            val now = System.currentTimeMillis()
            val configVersion = domainConfig.global.configUpdatedAt ?: "unknown"

            cache.save(
                config = domainConfig,
                rawJson = rawJson,
                configVersion = configVersion,
                timestamp = now
            )

            AdsConfigResult(
                config = domainConfig,
                source = AdsConfigSource.NETWORK,
                timestamp = now,
                configVersion = configVersion
            )
        }.getOrElse {
            AdsLog.e(AdsLog.TAG_REPO, "[AdsRepo] network status=EXCEPTION error=${it.message}", it)
            null
        }
    }

    private fun buildFallbackConfig(): AdsConfig {
        fun placement(key: String, adType: String): PlacementConfig {
            val id = Constants.fallbackAdUnitIdFor(key)
            val units = if (id.isNullOrBlank()) {
                emptyList()
            } else {
                listOf(
                    UnitConfig(
                        adUnitId = id,
                        priority = 1,
                        isTest = true,
                        isLive = false,
                        network = "admob",
                        sdkRequired = true
                    )
                )
            }

            return PlacementConfig(
                key = key,
                adType = adType,
                enabled = true,
                refreshSeconds = null,
                frequency = null,
                autoDisabled = false,
                units = units
            )
        }

        return AdsConfig(
            global = GlobalConfig(
                adsEnabled = true,
                environment = "test",
                cmpRequired = true,
                defaultFrequencyPerHour = 2,
                useTestAds = true,
                configUpdatedAt = null
            ),
            placements = listOf(
                placement("banner_1", "banner"),
                placement("banner_2", "banner"),
                placement("interstitial_1", "interstitial"),
                placement("interstitial_2", "interstitial"),
                placement("native_1", "native"),
                placement("native_2", "native"),
                placement("native_3", "native"),
                placement("rewarded_1", "rewarded")
            )
        )
    }
}
