/**
 * ---
 * File: AdsConfigCache.kt
 * Layer: Presentation (UI)
 * Project: PicPose
 *
 * Purpose:
 * Supports the PicPose app with feature-specific models, helpers, or UI building blocks.
 *
 * Interactions:
 * Works with nearby classes in the same layer to keep responsibilities separated and easier to maintain.
 *
 * Data Flow:
 * Feature-specific flow; see adjacent ViewModels and repositories for the full path.
 *
 * Maintainer Notes:
 * - Keep SDK-specific code isolated here so feature screens remain testable.
 * - TODO: Add analytics and remote-config driven rollout controls where appropriate.
 * ---
 */

package com.picpose.bestphotographyapp.components.ads

import android.content.Context
import com.google.gson.Gson
import com.picpose.bestphotographyapp.data.remote.dto.AdsConfig
import com.picpose.bestphotographyapp.data.remote.response.AdsConfigResponse
import com.picpose.bestphotographyapp.data.remote.response.toDomainOrNull

class AdsConfigCache(context: Context) {

    companion object {
        const val TTL_MS: Long = 300_000L // 300 seconds

        private const val PREFS_NAME = "ads_config"
        private const val KEY_JSON = "json"
        private const val KEY_TIME = "time"
        private const val KEY_CONFIG_VERSION = "config_version"
    }

    data class CachedAdsConfig(
        val config: AdsConfig,
        val rawJson: String,
        val timestamp: Long,
        val configVersion: String
    )

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    @Volatile
    private var inMemory: CachedAdsConfig? = null

    fun save(
        config: AdsConfig,
        rawJson: String,
        configVersion: String,
        timestamp: Long = System.currentTimeMillis()
    ) {
        AdsLog.i(
            AdsLog.TAG_CACHE,
            "[AdsCache] action=save version=$configVersion ts=$timestamp placements=${config.placements.size} updatedAt=${config.global.configUpdatedAt}"
        )
        inMemory = CachedAdsConfig(
            config = config,
            rawJson = rawJson,
            timestamp = timestamp,
            configVersion = configVersion
        )

        prefs.edit()
            .putString(KEY_JSON, rawJson)
            .putLong(KEY_TIME, timestamp)
            .putString(KEY_CONFIG_VERSION, configVersion)
            .apply()
    }

    fun get(): CachedAdsConfig? {
        inMemory?.let {
            AdsLog.d(
                AdsLog.TAG_CACHE,
                "[AdsCache] action=get source=MEMORY version=${it.configVersion} ts=${it.timestamp}"
            )
            return it
        }

        val rawJson = prefs.getString(KEY_JSON, null) ?: return null
        val timestamp = prefs.getLong(KEY_TIME, 0L)
        if (timestamp <= 0L) return null

        val parsed = runCatching {
            gson.fromJson(rawJson, AdsConfigResponse::class.java)?.toDomainOrNull()
        }.getOrNull() ?: return null

        val configVersion = prefs.getString(KEY_CONFIG_VERSION, null)
            ?: parsed.global.configUpdatedAt
            ?: "unknown"

        val cached = CachedAdsConfig(
            config = parsed,
            rawJson = rawJson,
            timestamp = timestamp,
            configVersion = configVersion
        ).also { inMemory = it }
        AdsLog.d(
            AdsLog.TAG_CACHE,
            "[AdsCache] action=get source=DISK version=${cached.configVersion} ts=${cached.timestamp} placements=${cached.config.placements.size}"
        )
        return cached
    }

    fun isFresh(timestamp: Long, ttlMs: Long = TTL_MS): Boolean {
        val ageMs = System.currentTimeMillis() - timestamp
        val fresh = ageMs <= ttlMs
        AdsLog.d(AdsLog.TAG_CACHE, "[AdsCache] action=isFresh ts=$timestamp ageMs=$ageMs ttlMs=$ttlMs fresh=$fresh")
        return fresh
    }

    fun clear() {
        AdsLog.w(AdsLog.TAG_CACHE, "[AdsCache] action=clear")
        inMemory = null
        prefs.edit().clear().apply()
    }
}
