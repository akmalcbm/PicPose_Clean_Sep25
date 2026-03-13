/**
 * ---
 * File: RemoveBgApiService.kt
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

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface RemoveBgApiService {
    @Multipart
    @POST("api/remove_bg")
    suspend fun removeBg(
        @Part image: MultipartBody.Part,
        @Part("mode") mode: RequestBody,
        @Part("size") size: RequestBody,
        @Part("format") format: RequestBody
    ): Response<ResponseBody>
}
