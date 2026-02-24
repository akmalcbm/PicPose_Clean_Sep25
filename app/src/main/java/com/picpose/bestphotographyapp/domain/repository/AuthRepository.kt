package com.picpose.bestphotographyapp.domain.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.net.toFile
import com.picpose.bestphotographyapp.core.crash.CrashReporter
import com.picpose.bestphotographyapp.data.datastore.UserSessionManager
import com.picpose.bestphotographyapp.data.models.AccountType
import com.picpose.bestphotographyapp.data.models.DeleteAccountRequest
import com.picpose.bestphotographyapp.data.models.ForgotPasswordRequest
import com.picpose.bestphotographyapp.data.models.LoginRequest
import com.picpose.bestphotographyapp.data.models.RequestEmailVerificationRequest
import com.picpose.bestphotographyapp.data.models.RegisterRequest
import com.picpose.bestphotographyapp.data.models.ResetPasswordRequest
import com.picpose.bestphotographyapp.data.models.SocialAuthData
import com.picpose.bestphotographyapp.data.models.User
import com.picpose.bestphotographyapp.data.models.VerifyEmailTokenRequest
import com.picpose.bestphotographyapp.data.network.RetrofitClient
import com.picpose.bestphotographyapp.data.network.UserApiService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.io.path.createTempFile

