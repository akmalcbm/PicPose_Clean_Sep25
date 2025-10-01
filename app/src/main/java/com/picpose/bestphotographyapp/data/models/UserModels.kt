package com.picpose.bestphotographyapp.data.models

import com.google.gson.annotations.SerializedName

/**
 * User data model
 */
data class User(
    @SerializedName("id") val id: String,
    @SerializedName("email") val email: String,
    @SerializedName("name") val name: String,
    @SerializedName("profile_picture") val profilePicture: String? = null,
    @SerializedName("followers_count") val followersCount: Int = 0,
    @SerializedName("following_count") val followingCount: Int = 0,
    @SerializedName("posts_count") val postsCount: Int = 0,
    @SerializedName("bio") val bio: String? = null,
    @SerializedName("created_at") val createdAt: String? = null
)

/**
 * Login request model
 */
data class LoginRequest(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String
)

/**
 * Register request model
 */
data class RegisterRequest(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String,
    @SerializedName("name") val name: String
)

/**
 * Auth response model
 * Supports both old format (success: boolean) and new format (status: string)
 */
data class AuthResponse(
    @SerializedName("success") val success: Boolean? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("message") val message: String?,
    @SerializedName("user") val user: User?,
    @SerializedName("token") val token: String?
) {
    /**
     * Check if response is successful
     * Supports both formats:
     * - Old: {"success": true}
     * - New: {"status": "success"}
     */
    fun isSuccessful(): Boolean {
        return when {
            status != null -> status == "success"
            success != null -> success
            else -> false
        }
    }
}

/**
 * Social auth data model
 */
data class SocialAuthData(
    val provider: String, // "google", "facebook", "twitter"
    val token: String,
    val email: String,
    val name: String,
    val profilePicture: String?
)
