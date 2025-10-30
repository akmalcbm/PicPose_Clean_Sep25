package com.picpose.bestphotographyapp.data.network

import com.picpose.bestphotographyapp.data.models.*
import com.picpose.bestphotographyapp.data.remote.ApiResponse
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // -----------------------------------------------------------------------------------------
    // 🔹 App Settings & Tips
    // -----------------------------------------------------------------------------------------
    @GET("api/get_app_settings.php")
    suspend fun getAppSettings(
        @Query("api_key") apiKey: String? = null
    ): Response<AppSettingsResponse>

    @GET("api/ai_posts/get_daily_tips.php")
    suspend fun getDailyTips(
        @Query("api_key") apiKey: String? = null
    ): Response<ApiResponse<List<DailyTip>>>

    // -----------------------------------------------------------------------------------------
    // 🔹 AI Prompts / AI Posts
    // -----------------------------------------------------------------------------------------
    @GET("api/ai_posts/get_ai_post.php")
    suspend fun getPromptById(
        @Query("id") promptId: String,
        @Query("api_key") apiKey: String? = null
    ): Response<ApiResponse<AIPrompt>>

    /**
     * General list of AI posts with optional filters.
     */
    @GET("api/ai_posts/get_ai_posts.php")
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

    /**
     * 🔸 Newest / Latest posts (for HomeScreen)
     */
    @GET("api/ai_posts/get_ai_posts.php")
    suspend fun getLatestRecent5AiPosts(
        @Query("api_key") apiKey: String? = null,
        @Query("limit") limit: Int = 5,
        @Query("order") order: String = "desc"
    ): Response<ApiResponse<List<AIPrompt>>>

    /**
     * 🔸 Trending posts (for Explore filter or HomeScreen horizontal list)
     */
    @GET("api/ai_posts/get_ai_post_by_trends.php")
    suspend fun getTrendingAiPosts(
        @Query("api_key") apiKey: String? = null,
        @Query("type") type: String = "popular",
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0
    ): Response<ApiResponse<List<AIPrompt>>>

    /**
     * 🔸 Favorite posts for a specific user (future-ready)
     * Example endpoint: api/ai_posts/get_ai_posts.php?filter=favorite&user_id=123
     */
    @GET("api/ai_posts/get_ai_posts.php")
    suspend fun getFavoriteAiPosts(
        @Query("api_key") apiKey: String? = null,
        @Query("filter") filter: String = "favorite",
        @Query("user_id") userId: String
    ): Response<ApiResponse<List<AIPrompt>>>


    /**
     * 🔸 Most Liked posts
     */
    @GET("api/ai_posts/get_ai_post_by_likes.php")
    suspend fun getMostLikedAiPosts(
        @Query("api_key") apiKey: String? = null,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0
    ): Response<ApiResponse<List<AIPrompt>>>

    // -----------------------------------------------------------------------------------------
    // 🔹 Guide Posts
    // -----------------------------------------------------------------------------------------
    @GET("api/guide_posts/get_guide_posts.php")
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

    @GET("api/guide_posts/get_guide_post.php")
    suspend fun getGuidePostById(
        @Query("id") guidePostId: String,
        @Query("api_key") apiKey: String? = null
    ): Response<ApiResponse<GuidePostDto>>

    // -----------------------------------------------------------------------------------------
    // 🔹 Categories
    // -----------------------------------------------------------------------------------------
    @GET("api/ai_posts/get_categories.php")
    suspend fun getCategories(
        @Query("api_key") apiKey: String? = null
    ): Response<ApiResponse<List<CategoryDto>>>

    // -----------------------------------------------------------------------------------------
    // 🔹 Quick Stats (total prompts, favorites, copies)
    // -----------------------------------------------------------------------------------------
    @GET("api/ai_posts/get_quick_stats.php")
    suspend fun getQuickStats(
        @Query("api_key") apiKey: String? = null
    ): Response<ApiResponse<StatsResponse>>

    // -----------------------------------------------------------------------------------------
    // 🔹 Increment Endpoints
    // -----------------------------------------------------------------------------------------
    @FormUrlEncoded
    @POST("api/ai_posts/increment_like.php")
    suspend fun incrementLike(
        @Field("id") id: Int,
        @Field("api_key") apiKey: String? = null
    ): Response<ApiResponse<Unit>>

    @FormUrlEncoded
    @POST("api/ai_posts/increment_view.php")
    suspend fun incrementView(
        @Field("id") id: Int,
        @Field("api_key") apiKey: String? = null
    ): Response<ApiResponse<Unit>>

    @FormUrlEncoded
    @POST("api/ai_posts/increment_copy.php")
    suspend fun incrementCopy(
        @Field("id") id: Int,
        @Field("api_key") apiKey: String? = null
    ): Response<ApiResponse<Unit>>

    @FormUrlEncoded
    @POST("api/ai_posts/increment_favorite.php")
    suspend fun incrementFavorite(
        @Field("id") id: Int,
        @Field("api_key") apiKey: String? = null
    ): Response<ApiResponse<Unit>>

    // -----------------------------------------------------------------------------------------
    // 🔹 Support Query
    // -----------------------------------------------------------------------------------------
    @POST("api/support/submit_query.php")
    suspend fun submitSupportQuery(
        @Body request: SupportQueryRequest,
        @Query("api_key") apiKey: String? = null
    ): Response<SupportQueryResponse>


}
