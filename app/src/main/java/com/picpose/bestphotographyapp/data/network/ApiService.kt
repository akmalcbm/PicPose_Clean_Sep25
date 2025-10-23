package com.picpose.bestphotographyapp.data.network

import com.picpose.bestphotographyapp.data.models.*
import com.picpose.bestphotographyapp.data.remote.ApiResponse
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // -----------------------------------------------------------------------------------------
    // 🔹 App Settings & Tips
    // -----------------------------------------------------------------------------------------
    @GET("get_app_settings.php")
    suspend fun getAppSettings(
        @Query("api_key") apiKey: String? = null
    ): Response<AppSettingsResponse>

    @GET("ai_posts/get_daily_tips.php")
    suspend fun getDailyTips(
        @Query("api_key") apiKey: String? = null
    ): Response<ApiResponse<List<DailyTip>>>

    // -----------------------------------------------------------------------------------------
    // 🔹 AI Prompts
    // -----------------------------------------------------------------------------------------
    @GET("ai_posts/get_ai_post.php")
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

    // -----------------------------------------------------------------------------------------
    // 🔹 Guide Posts
    // -----------------------------------------------------------------------------------------
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
    ): Response<ApiResponse<List<GuidePostDto>>>

    @GET("guide_posts/get_guide_post.php")
    suspend fun getGuidePostById(
        @Query("id") guidePostId: String,
        @Query("api_key") apiKey: String? = null
    ): Response<ApiResponse<GuidePostDto>>

    // -----------------------------------------------------------------------------------------
    // 🔹 Categories
    // -----------------------------------------------------------------------------------------
    @GET("ai_posts/get_categories.php")
    suspend fun getCategories(
        @Query("api_key") apiKey: String? = null
    ): Response<ApiResponse<List<CategoryDto>>>

    // -----------------------------------------------------------------------------------------
    // 🔹 Quick Stats (total prompts, favorites, copies)
    // -----------------------------------------------------------------------------------------
    @GET("ai_posts/get_quick_stats.php")
    suspend fun getQuickStats(
        @Query("api_key") apiKey: String? = null
    ): Response<ApiResponse<StatsResponse>>

    // -----------------------------------------------------------------------------------------
    // 🔹 Increment Endpoints
    // -----------------------------------------------------------------------------------------

    // Likes (existing)
    @FormUrlEncoded
    @POST("ai_posts/increment_like.php")
    suspend fun incrementLike(
        @Field("id") id: Int,
        @Field("api_key") apiKey: String? = null
    ): Response<ApiResponse<Unit>>

    // Views
    @FormUrlEncoded
    @POST("ai_posts/increment_view.php")
    suspend fun incrementView(
        @Field("id") id: Int,
        @Field("api_key") apiKey: String? = null
    ): Response<ApiResponse<Unit>>

    // Copies
    @FormUrlEncoded
    @POST("ai_posts/increment_copy.php")
    suspend fun incrementCopy(
        @Field("id") id: Int,
        @Field("api_key") apiKey: String? = null
    ): Response<ApiResponse<Unit>>

    // Favorites ❤️ (your corrected version)
    @FormUrlEncoded
    @POST("ai_posts/increment_favorite.php")
    suspend fun incrementFavorite(
        @Field("id") id: Int,
        @Field("api_key") apiKey: String? = null
    ): Response<ApiResponse<Unit>>
}
