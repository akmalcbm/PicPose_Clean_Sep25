package com.picpose.bestphotographyapp.data.network

import com.picpose.bestphotographyapp.data.remote.AdsConfigResponse
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
