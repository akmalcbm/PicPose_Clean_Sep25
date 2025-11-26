package com.picpose.bestphotographyapp.data.models

import com.google.gson.annotations.SerializedName

/**
 * Represents a user in the system.
 */
data class User(

    @SerializedName("id")
    val id: String,

    @SerializedName("email")
    val email: String,

    // Backend may return `name` OR `display_name`
    @SerializedName("name")
    val name: String? = null,

    @SerializedName("display_name")
    val displayNameBackend: String? = null,

    @SerializedName("username")
    val username: String? = null,

    // Profile picture (2 possible keys)
    @SerializedName("profile_picture")
    val profilePicture: String? = null,

    @SerializedName("profile_pic")
    val profilePic: String? = null,

    // Social stats
    @SerializedName("followers_count")
    val followersCount: Int = 0,

    @SerializedName("following_count")
    val followingCount: Int = 0,

    @SerializedName("posts_count")
    val postsCount: Int = 0,

    @SerializedName("bio")
    val bio: String? = null,

    @SerializedName("created_at")
    val createdAt: String? = null,

    @SerializedName("account_type")
    val accountType: AccountType = AccountType.NORMAL,

    @SerializedName("role")
    val role: UserRole = UserRole.USER
) {

    /**
     * Final Display Name Helper
     */
    val displayName: String
        get() = when {
            !name.isNullOrBlank() -> name
            !displayNameBackend.isNullOrBlank() -> displayNameBackend
            !username.isNullOrBlank() -> username
            else -> "Guest User"
        }

    /**
     * Return fully qualified profile picture URL
     */
    val displayProfilePicture: String?
        get() {
            val base = "https://picpose.iamakmal.in/"
            val pic = when {
                !profilePicture.isNullOrBlank() -> profilePicture
                !profilePic.isNullOrBlank() -> profilePic
                else -> return null
            }

            return if (pic.startsWith("http")) pic else base + pic.removePrefix("/")
        }
}

/**
 * Account types
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
 * User roles
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
 * Login Request
 */
data class LoginRequest(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String
)

/**
 * Register Request – BACKEND COMPATIBLE
 */
data class RegisterRequest(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String,
    @SerializedName("name") val name: String
)

/**
 * Auth Response from server (compatible with all PHP formats)
 */
data class AuthResponse(
    @SerializedName("status") val status: String? = null,      // "success" or "error"
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
 * Social Auth Payload
 */
data class SocialAuthData(
    val provider: String,
    val token: String,
    val email: String,
    val name: String,
    val profilePicture: String?,
    val role: String = "user",
    val accountType: String = "normal"
)
