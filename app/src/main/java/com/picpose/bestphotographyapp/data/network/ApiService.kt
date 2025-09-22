package com.picpose.bestphotographyapp.data.network

import com.picpose.bestphotographyapp.data.models.AIPrompt
import com.picpose.bestphotographyapp.data.remote.ApiResponse
import com.picpose.bestphotographyapp.data.models.DailyTip
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface ApiService {
    @GET("get_daily_tips.php")
    suspend fun getDailyTips(
        @Query("api_key") apiKey: String? = null
    ): Response<ApiResponse<List<DailyTip>>> // <- typed to DailyTip

    @GET("get_ai_posts.php")
    suspend fun getAiPosts(
        @Query("api_key") apiKey: String? = null,
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null,
        @Query("q") q: String? = null,
        @Query("category") category: String? = null,
        @Query("tag") tag: String? = null,
        @Query("popular") popular: Boolean? = null,
        @Query("status") status: String? = null,
        @Query("featured") featured: Boolean? = null
    ): Response<ApiResponse<List<AIPrompt>>>
}
