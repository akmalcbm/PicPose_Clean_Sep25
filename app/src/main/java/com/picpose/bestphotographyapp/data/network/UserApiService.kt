package com.picpose.bestphotographyapp.data.network

import com.picpose.bestphotographyapp.data.models.AuthResponse
import com.picpose.bestphotographyapp.data.models.LoginRequest
import com.picpose.bestphotographyapp.data.models.RegisterRequest
import com.picpose.bestphotographyapp.data.models.User
import retrofit2.Response
import retrofit2.http.*

/**
 * User API service for authentication and user profile operations
 */
interface UserApiService {
    
    @POST("users.php")
    suspend fun login(
        @Query("action") action: String = "login",
        @Body request: LoginRequest
    ): Response<AuthResponse>
    
    @POST("users.php")
    suspend fun register(
        @Query("action") action: String = "register",
        @Body request: RegisterRequest
    ): Response<AuthResponse>
    
    @GET("users.php")
    suspend fun getUserProfile(
        @Query("id") userId: String,
        @Query("api_key") apiKey: String? = null
    ): Response<AuthResponse>
    
    @PUT("users.php")
    suspend fun updateUserProfile(
        @Query("id") userId: String,
        @Body user: User,
        @Query("api_key") apiKey: String? = null
    ): Response<AuthResponse>
}
