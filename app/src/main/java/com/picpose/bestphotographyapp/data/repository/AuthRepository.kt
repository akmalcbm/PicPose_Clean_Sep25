package com.picpose.bestphotographyapp.data.repository

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FacebookAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.picpose.bestphotographyapp.data.datastore.UserSessionManager
import com.picpose.bestphotographyapp.data.models.LoginRequest
import com.picpose.bestphotographyapp.data.models.RegisterRequest
import com.picpose.bestphotographyapp.data.models.User
import com.picpose.bestphotographyapp.data.network.RetrofitClient
import com.picpose.bestphotographyapp.data.network.UserApiService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for authentication operations
 */
@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userSessionManager: UserSessionManager
) {
    private val TAG = "AuthRepository"
    private val userApiService: UserApiService = RetrofitClient.createService(UserApiService::class.java)
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()

    /**
     * Manual login with email and password via backend API
     */
    suspend fun login(email: String, password: String): Result<User> {
        return try {
            val response = userApiService.login(
                action = "login",
                request = LoginRequest(email, password)
            )

            if (response.isSuccessful && response.body()?.success == true) {
                val user = response.body()?.user
                val token = response.body()?.token
                if (user != null) {
                    // Save session
                    userSessionManager.saveUserSession(
                        userId = user.id,
                        email = user.email,
                        name = user.name,
                        profilePicture = user.profilePicture,
                        token = token
                    )
                    Log.d(TAG, "Login successful for user: ${user.email}")
                    Result.success(user)
                } else {
                    Result.failure(Exception("User data is null"))
                }
            } else {
                val errorMsg = response.body()?.message ?: "Login failed"
                Log.e(TAG, "Login failed: $errorMsg")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Login exception: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Register new user via backend API
     */
    suspend fun register(email: String, password: String, name: String): Result<User> {
        return try {
            val response = userApiService.register(
                action = "register",
                request = RegisterRequest(email, password, name)
            )

            if (response.isSuccessful && response.body()?.success == true) {
                val user = response.body()?.user
                val token = response.body()?.token
                if (user != null) {
                    // Save session
                    userSessionManager.saveUserSession(
                        userId = user.id,
                        email = user.email,
                        name = user.name,
                        profilePicture = user.profilePicture,
                        token = token
                    )
                    Log.d(TAG, "Registration successful for user: ${user.email}")
                    Result.success(user)
                } else {
                    Result.failure(Exception("User data is null"))
                }
            } else {
                val errorMsg = response.body()?.message ?: "Registration failed"
                Log.e(TAG, "Registration failed: $errorMsg")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Registration exception: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Google Sign-In
     */
    fun getGoogleSignInClient(): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("123456789000-abcdefghijklmnop.apps.googleusercontent.com") // Replace with your web client ID
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
                
                // Save session
                userSessionManager.saveUserSession(
                    userId = user.id,
                    email = user.email,
                    name = user.name,
                    profilePicture = user.profilePicture
                )
                Log.d(TAG, "Google sign-in successful for user: ${user.email}")
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
                
                // Save session
                userSessionManager.saveUserSession(
                    userId = user.id,
                    email = user.email,
                    name = user.name,
                    profilePicture = user.profilePicture
                )
                Log.d(TAG, "Facebook sign-in successful for user: ${user.email}")
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
     * Get user profile from API
     */
    suspend fun getUserProfile(userId: String): Result<User> {
        return try {
            val response = userApiService.getUserProfile(userId)

            if (response.isSuccessful && response.body()?.success == true) {
                val user = response.body()?.user
                if (user != null) {
                    Result.success(user)
                } else {
                    Result.failure(Exception("User data is null"))
                }
            } else {
                val errorMsg = response.body()?.message ?: "Failed to fetch user profile"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Get user profile exception: ${e.message}")
            Result.failure(e)
        }
    }
}
