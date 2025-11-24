package com.picpose.bestphotographyapp.data.models

import com.google.gson.annotations.SerializedName

/**
 * Represents a user in the system.
 * Supports multiple account types and roles.
 */
data class User(
    @SerializedName("id") val id: String,
    @SerializedName("email") val email: String,

    @SerializedName("name")
    val name: String? = null,

    @SerializedName("username")
    val username: String? = null, // backend support

    @SerializedName("profile_picture")
    val profilePicture: String? = null,

    @SerializedName("profile_pic")
    val profilePic: String? = null, // backend support


    @SerializedName("followers_count") val followersCount: Int = 0,
    @SerializedName("following_count") val followingCount: Int = 0,
    @SerializedName("posts_count") val postsCount: Int = 0,
    @SerializedName("bio") val bio: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,

    // 🔹 New Fields
    @SerializedName("account_type") val accountType: AccountType = AccountType.NORMAL,
    @SerializedName("role") val role: UserRole = UserRole.USER

)

{
    val displayName: String
        get() = name ?: username ?: "Guest User"

    val displayProfilePicture: String?
        get() = when {
            !profilePicture.isNullOrBlank() && profilePicture!!.startsWith("http") -> profilePicture
            !profilePicture.isNullOrBlank() -> "https://picpose.iamakmal.in/" + profilePicture
            !profilePic.isNullOrBlank() && profilePic!!.startsWith("http") -> profilePic
            !profilePic.isNullOrBlank() -> "https://picpose.iamakmal.in/" + profilePic
            else -> null
        }

}

/**
 * Enum representing user account type (used for monetization)
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
 * Enum representing user role type (permissions/identity)
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
    @SerializedName("name") val name: String,
    @SerializedName("role") val role: String = "user",
    @SerializedName("account_type") val accountType: String = "normal"
)

/**
 * Auth response model
 */
data class AuthResponse(
    @SerializedName("success") val success: Boolean? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("message") val message: String?,
    @SerializedName("user") val user: User?,
    @SerializedName("token") val token: String?
) {
    fun isSuccessful(): Boolean {
        return when {
            status != null -> status.equals("success", ignoreCase = true)
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
    val profilePicture: String?,
    val role: String = "user",
    val accountType: String = "normal"
)
