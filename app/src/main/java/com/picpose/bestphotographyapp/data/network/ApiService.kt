package com.picpose.bestphotographyapp.data.network

import com.picpose.bestphotographyapp.data.models.CategoryResponse
import com.picpose.bestphotographyapp.data.models.PostResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
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
}
