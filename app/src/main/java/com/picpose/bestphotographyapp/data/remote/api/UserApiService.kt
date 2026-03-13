/**
 * ---
 * File: UserApiService.kt
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

import com.picpose.bestphotographyapp.data.remote.dto.AuthResponse
import com.picpose.bestphotographyapp.data.remote.dto.DeleteAccountRequest
import com.picpose.bestphotographyapp.data.remote.dto.ForgotPasswordRequest
import com.picpose.bestphotographyapp.data.remote.dto.LoginRequest
import com.picpose.bestphotographyapp.data.remote.dto.RequestEmailVerificationRequest
import com.picpose.bestphotographyapp.data.remote.dto.RegisterRequest
import com.picpose.bestphotographyapp.data.remote.dto.ResetPasswordRequest
import com.picpose.bestphotographyapp.data.remote.dto.SocialAuthData
import com.picpose.bestphotographyapp.data.remote.dto.VerifyEmailTokenRequest
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface UserApiService {

    // -------------------------------------------------------------------
    // EMAIL/PASSWORD LOGIN
    // POST: /api/users.php?action=login
    // -------------------------------------------------------------------
    @POST("api/users.php")
    suspend fun login(
        @Query("action") action: String = "login",
        @Body request: LoginRequest,
        @Header("X-API-Key") apiKey: String? = null
    ): Response<AuthResponse>

    // -------------------------------------------------------------------
    // REGISTER NEW USER
    // POST: /api/users.php?action=register
    // -------------------------------------------------------------------
    @POST("api/users.php")
    suspend fun register(
        @Query("action") action: String = "register",
        @Body request: RegisterRequest,
        @Header("X-API-Key") apiKey: String? = null
    ): Response<AuthResponse>

    // -------------------------------------------------------------------
    // GET USER PROFILE
    // GET: /api/users.php?id=123
    // -------------------------------------------------------------------
    @GET("api/users.php")
    suspend fun getUserProfile(
        @Query("id") userId: String,
        @Header("X-API-Key") apiKey: String? = null
    ): Response<AuthResponse>

    // -------------------------------------------------------------------
    // SOCIAL LOGIN (Google / Facebook / Twitter)
    // POST: /api/social_login.php
    // BODY: SocialAuthData
    // -------------------------------------------------------------------
    @POST("api/social_login.php")
    suspend fun socialLogin(
        @Body data: SocialAuthData,
        @Header("X-API-Key") apiKey: String? = null
    ): Response<AuthResponse>

    // -------------------------------------------------------------------
    // UPDATE USER PROFILE (with optional image upload)
    // POST: /api/update_profile.php
    // -------------------------------------------------------------------
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

    // -------------------------------------------------------------------
    // DELETE ACCOUNT (in-app user initiated)
    // POST: /api/users.php?action=delete_account
    // -------------------------------------------------------------------
    @POST("api/users.php")
    suspend fun deleteAccount(
        @Query("action") action: String = "delete_account",
        @Body request: DeleteAccountRequest,
        @Header("X-API-Key") apiKey: String? = null
    ): Response<AuthResponse>

    @POST("api/auth/request_password_reset.php")
    suspend fun requestPasswordReset(
        @Body request: ForgotPasswordRequest,
        @Header("X-API-Key") apiKey: String? = null
    ): Response<AuthResponse>

    @POST("api/auth/reset_password.php")
    suspend fun resetPassword(
        @Body request: ResetPasswordRequest,
        @Header("X-API-Key") apiKey: String? = null
    ): Response<AuthResponse>

    @POST("api/auth/request_email_verification.php")
    suspend fun requestEmailVerification(
        @Body request: RequestEmailVerificationRequest,
        @Header("X-API-Key") apiKey: String? = null
    ): Response<AuthResponse>

    @POST("api/auth/verify_email_token.php")
    suspend fun verifyEmailToken(
        @Body request: VerifyEmailTokenRequest,
        @Header("X-API-Key") apiKey: String? = null
    ): Response<AuthResponse>
}
