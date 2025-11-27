package com.picpose.bestphotographyapp.presentation.viewmodels

import android.content.Context
import androidx.credentials.GetCredentialResponse
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.picpose.bestphotographyapp.auth.GoogleAuthUiClient
import com.picpose.bestphotographyapp.auth.GoogleUserData
import com.picpose.bestphotographyapp.data.datastore.UserSessionManager
import com.picpose.bestphotographyapp.data.models.AccountType
import com.picpose.bestphotographyapp.data.models.User
import com.picpose.bestphotographyapp.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ---------------------------
// Auth State
// ---------------------------
sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Success(val user: User) : AuthState()
    data class Error(val message: String) : AuthState()
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userSessionManager: UserSessionManager
) : ViewModel() {

    // ---------------------------------------------------------
    // Modern Google Sign-In (Credential Manager)
    // ---------------------------------------------------------
    private var googleClient: GoogleAuthUiClient? = null

    fun initGoogleClient(context: Context) {
        googleClient = GoogleAuthUiClient(context)
    }

    /** Step 1 — Launch Google Sign-In (returns AndroidX Credential response) */
    suspend fun startGoogleSignIn(): Result<GetCredentialResponse?> {
        return try {
            val response = googleClient?.signIn() // googleClient should return androidx.credentials.GetCredentialResponse?
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Step 2 — Handle Google credential result (androidx.credentials.GetCredentialResponse)
     *
     * Notes:
     * - `response` must be the same type returned by startGoogleSignIn() (androidx.credentials.GetCredentialResponse?)
     * - `googleClient.parseGoogleCredential` should accept androidx.credentials.GetCredentialResponse?
     */
    suspend fun finishGoogleSignIn(
        response: GetCredentialResponse?,
        onResult: (Result<User>) -> Unit
    ) {
        // Parse credential safely
        val googleData: GoogleUserData? = try {
            googleClient?.parseGoogleCredential(response)
        } catch (e: Exception) {
            null
        }

        if (googleData == null) {
            onResult(Result.failure(Exception("Unable to sign in with Google (no credential)")))
            return
        }

        // Proceed to backend login
        viewModelScope.launch {
            _authState.value = AuthState.Loading

            val result = try {
                authRepository.signInWithGoogleIdToken(
                    idToken = googleData.idToken ?: "",
                    email = googleData.email ?: "",
                    name = googleData.displayName ?: "",
                    profilePicture = googleData.profilePictureUrl
                )
            } catch (e: Exception) {
                Result.failure<User>(e)
            }

            if (result.isSuccess) {
                val user = result.getOrNull()!!

                // Save user session — use named parameters so the call fails at compile time if your manager signature differs.
                userSessionManager.saveUserSession(
                    userId = user.id,
                    email = user.email,
                    name = user.displayName,
                    profilePicture = user.displayProfilePicture,
                    bio = user.bio // remove this named arg if your saveUserSession doesn't accept bio
                )

                _authState.value = AuthState.Success(user)
                onResult(Result.success(user))
            } else {
                val msg = result.exceptionOrNull()?.message ?: "Google login failed"
                _authState.value = AuthState.Error(msg)
                onResult(Result.failure(Exception(msg)))
            }
        }
    }

    // --------------------------
    // AuthState Flow
    // --------------------------
    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    // --------------------------
    // Logged-in / Skip flags
    // --------------------------
    val isLoggedIn: StateFlow<Boolean> =
        userSessionManager.isLoggedIn.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val hasSkippedAuth: StateFlow<Boolean> =
        userSessionManager.hasSkippedAuth.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // --------------------------
    // Current User Flow
    // --------------------------
    val currentUser: StateFlow<User?> = combine(
        userSessionManager.userId,
        userSessionManager.userEmail,
        userSessionManager.userName,
        userSessionManager.userProfilePicture,
        userSessionManager.userBio
    ) { id, email, name, profilePicture, bio ->

        if (!id.isNullOrBlank() && !email.isNullOrBlank() && !name.isNullOrBlank()) {
            User(
                id = id,
                email = email,
                name = name,
                profilePicture = profilePicture,
                bio = bio
            )
        } else null

    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _authEvents = MutableSharedFlow<AuthState>()
    val authEvents = _authEvents.asSharedFlow()

    // --------------------------
    // Email + Password Login
    // --------------------------
    fun login(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = authRepository.login(email, password)

            if (result.isSuccess) {
                val user = result.getOrNull()!!
                _authState.value = AuthState.Success(user)
                _authEvents.emit(AuthState.Success(user))
            } else {
                _authState.value =
                    AuthState.Error(result.exceptionOrNull()?.message ?: "Login failed")
            }
        }
    }

    fun register(email: String, password: String, name: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = authRepository.register(email, password, name)

            if (result.isSuccess) {
                val user = result.getOrNull()!!
                _authState.value = AuthState.Success(user)
                _authEvents.emit(AuthState.Success(user))
            } else {
                _authState.value =
                    AuthState.Error(result.exceptionOrNull()?.message ?: "Registration failed")
            }
        }
    }

    // --------------------------
    // Facebook Login
    // --------------------------
    fun signInWithFacebook(token: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = authRepository.signInWithFacebook(token)

            if (result.isSuccess) {
                val user = result.getOrNull()!!
                _authState.value = AuthState.Success(user)
                _authEvents.emit(AuthState.Success(user))
            } else {
                _authState.value =
                    AuthState.Error(result.exceptionOrNull()?.message ?: "Facebook login failed")
            }
        }
    }

    // --------------------------
    // Logout
    // --------------------------
    fun logout(onDone: (() -> Unit)? = null) {
        viewModelScope.launch {
            authRepository.logout()
            userSessionManager.setSkipAuth(false)
            _authState.value = AuthState.Idle
            onDone?.invoke()
        }
    }

    // --------------------------
    // Skip Auth
    // --------------------------
    fun skipAuth() {
        viewModelScope.launch { userSessionManager.setSkipAuth(true) }
    }

    fun resetSkip() {
        viewModelScope.launch { userSessionManager.setSkipAuth(false) }
    }

    fun resetAuthState() {
        _authState.value = AuthState.Idle
    }

    // --------------------------
    // Load Profile
    // --------------------------
    fun refreshUserFromSession() {
        viewModelScope.launch {
            val id = userSessionManager.userId.firstOrNull()
            val email = userSessionManager.userEmail.firstOrNull()
            val name = userSessionManager.userName.firstOrNull()
            val pic = userSessionManager.userProfilePicture.firstOrNull()

            if (!id.isNullOrEmpty() && !email.isNullOrEmpty() && !name.isNullOrEmpty()) {
                _authState.value =
                    AuthState.Success(User(id, email, name, profilePicture = pic))
            }
        }
    }

    fun fetchCurrentUser() {
        viewModelScope.launch {
            _authState.value = AuthState.Loading

            val userId = userSessionManager.userId.firstOrNull()
            if (userId == null) {
                _authState.value = AuthState.Error("User not logged in")
                return@launch
            }

            val result = authRepository.getUserProfile(userId)

            if (result.isSuccess) {
                val user = result.getOrNull()!!
                userSessionManager.saveUserSession(
                    userId = user.id,
                    email = user.email,
                    name = user.displayName,
                    bio = user.bio,
                    profilePicture = user.displayProfilePicture
                )
                _authState.value = AuthState.Success(user)
            } else {
                refreshUserFromSession()
            }
        }
    }

    // --------------------------
    // Update Profile
    // --------------------------
    fun updateProfile(
        name: String,
        bio: String?,
        profilePictureUri: Uri?,
        accountType: AccountType,
        onResult: (Result<User>) -> Unit
    ) {
        viewModelScope.launch {
            val result =
                authRepository.updateProfile(name, bio, profilePictureUri, accountType)

            if (result.isSuccess) {
                refreshUserSession(result.getOrNull()!!)
            }

            onResult(result)
        }
    }

    private fun refreshUserSession(updatedUser: User) {
        viewModelScope.launch {
            userSessionManager.saveUserSession(
                userId = updatedUser.id,
                email = updatedUser.email,
                name = updatedUser.displayName,
                profilePicture = updatedUser.displayProfilePicture,
                bio = updatedUser.bio
            )
        }
    }
}
