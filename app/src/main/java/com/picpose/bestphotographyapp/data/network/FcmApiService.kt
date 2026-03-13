/**
 * ---
 * File: FcmApiService.kt
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
