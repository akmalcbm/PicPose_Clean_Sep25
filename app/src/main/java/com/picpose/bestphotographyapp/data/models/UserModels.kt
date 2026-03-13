/**
 * ---
 * File: UserModels.kt
 * Layer: Data
 * Project: PicPose
 *
 * Purpose:
 * Defines DTOs and domain-facing models that represent prompt, user, stats, or settings data.
 *
 * Interactions:
 * Works with nearby classes in the same layer to keep responsibilities separated and easier to maintain.
 *
 * Data Flow:
 * Feature-specific flow; see adjacent ViewModels and repositories for the full path.
 *
 * Maintainer Notes:
 * - Keep responsibilities narrow and update this header when the file grows into a larger abstraction.
 * ---
 */

package com.picpose.bestphotographyapp.data.models

import com.google.gson.annotations.SerializedName

/**
 * Represents a user returned from the backend.
 */
data class User(

    @SerializedName("id")
    val id: String,

    @SerializedName("email")
    val email: String,

    // Backend may return name OR display_name
    @SerializedName("name")
    val nameRaw: String? = null,

    @SerializedName("display_name")
    val displayNameBackend: String? = null,

    @SerializedName("username")
    val username: String? = null,

    // Social auth metadata
    @SerializedName("provider")
    val provider: String? = null,

    @SerializedName("social_id")
    val socialId: String? = null,

    @SerializedName("api_token")
    val apiToken: String? = null,

    // Profile picture support (2 key variations)
    @SerializedName("profile_picture")
    val profilePicture: String? = null,

    @SerializedName("profile_pic")
    val profilePic: String? = null,

    @SerializedName("bio")
    val bio: String? = null,

    @SerializedName("created_at")
    val createdAt: String? = null,

    @SerializedName("email_verified")
    val emailVerified: Int? = 0,

    @SerializedName("email_verified_at")
    val emailVerifiedAt: String? = null,

    @SerializedName("account_type")
    val accountType: AccountType = AccountType.NORMAL,

    @SerializedName("role")
    val role: UserRole = UserRole.USER
) {
    val isEmailVerified: Boolean
        get() = (emailVerified ?: 0) == 1

    /**
     * Unified safe display name
     */
    val displayName: String
        get() = when {
            !nameRaw.isNullOrBlank() -> nameRaw
            !displayNameBackend.isNullOrBlank() -> displayNameBackend
            !username.isNullOrBlank() -> username
            else -> "Guest User"
        }

    /**
     * Normalized profile picture URL
     */
    val displayProfilePicture: String?
        get() {
            val base = "https://picpose.iamakmal.in/"
            val raw = when {
                !profilePicture.isNullOrBlank() -> profilePicture
                !profilePic.isNullOrBlank() -> profilePic
                else -> return null
            }

            // Already a full link
            if (raw.startsWith("http")) return raw

            // Remove accidental double slashes
            val cleaned = raw.removePrefix("/")
            return base + cleaned
        }
}

/**
 * Account types (Matches database enum)
 */
enum class AccountType(val value: String) {
    @SerializedName("normal")
    NORMAL("normal"),

    @SerializedName("premium")
    PREMIUM("premium"),

    @SerializedName("ad_free")
    AD_FREE("ad_free");

    companion object {
        fun from(value: String?): AccountType {
            return when (value?.lowercase()) {
                "premium" -> PREMIUM
                "ad_free" -> AD_FREE
                else -> NORMAL
            }
        }
    }
}

/**
 * User roles used for admin/premium logic
 */
enum class UserRole(val value: String) {
    @SerializedName("user")
    USER("user"),

    @SerializedName("professional")
    PROFESSIONAL("professional"),

    @SerializedName("admin")
    ADMIN("admin");

    companion object {
        fun from(value: String?): UserRole {
            return when (value?.lowercase()) {
                "professional" -> PROFESSIONAL
                "admin" -> ADMIN
                else -> USER
            }
        }
    }
}

/**
 * Email/Password Login Request
 */
data class LoginRequest(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String
)

/**
 * Register Request
 */
data class RegisterRequest(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String,
    @SerializedName("name") val name: String
)

data class ForgotPasswordRequest(
    @SerializedName("email") val email: String
)

data class ResetPasswordRequest(
    @SerializedName("token") val token: String,
    @SerializedName("new_password") val newPassword: String
)

data class VerifyEmailTokenRequest(
    @SerializedName("token") val token: String
)

data class RequestEmailVerificationRequest(
    @SerializedName("user_id") val userId: String
)

/**
 * In-app account deletion request payload.
 */
data class DeleteAccountRequest(
    @SerializedName("user_id") val userId: String,
    @SerializedName("email") val email: String,
    @SerializedName("reason") val reason: String? = null
)

/**
 * Auth Response wrapper
 */
data class AuthResponse(
    @SerializedName("status") val status: String? = null,
    @SerializedName("success") val successFlag: Boolean? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("user") val user: User? = null,
    @SerializedName("token") val token: String? = null
) {
    fun isSuccessful(): Boolean {
        return when {
            status != null -> status.equals("success", true)
            successFlag != null -> successFlag == true
            else -> false
        }
    }
}

/**
 * Social Auth Payload — Unified for Google, Facebook, Twitter
 */
data class SocialAuthData(
    val provider: String,
    val token: String,

    // Social providers may not always return email/name
    val email: String? = null,
    val name: String? = null,

    val profilePicture: String? = null,

    val socialId: String? = null,   // ADD THIS

    // Default values supported by backend
    val role: String = "user",
    val accountType: String = "normal"
)
