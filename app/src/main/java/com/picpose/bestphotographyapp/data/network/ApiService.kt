package com.picpose.bestphotographyapp.data.network

import com.picpose.bestphotographyapp.data.models.AIPrompt
import com.picpose.bestphotographyapp.data.models.AppSettingsResponse
import com.picpose.bestphotographyapp.data.models.CategoryDto
import com.picpose.bestphotographyapp.data.models.GuidePostDto
import com.picpose.bestphotographyapp.data.remote.ApiResponse
import com.picpose.bestphotographyapp.data.models.DailyTip
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface ApiService {
    @GET("get_app_settings.php")
    suspend fun getAppSettings(
        @Query("api_key") apiKey: String? = null
    ): Response<AppSettingsResponse>

    @GET("ai_posts/get_daily_tips.php")
    suspend fun getDailyTips(
        @Query("api_key") apiKey: String? = null
    ): Response<ApiResponse<List<DailyTip>>> // <- typed to DailyTip

    // ✅ ADD: Single prompt endpoint
    @GET("ai_posts/get_ai_post.php") // or whatever your single post endpoint is
    suspend fun getPromptById(
        @Query("id") promptId: String,
        @Query("api_key") apiKey: String? = null
    ): Response<ApiResponse<AIPrompt>>

    @GET("ai_posts/get_ai_posts.php")
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

    // Guide Posts endpoints
    @GET("guide_posts/get_guide_posts.php")
    suspend fun getGuidePosts(
        @Query("api_key") apiKey: String? = null,
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null,
        @Query("page") page: Int? = null,
        @Query("q") q: String? = null,
        @Query("category") category: String? = null,
        @Query("featured") featured: Boolean? = null,
        @Query("popular") popular: Boolean? = null,
        @Query("status") status: String? = null
    ): Response<com.picpose.bestphotographyapp.data.remote.ApiResponse<List<com.picpose.bestphotographyapp.data.models.GuidePostDto>>>

    @GET("guide_posts/get_guide_post.php")
    suspend fun getGuidePostById(
        @Query("id") guidePostId: String,
        @Query("api_key") apiKey: String? = null
    ): Response<ApiResponse<GuidePostDto>>

    @GET("ai_posts/get_categories.php")
    suspend fun getCategories(
        @Query("api_key") apiKey: String = "7a6f3c27a1b6d5e8e4c8a2b3f9e6d1f47c5b8a9d3e7f2c6a4b9e3d1c5f8a7b2c"
    ): Response<ApiResponse<List<CategoryDto>>>

}
