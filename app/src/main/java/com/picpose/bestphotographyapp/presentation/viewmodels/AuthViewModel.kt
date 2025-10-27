package com.picpose.bestphotographyapp.presentation.viewmodels

import android.app.Activity
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.picpose.bestphotographyapp.data.datastore.UserSessionManager
import com.picpose.bestphotographyapp.data.models.AccountType
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

    private val _currentUser = MutableStateFlow<User?>(null)

    val isLoggedIn: StateFlow<Boolean> = userSessionManager.isLoggedIn
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _hasSkippedAuth = MutableStateFlow(false)
    val hasSkippedAuth: StateFlow<Boolean> = _hasSkippedAuth



    // 🧩 Combines user info from DataStore
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
        } else null
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * ✅ Login with email and password
     */
    fun login(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = authRepository.login(email, password)
            if (result.isSuccess) {
                _authState.value = AuthState.Success(result.getOrNull()!!)
                refreshUserFromSession() // 👈 ensures UI shows user immediately
            } else {
                _authState.value = AuthState.Error(result.exceptionOrNull()?.message ?: "Login failed")
            }
        }
    }

    /**
     * ✅ Register new user
     */
    fun register(email: String, password: String, name: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = authRepository.register(email, password, name)
            if (result.isSuccess) {
                _authState.value = AuthState.Success(result.getOrNull()!!)
                refreshUserFromSession() // 👈 ensures instant profile update
            } else {
                _authState.value = AuthState.Error(result.exceptionOrNull()?.message ?: "Registration failed")
            }
        }
    }

    /**
     * ✅ Sign in with Google
     */
    fun signInWithGoogle(account: GoogleSignInAccount) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = authRepository.signInWithGoogle(account)
            if (result.isSuccess) {
                _authState.value = AuthState.Success(result.getOrNull()!!)
                refreshUserFromSession() // 👈 for instant Google user display
            } else {
                _authState.value = AuthState.Error(result.exceptionOrNull()?.message ?: "Google sign-in failed")
            }
        }
    }

    /**
     * ✅ Sign in with Facebook
     */
    fun signInWithFacebook(token: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = authRepository.signInWithFacebook(token)
            if (result.isSuccess) {
                _authState.value = AuthState.Success(result.getOrNull()!!)
                refreshUserFromSession() // 👈 for instant Facebook user display
            } else {
                _authState.value = AuthState.Error(result.exceptionOrNull()?.message ?: "Facebook sign-in failed")
            }
        }
    }

    /**
     * ✅ Logout
     */
    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _authState.value = AuthState.Idle
        }
    }

    /**
     * ✅ Skip authentication (guest mode)
     */
    fun skipAuth() {
        _hasSkippedAuth.value = true
    }

    fun resetSkip() {
        _hasSkippedAuth.value = false
    }

    /**
     * ✅ Reset auth state
     */
    fun resetAuthState() {
        _authState.value = AuthState.Idle
    }

    /**
     * ✅ Refresh user info from session storage (DataStore)
     */
    fun refreshUserFromSession() {
        viewModelScope.launch {
            userSessionManager.userId.firstOrNull()?.let { id ->
                val email = userSessionManager.userEmail.firstOrNull()
                val name = userSessionManager.userName.firstOrNull()
                val pic = userSessionManager.userProfilePicture.firstOrNull()
                if (email != null && name != null) {
                    _authState.value = AuthState.Success(
                        User(id = id, email = email, name = name, profilePicture = pic)
                    )
                }
            }
        }
    }
    
    /**
     * ✅ Fetch current user from API (if needed to get fresh data)
     */
    fun fetchCurrentUser() {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            
            // First try to get user from session
            val userId = userSessionManager.userId.firstOrNull()
            if (userId != null) {
                // Try to fetch from API
                val result = authRepository.getUserProfile(userId)
                if (result.isSuccess) {
                    val user = result.getOrNull()!!
                    _authState.value = AuthState.Success(user)
                    // Update session with fresh data
                    userSessionManager.saveUserSession(
                        userId = user.id,
                        email = user.email,
                        name = user.name,
                        profilePicture = user.profilePicture
                    )
                } else {
                    // Fallback to session data
                    refreshUserFromSession()
                }
            } else {
                _authState.value = AuthState.Error("User not logged in")
            }
        }
    }

    fun refreshUserSession(updatedUser: User) {
        viewModelScope.launch {
            userSessionManager.saveUserSession(
                userId = updatedUser.id,
                email = updatedUser.email,
                name = updatedUser.name,
                profilePicture = updatedUser.profilePicture
            )
            _currentUser.value = updatedUser // ✅ Now this works perfectly
        }
    }


    fun updateProfile(
        name: String,
        bio: String?,
        profilePictureUri: Uri?,
        accountType: AccountType,
        onResult: (Result<User>) -> Unit
    ) {
        viewModelScope.launch {
            val result = authRepository.updateProfile(name, bio, profilePictureUri, accountType)
            onResult(result)
        }
    }



    /**
     * ✅ Google Sign-In Client getter
     */
    fun getGoogleSignInClient(activity: Activity) = authRepository.getGoogleSignInClient()
}
