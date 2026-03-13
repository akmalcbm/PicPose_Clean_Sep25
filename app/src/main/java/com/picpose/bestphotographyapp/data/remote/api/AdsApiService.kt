/**
 * ---
 * File: AdsApiService.kt
 * Layer: Data
 * Project: PicPose
 *
 * Purpose:
 * Declares Retrofit endpoints used to communicate with backend services.
 *
 * Interactions:
 * Consumed by repositories to talk to backend APIs and map raw payloads into app models.
 *
 * Data Flow:
 * Repository -> Retrofit service -> Backend response -> Model mapping -> ViewModel/UI
 *
 * Maintainer Notes:
 * - Prefer backend-neutral mapping in repositories instead of leaking transport details into the UI.
 * - TODO: Add stricter error classification and retry policy where network flows are user-critical.
 * ---
 */

package com.picpose.bestphotographyapp.data.remote.api

import com.picpose.bestphotographyapp.data.remote.response.AdsConfigResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface AdsApiService {

    companion object {
        const val ADS_CONFIG_PATH = "api/ads_config.php"
    }

    @GET(ADS_CONFIG_PATH)
    suspend fun getAdsConfig(
        @Header("X-Device-ID") deviceId: String,
        @Header("X-App-Version") appVersion: String,
        @Header("X-Platform") platform: String = "android",
        @Query("api_key") apiKey: String? = null
    ): Response<AdsConfigResponse>
}
