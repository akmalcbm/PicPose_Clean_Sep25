package com.picpose.bestphotographyapp.data.network

import com.picpose.bestphotographyapp.data.remote.AdsConfigResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface AdsApiService {

    @GET("api/ads_config.php")
    suspend fun getAdsConfig(
        @Query("api_key") apiKey: String
    ): Response<AdsConfigResponse>
}
