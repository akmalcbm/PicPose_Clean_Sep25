package com.picpose.bestphotographyapp.presentation.viewmodels

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facebook.AccessToken
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.picpose.bestphotographyapp.data.datastore.UserSessionManager
import com.picpose.bestphotographyapp.data.models.User
import com.picpose.bestphotographyapp.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Authentication state
 */
sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Success(val user: User) : AuthState()
    data class Error(val message: String) : AuthState()
}

/**
 * ViewModel for authentication operations
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userSessionManager: UserSessionManager
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    val isLoggedIn: StateFlow<Boolean> = userSessionManager.isLoggedIn
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val currentUser: StateFlow<User?> = combine(
        userSessionManager.userId,
        userSessionManager.userEmail,
        userSessionManager.userName,
        userSessionManager.userProfilePicture
    ) { id, email, name, profilePicture ->
        if (id != null && email != null && name != null) {
            User(
                id = id,
                email = email,
                name = name,
                profilePicture = profilePicture
            )
        } else {
            null
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * Login with email and password
     */
    fun login(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = authRepository.login(email, password)
            _authState.value = if (result.isSuccess) {
                AuthState.Success(result.getOrNull()!!)
            } else {
                AuthState.Error(result.exceptionOrNull()?.message ?: "Login failed")
            }
        }
    }

    /**
     * Register new user
     */
    fun register(email: String, password: String, name: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = authRepository.register(email, password, name)
            _authState.value = if (result.isSuccess) {
                AuthState.Success(result.getOrNull()!!)
            } else {
                AuthState.Error(result.exceptionOrNull()?.message ?: "Registration failed")
            }
        }
    }

    /**
     * Sign in with Google
     */
    fun signInWithGoogle(account: GoogleSignInAccount) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = authRepository.signInWithGoogle(account)
            _authState.value = if (result.isSuccess) {
                AuthState.Success(result.getOrNull()!!)
            } else {
                AuthState.Error(result.exceptionOrNull()?.message ?: "Google sign-in failed")
            }
        }
    }

    /**
     * Sign in with Facebook
     */
    fun signInWithFacebook(token: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = authRepository.signInWithFacebook(token)
            _authState.value = if (result.isSuccess) {
                AuthState.Success(result.getOrNull()!!)
            } else {
                AuthState.Error(result.exceptionOrNull()?.message ?: "Facebook sign-in failed")
            }
        }
    }

    /**
     * Logout
     */
    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _authState.value = AuthState.Idle
        }
    }

    /**
     * Reset auth state
     */
    fun resetAuthState() {
        _authState.value = AuthState.Idle
    }

    /**
     * Get Google Sign-In client
     */
    fun getGoogleSignInClient(activity: Activity) = authRepository.getGoogleSignInClient()
}
