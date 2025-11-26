package com.picpose.bestphotographyapp.data.network

import com.picpose.bestphotographyapp.data.models.AuthResponse
import com.picpose.bestphotographyapp.data.models.LoginRequest
import com.picpose.bestphotographyapp.data.models.RegisterRequest
import com.picpose.bestphotographyapp.data.models.SocialAuthData
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface UserApiService {

    // ---------------------------------------------------------
    // LOGIN
    // ---------------------------------------------------------
    @POST("api/users.php")
    suspend fun login(
        @Query("action") action: String = "login",
        @Body request: LoginRequest,
        @Header("X-API-Key") apiKey: String? = null
    ): Response<AuthResponse>


    // ---------------------------------------------------------
    // REGISTER
    // ---------------------------------------------------------
    @POST("api/users.php")
    suspend fun register(
        @Query("action") action: String = "register",
        @Body request: RegisterRequest,
        @Header("X-API-Key") apiKey: String? = null
    ): Response<AuthResponse>


    // ---------------------------------------------------------
    // GET USER PROFILE
    // ---------------------------------------------------------
    @GET("api/users.php")
    suspend fun getUserProfile(
        @Query("id") userId: String,
        @Header("X-API-Key") apiKey: String? = null
    ): Response<AuthResponse>


    // ---------------------------------------------------------
    // SOCIAL LOGIN (Google / Facebook)
    // ---------------------------------------------------------
    @POST("api/social_login.php")
    suspend fun socialLogin(
        @Body data: SocialAuthData,
        @Header("X-API-Key") apiKey: String? = null
    ): Response<AuthResponse>


    // ---------------------------------------------------------
    // MULTIPART PROFILE UPDATE
    // Fields MUST match your PHP file
    // ---------------------------------------------------------
    @Multipart
    @POST("api/update_profile.php")
    suspend fun updateProfile(
        @Part("user_id") userId: RequestBody,
        @Part("name") name: RequestBody,
        @Part("bio") bio: RequestBody,
        @Part("account_type") accountType: RequestBody,
        @Part profile_picture: MultipartBody.Part?,
        @Part("api_key") apiKey: RequestBody
    ): Response<AuthResponse>

}
