package com.picpose.bestphotographyapp.data.network

import com.picpose.bestphotographyapp.data.models.AIPromptDto
import com.picpose.bestphotographyapp.data.models.AIPromptResponse
import com.picpose.bestphotographyapp.data.models.CategoryResponse
import com.picpose.bestphotographyapp.data.models.PostResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    @GET("posts")
    suspend fun getPosts(
        @Header("API-Key") apiKey: String,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int
    ): Response<PostResponse>

    @GET("featured-posts")
    suspend fun getFeaturedPosts(
        @Header("API-Key") apiKey: String,
        @Query("limit") limit: Int = 5
    ): Response<PostResponse>

    @GET("categories")
    suspend fun getCategories(
        @Header("API-Key") apiKey: String
    ): Response<CategoryResponse>


    @GET("ai-prompts")
    suspend fun getAllAIPrompts(
        @Query("api_key") apiKey: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
        @Query("category") category: String? = null,
        @Query("search") search: String? = null
    ): Response<AIPromptResponse>

    @GET("ai-prompts/featured")
    suspend fun getFeaturedAIPrompts(
        @Query("api_key") apiKey: String,
        @Query("limit") limit: Int = 5
    ): Response<AIPromptResponse>

    @GET("ai-prompts/{id}")
    suspend fun getAIPromptById(
        @Path("id") id: String,
        @Query("api_key") apiKey: String
    ): Response<AIPromptDto>

    // For admin panel (future)
    @POST("admin/ai-prompts")
    suspend fun createAIPrompt(
        @Body prompt: AIPromptDto,
        @Header("Authorization") token: String
    ): Response<AIPromptDto>

    @PUT("admin/ai-prompts/{id}")
    suspend fun updateAIPrompt(
        @Path("id") id: String,
        @Body prompt: AIPromptDto,
        @Header("Authorization") token: String
    ): Response<AIPromptDto>

    @DELETE("admin/ai-prompts/{id}")
    suspend fun deleteAIPrompt(
        @Path("id") id: String,
        @Header("Authorization") token: String
    ): Response<Unit>

}
