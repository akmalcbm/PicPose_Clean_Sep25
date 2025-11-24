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

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    // Expose whether user is logged in (DataStore source-of-truth)
    val isLoggedIn: StateFlow<Boolean> =
        userSessionManager.isLoggedIn.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // Expose skip auth flag from DataStore
    val hasSkippedAuth: StateFlow<Boolean> =
        userSessionManager.hasSkippedAuth.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // Single source for current user built from DataStore fields
    val currentUser: StateFlow<User?> = combine(
        userSessionManager.userId,
        userSessionManager.userEmail,
        userSessionManager.userName,
        userSessionManager.userProfilePicture
    ) { id, email, name, profilePicture ->
        if (!id.isNullOrBlank() && !email.isNullOrBlank() && !name.isNullOrBlank()) {
            User(
                id = id,
                email = email,
                name = name,
                profilePicture = profilePicture
            )
        } else null
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Local cache / event holder (optional)
    private val _authEvents = MutableSharedFlow<AuthState>(replay = 0)
    val authEvents: SharedFlow<AuthState> = _authEvents.asSharedFlow()

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = authRepository.login(email, password)
            if (result.isSuccess) {
                val user = result.getOrNull()!!
                _authState.value = AuthState.Success(user)
                _authEvents.emit(AuthState.Success(user))
            } else {
                val msg = result.exceptionOrNull()?.message ?: "Login failed"
                _authState.value = AuthState.Error(msg)
                _authEvents.emit(AuthState.Error(msg))
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
                val msg = result.exceptionOrNull()?.message ?: "Registration failed"
                _authState.value = AuthState.Error(msg)
                _authEvents.emit(AuthState.Error(msg))
            }
        }
    }

    fun signInWithGoogle(account: GoogleSignInAccount) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = authRepository.signInWithGoogle(account)
            if (result.isSuccess) {
                val user = result.getOrNull()!!
                _authState.value = AuthState.Success(user)
                _authEvents.emit(AuthState.Success(user))
            } else {
                val msg = result.exceptionOrNull()?.message ?: "Google sign-in failed"
                _authState.value = AuthState.Error(msg)
                _authEvents.emit(AuthState.Error(msg))
            }
        }
    }

    fun signInWithFacebook(token: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = authRepository.signInWithFacebook(token)
            if (result.isSuccess) {
                val user = result.getOrNull()!!
                _authState.value = AuthState.Success(user)
                _authEvents.emit(AuthState.Success(user))
            } else {
                val msg = result.exceptionOrNull()?.message ?: "Facebook sign-in failed"
                _authState.value = AuthState.Error(msg)
                _authEvents.emit(AuthState.Error(msg))
            }
        }
    }

    fun logout(onDone: (() -> Unit)? = null) {
        viewModelScope.launch {
            authRepository.logout()
            _authState.value = AuthState.Idle
            onDone?.invoke()
        }
    }

    fun skipAuth() {
        viewModelScope.launch {
            userSessionManager.setSkipAuth(true)
        }
    }

    fun resetSkip() {
        viewModelScope.launch {
            userSessionManager.setSkipAuth(false)
        }
    }

    fun resetAuthState() {
        _authState.value = AuthState.Idle
    }

    fun refreshUserFromSession() {
        viewModelScope.launch {
            userSessionManager.userId.firstOrNull()?.let { id ->
                val email = userSessionManager.userEmail.firstOrNull()
                val name = userSessionManager.userName.firstOrNull()
                val pic = userSessionManager.userProfilePicture.firstOrNull()
                if (!id.isNullOrBlank() && !email.isNullOrBlank() && !name.isNullOrBlank()) {
                    _authState.value = AuthState.Success(
                        User(id = id, email = email, name = name, profilePicture = pic)
                    )
                }
            }
        }
    }

    fun fetchCurrentUser() {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val userId = userSessionManager.userId.firstOrNull()
            if (userId != null) {
                val result = authRepository.getUserProfile(userId)
                if (result.isSuccess) {
                    val user = result.getOrNull()!!
                    _authState.value = AuthState.Success(user)
                    // persist the fresh user
                    userSessionManager.saveUserSession(
                        userId = user.id,
                        email = user.email,
                        name = user.displayName,
                        profilePicture = user.displayProfilePicture
                    )
                } else {
                    // fallback
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
                name = updatedUser.displayName,
                profilePicture = updatedUser.displayProfilePicture
            )
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
            if (result.isSuccess) {
                result.getOrNull()?.let { refreshUserSession(it) }
            }
            onResult(result)
        }
    }

    fun getGoogleSignInClient(activity: Activity) = authRepository.getGoogleSignInClient()
}
