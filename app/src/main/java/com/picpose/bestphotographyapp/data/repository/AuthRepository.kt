package com.picpose.bestphotographyapp.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.net.toFile
import com.google.firebase.auth.FacebookAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.picpose.bestphotographyapp.data.datastore.UserSessionManager
import com.picpose.bestphotographyapp.data.models.*
import com.picpose.bestphotographyapp.data.network.RetrofitClient
import com.picpose.bestphotographyapp.data.network.UserApiService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userSessionManager: UserSessionManager
) {

    private val TAG = "AuthRepository"
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
    private val userApi: UserApiService =
        RetrofitClient.createService(UserApiService::class.java)

    private val API_KEY = RetrofitClient.defaultApiKey

    // ---------------------------------------------------------
    // 1. LOGIN VIA EMAIL/PASSWORD
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

                userSessionManager.saveUserSession(
                    userId = user.id,
                    email = user.email,
                    name = user.displayName,
                    profilePicture = user.displayProfilePicture,
                    bio = user.bio,
                    token = body.token
                )

                return Result.success(user)
            }

            val message = safeServerError(response.errorBody()?.string(), body?.message)
            Result.failure(Exception(message))

        } catch (e: Exception) {
            Log.e(TAG, "Login exception: ${e.message}")
            Result.failure(e)
        }
    }

    // ---------------------------------------------------------
    // 2. REGISTER
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
                userSessionManager.saveUserSession(
                    userId = user.id,
                    email = user.email,
                    name = user.displayName,
                    profilePicture = user.displayProfilePicture,
                    bio = user.bio,
                    token = body.token
                )
                return Result.success(user)
            }

            val message = safeServerError(response.errorBody()?.string(), body?.message)
            Result.failure(Exception(message))

        } catch (e: Exception) {
            Log.e(TAG, "Register exception: ${e.message}")
            Result.failure(e)
        }
    }

    // ---------------------------------------------------------
    // 3. GOOGLE LOGIN (CREDENTIAL MANAGER → BACKEND LOGIN)
    // ---------------------------------------------------------
    suspend fun signInWithGoogleIdToken(
        idToken: String,
        email: String,
        name: String,
        profilePicture: String?
    ): Result<User> {

        val payload = SocialAuthData(
            provider = "google",
            token = idToken,
            email = email,
            name = name,
            profilePicture = profilePicture
        )

        return try {
            val response = userApi.socialLogin(
                data = payload,
                apiKey = API_KEY
            )

            val body = response.body()

            if (response.isSuccessful && body?.isSuccessful() == true && body.user != null) {
                // save session
                val serverUser = body.user!!
                userSessionManager.saveUserSession(
                    userId = serverUser.id,
                    email = serverUser.email,
                    name = serverUser.displayName,
                    profilePicture = serverUser.displayProfilePicture,
                    bio = serverUser.bio,
                    token = body.token
                )
                return Result.success(serverUser)
            }

            val msg = safeServerError(response.errorBody()?.string(), body?.message)
            Result.failure(Exception(msg))

        } catch (e: Exception) {
            Log.e(TAG, "Google social login error: ${e.message}")
            Result.failure(e)
        }
    }


    // ---------------------------------------------------------
    // 4. FACEBOOK LOGIN THROUGH FIREBASE
    // ---------------------------------------------------------
    suspend fun signInWithFacebook(token: String): Result<User> {
        return try {
            val credential = FacebookAuthProvider.getCredential(token)
            val authResult = firebaseAuth.signInWithCredential(credential).await()
            val fb = authResult.user ?: return Result.failure(Exception("Facebook user null"))

            val user = User(
                id = fb.uid,
                email = fb.email ?: "",
                name = fb.displayName ?: "",
                profilePicture = fb.photoUrl?.toString()
            )

            userSessionManager.saveUserSession(
                userId = user.id,
                email = user.email,
                name = user.displayName,
                profilePicture = user.displayProfilePicture,
                bio = user.bio
            )

            Result.success(user)

        } catch (e: Exception) {
            Log.e(TAG, "Facebook login exception: ${e.message}")
            Result.failure(e)
        }
    }

    // ---------------------------------------------------------
    // 4b. TWITTER LOGIN (send token to server for app JWT)
    // ---------------------------------------------------------
    suspend fun signInWithTwitter(token: String): Result<User> {
        return try {
            val payload = SocialAuthData(
                provider = "twitter",
                token = token,
                email = "",
                name = "",
                profilePicture = null
            )

            val response = userApi.socialLogin(
                data = payload,
                apiKey = API_KEY
            )

            val body = response.body()
            if (response.isSuccessful && body?.isSuccessful() == true && body.user != null) {
                val serverUser = body.user!!
                userSessionManager.saveUserSession(
                    userId = serverUser.id,
                    email = serverUser.email,
                    name = serverUser.displayName,
                    profilePicture = serverUser.displayProfilePicture,
                    bio = serverUser.bio,
                    token = body.token
                )
                return Result.success(serverUser)
            }

            val msg = safeServerError(response.errorBody()?.string(), body?.message)
            Result.failure(Exception(msg))

        } catch (e: Exception) {
            Log.e(TAG, "Twitter social login error: ${e.message}")
            Result.failure(e)
        }
    }

    // ---------------------------------------------------------
    // 5. LOGOUT
    // ---------------------------------------------------------
    suspend fun logout() {
        try {
            firebaseAuth.signOut()
            userSessionManager.clearUserSession()
            Log.d(TAG, "Logout successful")
        } catch (e: Exception) {
            Log.e(TAG, "Logout exception: ${e.message}")
        }
    }

    // ---------------------------------------------------------
    // 6. FETCH PROFILE FROM SERVER
    // ---------------------------------------------------------
    suspend fun getUserProfile(userId: String): Result<User> {
        return try {
            val response = userApi.getUserProfile(userId, API_KEY)
            val body = response.body()

            if (response.isSuccessful && body?.isSuccessful() == true && body.user != null) {
                return Result.success(body.user!!)
            }

            Result.failure(Exception(body?.message ?: "Profile fetch error"))

        } catch (e: Exception) {
            Log.e(TAG, "Profile fetch error: ${e.message}")
            Result.failure(e)
        }
    }

    // ---------------------------------------------------------
    // 7. UPDATE PROFILE (TEXT + OPTIONAL PHOTO)
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

            // Prepare text fields
            val userIdPart = userId.toRequestBody("text/plain".toMediaTypeOrNull())
            val namePart = name.toRequestBody("text/plain".toMediaTypeOrNull())
            val bioPart = (bio ?: "").toRequestBody("text/plain".toMediaTypeOrNull())
            val accountTypePart =
                accountType.name.lowercase().toRequestBody("text/plain".toMediaTypeOrNull())
            val apiKeyPart = (API_KEY ?: "")
                .toRequestBody("text/plain".toMediaTypeOrNull())

            // Optional file part
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
                val user = body.user

                userSessionManager.saveUserSession(
                    userId = user.id,
                    email = user.email,
                    name = user.displayName,
                    profilePicture = user.displayProfilePicture,
                    bio = user.bio
                )

                return Result.success(user)
            }

            val msg = safeServerError(response.errorBody()?.string(), body?.message)
            Result.failure(Exception(msg))

        } catch (e: Exception) {
            Log.e(TAG, "Profile update exception: ${e.message}")
            Result.failure(e)
        }
    }

    // ---------------------------------------------------------
    // HELPERS
    // ---------------------------------------------------------
    private fun safeServerError(errorJson: String?, fallback: String?): String {
        return try {
            if (errorJson == null) fallback ?: "Unknown error"
            else JSONObject(errorJson).optString("message", fallback ?: "Unknown error")
        } catch (e: Exception) {
            fallback ?: "Server error"
        }
    }

    private suspend fun safeFileFromUri(uri: Uri): File {
        return withContext(Dispatchers.IO) {
            try {
                uri.toFile()
            } catch (e: Exception) {
                val input = context.contentResolver.openInputStream(uri)
                val file = kotlin.io.path.createTempFile("upload_", ".jpg").toFile()
                input?.use { it.copyTo(file.outputStream()) }
                file
            }
        }
    }
}
