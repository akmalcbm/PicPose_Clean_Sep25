package com.picpose.bestphotographyapp.data.repository

import com.picpose.bestphotographyapp.data.network.AdsApiService
import com.picpose.bestphotographyapp.data.remote.AdsConfigResponse

class AdsRepository(
    private val api: AdsApiService
) {

    suspend fun fetchAdsConfig(apiKey: String): AdsConfigResponse? {
        return try {
            val res = api.getAdsConfig(apiKey)
            if (res.isSuccessful) res.body() else null
        } catch (e: Exception) {
            null
        }
    }
}
