/**
 * ---
 * File: ApiService.kt
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

import com.picpose.bestphotographyapp.data.remote.dto.*
import com.picpose.bestphotographyapp.data.remote.response.ApiResponse
import com.picpose.bestphotographyapp.data.remote.response.CopyResponse
import com.picpose.bestphotographyapp.data.remote.response.FavoriteResponse
import com.picpose.bestphotographyapp.data.remote.response.GuideLikePayload
import com.picpose.bestphotographyapp.data.remote.response.GuideViewPayload
import com.picpose.bestphotographyapp.data.remote.response.LikeResponse
import com.picpose.bestphotographyapp.data.remote.response.ViewResponse
import retrofit2.Response
import retrofit2.http.*

interface ApiService {
    // Retrofit contract for the main PicPose backend.
    // Repositories should map these raw responses into UI-friendly models.

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
    // 🔹 AI Prompts / AI Posts for Details Screen
    // -----------------------------------------------------------------------------------------
    @GET("api/ai_posts/get_ai_post.php")
    suspend fun getPromptById(
        @Query("id") promptId: String,
        @Query("api_key") apiKey: String?
    ): Response<ApiResponse<AIPrompt>>

    // -----------------------------------------------------------------------------------------
    // 🔹 AI Prompts / AI Posts for Favourites Bookmarked Screen
    // -----------------------------------------------------------------------------------------
    @FormUrlEncoded
    @POST("api/ai_posts/get_ai_post_by_id.php")
    suspend fun getPromptsByIds(
        @Field("ids") ids: String,   // comma separated ids
        @Field("api_key") apiKey: String? = null
    ): Response<ApiResponse<List<AIPrompt>>>


    /**
     * General prompt listing endpoint.
     *
     * Repositories reuse this method for search, category filters, popularity,
     * and pagination by varying the optional query parameters.
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
     * Convenience wrapper for recent prompts shown on the Home screen.
     */
    @GET("api/ai_posts/get_ai_posts.php")
    suspend fun getLatestRecent5AiPosts(
        @Query("api_key") apiKey: String? = null,
        @Query("limit") limit: Int = 5,
        @Query("order") order: String = "desc"
    ): Response<ApiResponse<List<AIPrompt>>>


    /* Not Used by ye get ai post me already implements tha
    getAiPosts(
   limit,
   offset,
   category,
   q,
   status
)
    *//**
     * 🔸 Categories Wise Posts (for Explore categories filter or HomeScreen horizontal list with category Chips)
     *//*

    @GET("api/ai_posts/get_ai_post_by_category.php")
    suspend fun getAIPostsByCategory(
        @Query("category_id") categoryId: Int,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
        @Query("api_key") apiKey: String?
    ): Response<ApiResponse<List<AIPrompt>>>
*/

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
        @Query("device_id") deviceId: String? = null,
        @Query("api_key") apiKey: String? = null
    ): Response<ApiResponse<GuidePostDto>>

    @FormUrlEncoded
    @POST("api/guide_posts/increment_guide_view.php")
    suspend fun incrementGuideView(
        @Field("id") id: Int,
        @Field("api_key") apiKey: String? = null
    ): Response<ApiResponse<GuideViewPayload>>

    @FormUrlEncoded
    @POST("api/guide_posts/toggle_guide_like.php")
    suspend fun toggleGuideLike(
        @Field("id") id: Int,
        @Field("device_id") deviceId: String,
        @Field("api_key") apiKey: String? = null
    ): Response<ApiResponse<GuideLikePayload>>

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
    ): Response<LikeResponse>

    @FormUrlEncoded
    @POST("api/ai_posts/decrement_like.php")
    suspend fun decrementLike(
        @Field("id") id: Int,
        @Field("api_key") apiKey: String? = null
    ): Response<LikeResponse>


    @FormUrlEncoded
    @POST("api/ai_posts/increment_favorite.php")
    suspend fun incrementFavorite(
        @Field("id") id: Int,
        @Field("api_key") apiKey: String? = null
    ): Response<FavoriteResponse>

    @FormUrlEncoded
    @POST("api/ai_posts/decrement_favorite.php")
    suspend fun decrementFavorite(
        @Field("id") id: Int,
        @Field("api_key") apiKey: String? = null
    ): Response<FavoriteResponse>


    @FormUrlEncoded
    @POST("api/ai_posts/increment_view.php")
    suspend fun incrementView(
        @Field("id") id: Int,
        @Field("api_key") apiKey: String? = null
    ): Response<ViewResponse>


    @FormUrlEncoded
    @POST("api/ai_posts/increment_copy.php")
    suspend fun incrementCopy(
        @Field("id") id: Int,
        @Field("api_key") apiKey: String? = null,
        @Field("action_type") actionType: String? = null,
        @Field("source") source: String? = null
    ): Response<CopyResponse>


    // -----------------------------------------------------------------------------------------
    // 🔹 Support Query
    // -----------------------------------------------------------------------------------------
    @POST("api/support/submit_query.php")
    suspend fun submitSupportQuery(
        @Body request: SupportQueryRequest,
        @Query("api_key") apiKey: String? = null
    ): Response<SupportQueryResponse>


}
