package com.picpose.bestphotographyapp.data.network

import com.picpose.bestphotographyapp.data.models.AIPromptResponse
import com.picpose.bestphotographyapp.data.models.DailyTip
import com.picpose.bestphotographyapp.data.models.PostResponse
import com.picpose.bestphotographyapp.data.remote.ApiResponseDailyTips
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface ApiService {
    @GET("get_daily_tips.php")
    suspend fun getDailyTips(
        @Query("api_key") apiKey: String
    ): Response<ApiResponseDailyTips<List<DailyTip>>>

    @GET("get_posts.php")
    suspend fun getPosts(
        @Query("api_key") apiKey: String,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int
    ): Response<PostResponse>

    @GET("get_latest_posts.php")
    suspend fun getLatestPosts(
        @Query("api_key") apiKey: String,
        @Query("limit") limit: Int = 10
    ): Response<PostResponse>

    @GET("get_posts_by_category.php")
    suspend fun getPostsByCategory(
        @Query("api_key") apiKey: String,
        @Query("category") category: String,
        @Query("limit") limit: Int = 20,
        @Query("page") page: Int = 1
    ): Response<PostResponse>

    @GET("get_posts_by_tag.php")
    suspend fun getPostsByTag(
        @Query("api_key") apiKey: String,
        @Query("tag") tag: String,
        @Query("limit") limit: Int = 20,
        @Query("page") page: Int = 1
    ): Response<PostResponse>

    @GET("get_single_post.php")
    suspend fun getSinglePost(
        @Query("api_key") apiKey: String,
        @Query("id") id: String
    ): Response<PostResponse> // or Response<PostDto> depending on endpoint

    @GET("search_posts.php")
    suspend fun searchPosts(
        @Query("api_key") apiKey: String,
        @Query("q") q: String,
        @Query("limit") limit: Int = 20,
        @Query("page") page: Int = 1
    ): Response<PostResponse>

    // AI prompts endpoints (if filenames differ use get_ai_prompts.php)
    @GET("get_ai_prompts.php")
    suspend fun getAllAIPrompts(
        @Query("api_key") apiKey: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
        @Query("category") category: String? = null,
        @Query("search") search: String? = null
    ): Response<AIPromptResponse>

    // support fetching a single prompt by id (server returns same response shape, with data array)
    @GET("get_ai_prompts.php")
    suspend fun getAIPromptById(
        @Query("api_key") apiKey: String,
        @Query("id") id: String
    ): Response<AIPromptResponse>
}
