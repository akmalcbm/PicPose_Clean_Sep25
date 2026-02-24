package com.picpose.bestphotographyapp.data.network

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
