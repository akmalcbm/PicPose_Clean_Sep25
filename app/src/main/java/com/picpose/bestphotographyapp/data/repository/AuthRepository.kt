package com.picpose.bestphotographyapp.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.net.toFile
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FacebookAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.picpose.bestphotographyapp.data.datastore.UserSessionManager
import com.picpose.bestphotographyapp.data.models.*
import com.picpose.bestphotographyapp.data.network.RetrofitClient
import com.picpose.bestphotographyapp.data.network.UserApiService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for authentication & user profile operations
 */
@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userSessionManager: UserSessionManager
) {
    private val TAG = "AuthRepository"
    private val userApiService: UserApiService =
        RetrofitClient.createService(UserApiService::class.java)
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()

    // 🔹 Common API Key constant
    private val API_KEY = "7a6f3c27a1b6d5e8e4c8a2b3f9e6d1f47c5b8a9d3e7f2c6a4b9e3d1c5f8a7b2c"

    /**
     * Manual login
     */
    suspend fun login(email: String, password: String): Result<User> {
        return try {
            val response = userApiService.login(
                action = "login",
                request = LoginRequest(email, password)
            )
            val body = response.body()
            if (response.isSuccessful && body?.isSuccessful() == true && body.user != null) {
                val user = body.user
                val token = body.token
                userSessionManager.saveUserSession(
                    userId = user.id,
                    email = user.email,
                    name = user.name,
                    profilePicture = user.profilePicture,
                    token = token
                )
                Result.success(user)
            } else {
                Result.failure(Exception(body?.message ?: "Login failed"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Login exception: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Register new user
     */
    suspend fun register(email: String, password: String, name: String): Result<User> {
        return try {
            val response = userApiService.register(
                action = "register",
                request = RegisterRequest(email, password, name)
            )
            val body = response.body()
            if (response.isSuccessful && body?.isSuccessful() == true && body.user != null) {
                val user = body.user
                val token = body.token
                userSessionManager.saveUserSession(
                    userId = user.id,
                    email = user.email,
                    name = user.name,
                    profilePicture = user.profilePicture,
                    token = token
                )
                Result.success(user)
            } else {
                Result.failure(Exception(body?.message ?: "Registration failed"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Register exception: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Google Sign-In
     */
    fun getGoogleSignInClient(): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("123456789000-abcdefghijklmnop.apps.googleusercontent.com")
            .requestEmail()
            .build()
        return GoogleSignIn.getClient(context, gso)
    }

    suspend fun signInWithGoogle(account: GoogleSignInAccount): Result<User> {
        return try {
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            val authResult = firebaseAuth.signInWithCredential(credential).await()
            val firebaseUser = authResult.user

            if (firebaseUser != null) {
                val user = User(
                    id = firebaseUser.uid,
                    email = firebaseUser.email ?: "",
                    name = firebaseUser.displayName ?: "",
                    profilePicture = firebaseUser.photoUrl?.toString()
                )
                userSessionManager.saveUserSession(
                    userId = user.id,
                    email = user.email,
                    name = user.name,
                    profilePicture = user.profilePicture
                )
                Result.success(user)
            } else Result.failure(Exception("Firebase user is null"))
        } catch (e: Exception) {
            Log.e(TAG, "Google sign-in exception: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Facebook Sign-In
     */
    suspend fun signInWithFacebook(token: String): Result<User> {
        return try {
            val credential = FacebookAuthProvider.getCredential(token)
            val authResult = firebaseAuth.signInWithCredential(credential).await()
            val firebaseUser = authResult.user

            if (firebaseUser != null) {
                val user = User(
                    id = firebaseUser.uid,
                    email = firebaseUser.email ?: "",
                    name = firebaseUser.displayName ?: "",
                    profilePicture = firebaseUser.photoUrl?.toString()
                )
                userSessionManager.saveUserSession(
                    userId = user.id,
                    email = user.email,
                    name = user.name,
                    profilePicture = user.profilePicture
                )
                Result.success(user)
            } else Result.failure(Exception("Firebase user is null"))
        } catch (e: Exception) {
            Log.e(TAG, "Facebook sign-in exception: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Logout
     */
    suspend fun logout() {
        try {
            firebaseAuth.signOut()
            getGoogleSignInClient().signOut().await()
            userSessionManager.clearUserSession()
            Log.d(TAG, "Logout successful")
        } catch (e: Exception) {
            Log.e(TAG, "Logout exception: ${e.message}")
        }
    }

    /**
     * Get user profile
     */
    suspend fun getUserProfile(userId: String): Result<User> {
        return try {
            val response = userApiService.getUserProfile(userId)
            val body = response.body()
            if (response.isSuccessful && body?.isSuccessful() == true && body.user != null) {
                Result.success(body.user)
            } else {
                Result.failure(Exception(body?.message ?: "Profile fetch failed"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Get profile exception: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * ✅ Update user profile (name, bio, photo, account type)
     */
    suspend fun updateProfile(
        name: String,
        bio: String?,
        profilePictureUri: Uri?,
        accountType: AccountType
    ): Result<User> {
        return try {
            val userId =
                userSessionManager.userId.firstOrNull() ?: return Result.failure(Exception("User not logged in"))

            // --- Multipart Body Parts ---
            val userIdPart = userId.toRequestBody("text/plain".toMediaTypeOrNull())
            val namePart = name.toRequestBody("text/plain".toMediaTypeOrNull())
            val bioPart = (bio ?: "").toRequestBody("text/plain".toMediaTypeOrNull())
            val accountTypePart = accountType.name.lowercase().toRequestBody("text/plain".toMediaTypeOrNull())

            // --- Optional image upload ---
            val imagePart = profilePictureUri?.let { uri ->
                val file = try {
                    uri.toFile()
                } catch (e: Exception) {
                    val input = context.contentResolver.openInputStream(uri)
                    val tempFile = kotlin.io.path.createTempFile("profile_", ".jpg").toFile()
                    input?.use { it.copyTo(tempFile.outputStream()) }
                    tempFile
                }
                val reqFile = file.asRequestBody("image/*".toMediaTypeOrNull())
                MultipartBody.Part.createFormData("profile_picture", file.name, reqFile)
            }

            // --- API call ---
            val response = userApiService.updateProfile(
                userId = userIdPart,
                name = namePart,
                bio = bioPart,
                accountType = accountTypePart,
                profile_picture = imagePart,
                apiKey = API_KEY
            )

            val body = response.body()
            if (response.isSuccessful && body?.status == "success" && body.user != null) {
                val updatedUser = body.user
                userSessionManager.saveUserSession(
                    userId = updatedUser.id,
                    email = updatedUser.email,
                    name = updatedUser.name,
                    profilePicture = updatedUser.profilePicture
                )
                Log.d(TAG, "✅ Profile updated successfully for user: ${updatedUser.email}")
                Result.success(updatedUser)
            } else {
                Log.e(TAG, "❌ Profile update failed: ${body?.message}")
                Result.failure(Exception(body?.message ?: "Profile update failed"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Profile update exception: ${e.message}")
            Result.failure(e)
        }
    }
}
