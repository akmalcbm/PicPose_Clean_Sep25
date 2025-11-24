package com.picpose.bestphotographyapp.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.net.toFile
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN
import com.google.firebase.auth.FacebookAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.picpose.bestphotographyapp.R
import com.picpose.bestphotographyapp.data.datastore.UserSessionManager
import com.picpose.bestphotographyapp.data.models.AccountType
import com.picpose.bestphotographyapp.data.models.LoginRequest
import com.picpose.bestphotographyapp.data.models.RegisterRequest
import com.picpose.bestphotographyapp.data.models.User
import com.picpose.bestphotographyapp.data.network.RetrofitClient
import com.picpose.bestphotographyapp.data.network.UserApiService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.Dispatchers

@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userSessionManager: UserSessionManager
) {
    private val TAG = "AuthRepository"
    private val userApiService: UserApiService = RetrofitClient.createService(UserApiService::class.java)
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()

    // Prefer configuring API_KEY in RetrofitClient or via BuildConfig.
    private val API_KEY: String? = RetrofitClient.defaultApiKey // null-safe

    /**
     * Login using server endpoint. On success saves session to DataStore.
     */
    suspend fun login(email: String, password: String): Result<User> {
        return try {
            val response = userApiService.login(
                action = "login",
                request = LoginRequest(email, password),
                apiKey = API_KEY
            )

            val body = response.body()
            if (response.isSuccessful && body?.isSuccessful() == true && body.user != null) {
                val user = body.user
                // Save session (token may be null)
                userSessionManager.saveUserSession(
                    userId = user.id,
                    email = user.email,
                    name = user.displayName,
                    profilePicture = user.displayProfilePicture,
                    token = body.token
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
     * Register new user on server then save session.
     */
    suspend fun register(email: String, password: String, name: String): Result<User> {
        return try {
            val response = userApiService.register(
                action = "register",
                request = RegisterRequest(email = email, password = password, name = name),
                apiKey = API_KEY
            )

            val body = response.body()
            if (response.isSuccessful && body?.isSuccessful() == true && body.user != null) {
                val user = body.user
                userSessionManager.saveUserSession(
                    userId = user.id,
                    email = user.email,
                    name = user.displayName,
                    profilePicture = user.displayProfilePicture,
                    token = body.token
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
     * Return a GoogleSignInClient.
     * - Attempts to read client id from resources (R.string.google_client_id)
     * - Fallback: if not configured, uses DEFAULT_SIGN_IN with requestEmail only.
     *
     * NOTE: replace R.string.google_client_id with your actual client id in strings.xml.
     */
    fun getGoogleSignInClient(): GoogleSignInClient {
        val clientId = try {
            context.getString(R.string.google_client_id)
        } catch (_: Exception) {
            null
        }

        val builder = GoogleSignInOptions.Builder(DEFAULT_SIGN_IN)
            .requestEmail()

        if (!clientId.isNullOrBlank()) {
            builder.requestIdToken(clientId)
        }

        val gso = builder.build()
        return GoogleSignIn.getClient(context, gso)
    }

    /**
     * Sign in with Google using Firebase (existing behavior).
     */
    suspend fun signInWithGoogle(account: com.google.android.gms.auth.api.signin.GoogleSignInAccount): Result<User> {
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
                // Save session locally (we are not sending social login to your server automatically here)
                userSessionManager.saveUserSession(
                    userId = user.id,
                    email = user.email,
                    name = user.displayName,
                    profilePicture = user.displayProfilePicture
                )
                Result.success(user)
            } else {
                Result.failure(Exception("Firebase user is null"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Google sign-in exception: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Sign in with Facebook via Firebase
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
                    name = user.displayName,
                    profilePicture = user.displayProfilePicture
                )
                Result.success(user)
            } else {
                Result.failure(Exception("Firebase user is null"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Facebook sign-in exception: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Logout locally and from Firebase/Google.
     */
    suspend fun logout() {
        try {
            firebaseAuth.signOut()
            // Sign out Google if possible
            try {
                getGoogleSignInClient().signOut().await()
            } catch (_: Exception) {}
            userSessionManager.clearUserSession()
            Log.d(TAG, "Logout successful")
        } catch (e: Exception) {
            Log.e(TAG, "Logout exception: ${e.message}")
        }
    }

    /**
     * Fetch profile from server (if you want up-to-date server profile).
     */
    suspend fun getUserProfile(userId: String): Result<User> {
        return try {
            val response = userApiService.getUserProfile(userId = userId, apiKey = API_KEY)
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
     * Shows detailed server error messages (e.g. invalid file type)
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

            val userIdPart = userId.toRequestBody("text/plain".toMediaTypeOrNull())
            val namePart = name.toRequestBody("text/plain".toMediaTypeOrNull())
            val bioPart = (bio ?: "").toRequestBody("text/plain".toMediaTypeOrNull())
            val accountTypePart = accountType.name.lowercase().toRequestBody("text/plain".toMediaTypeOrNull())

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

            val response = userApiService.updateProfile(
                userId = userIdPart,
                name = namePart,
                bio = bioPart,
                accountType = accountTypePart,
                profile_picture = imagePart,
                apiKey = API_KEY
            )

            val body = response.body()

            // SUCCESS
            if (response.isSuccessful && body?.status == "success" && body.user != null) {
                val updatedUser = body.user

                userSessionManager.saveUserSession(
                    userId = updatedUser.id,
                    email = updatedUser.email,
                    name = updatedUser.displayName,
                    profilePicture = updatedUser.displayProfilePicture
                )

                return Result.success(updatedUser)
            }

            // ❌ ERROR — Extract real message from errorBody
            val errorMsg = try {
                response.errorBody()?.string()
                    ?.let { json ->
                        org.json.JSONObject(json).optString("message")
                    }
                    ?.takeIf { it.isNotEmpty() }
            } catch (e: Exception) {
                null
            }

            Result.failure(Exception(errorMsg ?: body?.message ?: "Profile update failed"))

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

}
