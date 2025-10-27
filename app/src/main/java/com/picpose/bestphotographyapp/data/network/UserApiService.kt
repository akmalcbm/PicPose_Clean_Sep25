package com.picpose.bestphotographyapp.data.network

import com.picpose.bestphotographyapp.data.models.AuthResponse
import com.picpose.bestphotographyapp.data.models.LoginRequest
import com.picpose.bestphotographyapp.data.models.RegisterRequest
import com.picpose.bestphotographyapp.data.models.User
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

/**
 * User API service for authentication and user profile operations
 */
interface UserApiService {
    
    @POST("api/users.php")
    suspend fun login(
        @Query("action") action: String = "login",
        @Body request: LoginRequest
    ): Response<AuthResponse>
    
    @POST("api/users.php")
    suspend fun register(
        @Query("action") action: String = "register",
        @Body request: RegisterRequest
    ): Response<AuthResponse>
    
    @GET("api/users.php")
    suspend fun getUserProfile(
        @Query("id") userId: String,
        @Query("api_key") apiKey: String? = null
    ): Response<AuthResponse>
    
    @PUT("api/users.php")
    suspend fun updateUserProfile(
        @Query("id") userId: String,
        @Body user: User,
        @Query("api_key") apiKey: String? = null
    ): Response<AuthResponse>

    @Multipart
    @POST("api/update_profile.php")
    suspend fun updateProfile(
        @Part("user_id") userId: RequestBody,
        @Part("name") name: RequestBody,
        @Part("bio") bio: RequestBody?,
        @Part("account_type") accountType: RequestBody,
        @Part profile_picture: MultipartBody.Part?,
        @Header("X-API-Key") apiKey: String = "7a6f3c27a1b6d5e8e4c8a2b3f9e6d1f47c5b8a9d3e7f2c6a4b9e3d1c5f8a7b2c"
    ): Response<AuthResponse>

}
