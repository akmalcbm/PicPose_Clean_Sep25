package com.picpose.bestphotographyapp.data.network

import com.picpose.bestphotographyapp.data.models.AuthResponse
import com.picpose.bestphotographyapp.data.models.LoginRequest
import com.picpose.bestphotographyapp.data.models.RegisterRequest
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

/**
 * User API service for authentication and user profile operations
 *
 * Notes:
 * - All endpoints accept an optional apiKey; prefer sending via header "X-API-Key".
 * - updateProfile is multipart and accepts profile picture as "profile_picture".
 */
interface UserApiService {

    @POST("api/users.php")
    suspend fun login(
        @Query("action") action: String = "login",
        @Body request: LoginRequest,
        @Header("X-API-Key") apiKey: String? = null
    ): Response<AuthResponse>

    @POST("api/users.php")
    suspend fun register(
        @Query("action") action: String = "register",
        @Body request: RegisterRequest,
        @Header("X-API-Key") apiKey: String? = null
    ): Response<AuthResponse>

    @GET("api/users.php")
    suspend fun getUserProfile(
        @Query("id") userId: String,
        @Header("X-API-Key") apiKey: String? = null
    ): Response<AuthResponse>

    @PUT("api/users.php")
    suspend fun updateUserProfile(
        @Query("id") userId: String,
        @Body user: Map<String, @JvmSuppressWildcards Any>,
        @Header("X-API-Key") apiKey: String? = null
    ): Response<AuthResponse>

    @Multipart
    @POST("api/update_profile.php")
    suspend fun updateProfile(
        @Part("user_id") userId: RequestBody,
        @Part("name") name: RequestBody,
        @Part("bio") bio: RequestBody?,
        @Part("account_type") accountType: RequestBody,
        @Part profile_picture: MultipartBody.Part?,
        @Header("X-API-Key") apiKey: String? = null
    ): Response<AuthResponse>
}