@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userSessionManager: UserSessionManager,
    private val crashReporter: CrashReporter
) {

    private val TAG = "AuthRepository"
    private val userApi: UserApiService =
        RetrofitClient.createService(UserApiService::class.java)

    private val API_KEY = RetrofitClient.defaultApiKey

    // ---------------------------------------------------------
    // EMAIL/PASSWORD LOGIN
    // ---------------------------------------------------------
    suspend fun login(email: String, password: String): Result<User> {
        return try {
            val response = userApi.login(
                action = "login",
                request = LoginRequest(email, password),
                apiKey = API_KEY
            )

            val body = response.body()

            if (response.isSuccessful && body?.isSuccessful() == true && body.user != null) {
                val user = body.user
                saveSession(user, body.token)
                Result.success(user)
            } else {
                Result.failure(Exception(safeServerError(response.errorBody()?.string(), body?.message)))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Login exception: ${e.message}")
            crashReporter.recordUnexpectedNetworkFailure("auth_login", e)
            Result.failure(e)
        }
    }

    // ---------------------------------------------------------
    // REGISTER
    // ---------------------------------------------------------
    suspend fun register(email: String, password: String, name: String): Result<User> {
        return try {
            val response = userApi.register(
                action = "register",
                request = RegisterRequest(email, password, name),
                apiKey = API_KEY
            )

            val body = response.body()

            if (response.isSuccessful && body?.isSuccessful() == true && body.user != null) {
                val user = body.user
                saveSession(user, body.token)
                Result.success(user)
            } else {
                Result.failure(Exception(safeServerError(response.errorBody()?.string(), body?.message)))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Register exception: ${e.message}")
            crashReporter.recordUnexpectedNetworkFailure("auth_register", e)
            Result.failure(e)
        }
    }

    // ---------------------------------------------------------
    // UNIFIED SOCIAL LOGIN (Google / Facebook / Twitter)
    // ---------------------------------------------------------
    suspend fun socialLogin(
        provider: String,
        token: String,
        email: String? = null,
        name: String? = null,
        profilePicture: String? = null
    ): Result<User> {

        val payload = SocialAuthData(
            provider = provider,
            token = token,
            email = email,
            name = name,
            profilePicture = profilePicture,
            socialId = email  // Google unique stable value

        )

        return try {
            val response = userApi.socialLogin(
                data = payload,
                apiKey = API_KEY
            )

            val body = response.body()

            if (response.isSuccessful && body?.status == "success" && body.user != null) {
                val user = body.user!!
                saveSession(user, body.token)
                Result.success(user)
            } else {
                Result.failure(Exception(safeServerError(response.errorBody()?.string(), body?.message)))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Social login error: ${e.message}")
            crashReporter.recordUnexpectedNetworkFailure("auth_social_login", e)
            Result.failure(e)
        }
    }

    // ---------------------------------------------------------
    // LOGOUT
    // ---------------------------------------------------------
    suspend fun logout() {
        try {
            userSessionManager.clearUserSession()
            Log.d(TAG, "Logout successful")
        } catch (e: Exception) {
            Log.e(TAG, "Logout exception: ${e.message}")
        }
    }

    // ---------------------------------------------------------
    // FETCH PROFILE
    // ---------------------------------------------------------
    suspend fun getUserProfile(userId: String): Result<User> {
        return try {
            val response = userApi.getUserProfile(userId, API_KEY)
            val body = response.body()

            if (response.isSuccessful && body?.isSuccessful() == true && body.user != null) {
                Result.success(body.user!!)
            } else {
                Result.failure(Exception(body?.message ?: "Profile fetch error"))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Profile fetch exception: ${e.message}")
            crashReporter.recordUnexpectedNetworkFailure("auth_profile_fetch", e)
            Result.failure(e)
        }
    }

    // ---------------------------------------------------------
    // UPDATE PROFILE (TEXT + IMAGE)
    // ---------------------------------------------------------
    suspend fun updateProfile(
        name: String,
        bio: String?,
        profilePictureUri: Uri?,
        accountType: AccountType
    ): Result<User> {

        return try {
            val userId = userSessionManager.userId.firstOrNull()
                ?: return Result.failure(Exception("User not logged in"))

            val userIdPart = userId.toRequestBody("text/plain".toMediaTypeOrNull())
            val namePart = name.toRequestBody("text/plain".toMediaTypeOrNull())
            val bioPart = (bio ?: "").toRequestBody("text/plain".toMediaTypeOrNull())
            val safeAccountType = runCatching { accountType.name.lowercase() }
                .getOrDefault(AccountType.NORMAL.value)
            val accountTypePart = safeAccountType.toRequestBody("text/plain".toMediaTypeOrNull())
            val apiKeyPart = (API_KEY ?: "")
                .toRequestBody("text/plain".toMediaTypeOrNull())

            val imagePart = profilePictureUri?.let { uri ->
                val file = safeFileFromUri(uri)
                MultipartBody.Part.createFormData(
                    "profile_picture",
                    file.name,
                    file.asRequestBody("image/*".toMediaTypeOrNull())
                )
            }

            val response = userApi.updateProfile(
                userId = userIdPart,
                name = namePart,
                bio = bioPart,
                accountType = accountTypePart,
                profile_picture = imagePart,
                apiKey = apiKeyPart
            )

            val body = response.body()

            if (response.isSuccessful && body?.status == "success" && body.user != null) {
                val updatedUser = body.user
                saveSession(updatedUser!!)
                Result.success(updatedUser)
            } else {
                Result.failure(Exception(safeServerError(response.errorBody()?.string(), body?.message)))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Profile update exception: ${e.message}")
            crashReporter.recordUnexpectedNetworkFailure("auth_profile_update", e)
            Result.failure(e)
        }
    }

    // ---------------------------------------------------------
    // DELETE ACCOUNT (IN-APP)
    // ---------------------------------------------------------
    suspend fun deleteAccount(reason: String = "user_requested_in_app"): Result<Unit> {
        return try {
            val userId = userSessionManager.userId.firstOrNull()
                ?: return Result.failure(Exception("User not logged in"))
            val email = userSessionManager.userEmail.firstOrNull()
                ?: return Result.failure(Exception("Missing user email"))

            val response = userApi.deleteAccount(
                action = "delete_account",
                request = DeleteAccountRequest(
                    userId = userId,
                    email = email,
                    reason = reason
                ),
                apiKey = API_KEY
            )

            val body = response.body()
            if (response.isSuccessful && body?.isSuccessful() == true) {
                userSessionManager.clearUserSession()
                Result.success(Unit)
            } else {
                Result.failure(Exception(safeServerError(response.errorBody()?.string(), body?.message)))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Delete account exception: ${e.message}")
            crashReporter.recordUnexpectedNetworkFailure("auth_delete_account", e)
            Result.failure(e)
        }
    }

    suspend fun requestPasswordReset(email: String): Result<String> {
        return try {
            val response = userApi.requestPasswordReset(
                request = ForgotPasswordRequest(email = email),
                apiKey = API_KEY
            )
            val body = response.body()
            if (response.isSuccessful) {
                Result.success(body?.message ?: "If an account exists, you'll receive instructions.")
            } else {
                Result.failure(Exception(safeServerError(response.errorBody()?.string(), body?.message)))
            }
        } catch (e: Exception) {
            crashReporter.recordUnexpectedNetworkFailure("auth_request_password_reset", e)
            Result.failure(e)
        }
    }

    suspend fun resetPassword(token: String, newPassword: String): Result<String> {
        return try {
            val response = userApi.resetPassword(
                request = ResetPasswordRequest(token = token, newPassword = newPassword),
                apiKey = API_KEY
            )
            val body = response.body()
            if (response.isSuccessful && body?.isSuccessful() == true) {
                Result.success(body.message ?: "Password reset successful.")
            } else {
                Result.failure(Exception(safeServerError(response.errorBody()?.string(), body?.message)))
            }
        } catch (e: Exception) {
            crashReporter.recordUnexpectedNetworkFailure("auth_reset_password", e)
            Result.failure(e)
        }
    }

    suspend fun requestEmailVerification(userId: String): Result<String> {
        return try {
            val response = userApi.requestEmailVerification(
                request = RequestEmailVerificationRequest(userId = userId),
                apiKey = API_KEY
            )
            val body = response.body()
            if (response.isSuccessful) {
                Result.success(body?.message ?: "Verification email sent.")
            } else {
                Result.failure(Exception(safeServerError(response.errorBody()?.string(), body?.message)))
            }
        } catch (e: Exception) {
            crashReporter.recordUnexpectedNetworkFailure("auth_request_email_verification", e)
            Result.failure(e)
        }
    }

    suspend fun verifyEmailToken(token: String): Result<String> {
        return try {
            val response = userApi.verifyEmailToken(
                request = VerifyEmailTokenRequest(token = token),
                apiKey = API_KEY
            )
            val body = response.body()
            if (response.isSuccessful && body?.isSuccessful() == true) {
                Result.success(body.message ?: "Email verified successfully.")
            } else {
                Result.failure(Exception(safeServerError(response.errorBody()?.string(), body?.message)))
            }
        } catch (e: Exception) {
            crashReporter.recordUnexpectedNetworkFailure("auth_verify_email_token", e)
            Result.failure(e)
        }
    }

    // ---------------------------------------------------------
    // HELPERS
    // ---------------------------------------------------------
    private suspend fun saveSession(user: User, token: String? = null) {
        userSessionManager.saveUserSession(
            userId = user.id,
            email = user.email,
                name = user.displayName,
                profilePicture = user.displayProfilePicture,
                bio = user.bio,
                token = token,
                emailVerified = user.isEmailVerified
            )
    }

    private fun safeServerError(raw: String?, fallback: String?): String {
        return try {
            if (raw == null) fallback ?: "Unknown error"
            else JSONObject(raw).optString("message", fallback ?: "Unknown error")
        } catch (e: Exception) {
            crashReporter.recordParsingFailure("auth_error_body", e)
            fallback ?: "Server error"
        }
    }

    private suspend fun safeFileFromUri(uri: Uri): File {
        return withContext(Dispatchers.IO) {
            try {
                uri.toFile()
            } catch (e: Exception) {
                runCatching {
                    val input = context.contentResolver.openInputStream(uri)
                    val file = createTempFile("upload_", ".jpg").toFile()
                    input?.use { it.copyTo(file.outputStream()) }
                    file
                }.getOrElse { fallbackError ->
                    crashReporter.recordImageUploadFailure("profile_image_prepare", fallbackError)
                    throw fallbackError
                }
            }
        }
    }
}
