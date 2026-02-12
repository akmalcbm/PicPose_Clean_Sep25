package com.picpose.bestphotographyapp.data.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

data class RegisterFcmTokenRequest(
    val token: String,
    val user_id: Int? = null,
    val platform: String = "android",
    val app_version: String,
    val device_model: String,
    val os_version: String,
    val language: String,
    val country: String,
    val timezone: String
)

data class RegisterFcmTokenResponse(
    val success: Boolean = false,
    val message: String? = null,
    val error: String? = null
)

interface FcmApiService {
    @POST("api/register-device.php")
    suspend fun registerDevice(@Body request: RegisterFcmTokenRequest): Response<RegisterFcmTokenResponse>
}
