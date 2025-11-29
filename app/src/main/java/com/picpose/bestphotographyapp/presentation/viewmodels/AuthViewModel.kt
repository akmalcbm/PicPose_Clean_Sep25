package com.picpose.bestphotographyapp.presentation.viewmodels

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.credentials.GetCredentialResponse
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facebook.AccessToken
import com.facebook.CallbackManager
import com.picpose.bestphotographyapp.auth.*
import com.picpose.bestphotographyapp.data.datastore.UserSessionManager
import com.picpose.bestphotographyapp.data.models.AccountType
import com.picpose.bestphotographyapp.data.models.User
import com.picpose.bestphotographyapp.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

// ---------------------------
// Auth State (kept from your project)
sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Success(val user: User) : AuthState()
    data class Error(val message: String) : AuthState()
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userSessionManager: UserSessionManager,
    private val googleClient: GoogleAuthUiClient,
    private val facebookClient: FacebookAuthClient,
    private val twitterClient: TwitterAuthClient
) : ViewModel() {

    // --------------------------
    // Google helper (uses your existing functions)
    // --------------------------
    fun initGoogleClient(context: Context) {
        // kept for compatibility — we provided client via DI; but also ensure googleClient has valid context if needed
        // (our GoogleAuthUiClient already accepts context in constructor which Hilt can't supply here,
        // so your existing initGoogleClient(context) can remain — using the DI-provided client is preferred.)
    }

    suspend fun startGoogleSignIn(): Result<GetCredentialResponse?> {
        return try {
            val response = googleClient.signIn()
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun finishGoogleSignIn(response: GetCredentialResponse?, onResult: (Result<User>) -> Unit) {
        val googleData = try { googleClient.parseGoogleCredential(response) } catch (e: Exception) { null }
        if (googleData == null) {
            onResult(Result.failure(Exception("Unable to sign in with Google (no credential)")))
            return
        }

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
                userSessionManager.saveUserSession(
                    userId = user.id,
                    email = user.email,
                    name = user.displayName,
                    profilePicture = user.displayProfilePicture,
                    bio = user.bio
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
    // State flows (preserve existing)
    // --------------------------
    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    val isLoggedIn: StateFlow<Boolean> =
        userSessionManager.isLoggedIn.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val hasSkippedAuth: StateFlow<Boolean> =
        userSessionManager.hasSkippedAuth.stateIn(viewModelScope, SharingStarted.Eagerly, false)

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
    // Email + Password login / register (unchanged)
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
    // FACEBOOK: start login (UI) -> token -> pass to repository
    // --------------------------
    fun getFacebookCallbackManager(): CallbackManager = facebookClient.getCallbackManager()

    fun startFacebookLogin(activity: Activity) {
        facebookClient.startLogin(activity, { accessToken ->
            // On success return token string to repository
            if (accessToken != null) {
                signInWithFacebook(accessToken.token)
            } else {
                _authState.value = AuthState.Error("Facebook token null")
            }
        }, { error ->
            _authState.value = AuthState.Error(error)
        })
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
                _authState.value = AuthState.Error(result.exceptionOrNull()?.message ?: "Facebook login failed")
            }
        }
    }

    // --------------------------
    // TWITTER (PKCE + redirect)
    // --------------------------
    private var twitterVerifier: String? = null
    private var twitterState: String? = null

    fun startTwitterSignIn(context: Context) {
        val verifier = PKCEUtil.generateCodeVerifier()
        val challenge = PKCEUtil.codeChallengeFromVerifier(verifier)
        val state = "st_${Random.nextInt(999999)}"
        twitterVerifier = verifier
        twitterState = state

        val url = twitterClient.buildAuthorizationUrl(codeChallenge = challenge, state = state)
        twitterClient.launchAuth(context, url)
    }

    fun handleTwitterRedirect(uri: Uri) {
        // Called from Activity.onNewIntent when deep link arrives
        val code = uri.getQueryParameter("code")
        val state = uri.getQueryParameter("state")

        if (code.isNullOrEmpty()) {
            _authState.value = AuthState.Error("Twitter callback missing code")
            return
        }
        if (state != twitterState) {
            _authState.value = AuthState.Error("Twitter state mismatch")
            return
        }
        val verifier = twitterVerifier
        if (verifier.isNullOrEmpty()) {
            _authState.value = AuthState.Error("Missing PKCE verifier")
            return
        }

        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val tokenResult = twitterClient.exchangeCodeForToken(code, verifier)
            if (tokenResult.isSuccess) {
                val token = tokenResult.getOrNull()?.access_token
                if (token.isNullOrEmpty()) {
                    _authState.value = AuthState.Error("Twitter returned empty access token")
                    return@launch
                }
                // send token to server to get/create user
                val loginResult = authRepository.signInWithTwitter(token)
                if (loginResult.isSuccess) {
                    val user = loginResult.getOrNull()!!
                    _authState.value = AuthState.Success(user)
                    _authEvents.emit(AuthState.Success(user))
                } else {
                    _authState.value = AuthState.Error(loginResult.exceptionOrNull()?.message ?: "Twitter login failed")
                }
            } else {
                _authState.value = AuthState.Error(tokenResult.exceptionOrNull()?.message ?: "Twitter token exchange failed")
            }
        }
    }

    // --------------------------
    // LOGOUT / SKIP / PROFILE helpers (unchanged)
    // --------------------------
    fun logout(onDone: (() -> Unit)? = null) {
        viewModelScope.launch {
            authRepository.logout()
            userSessionManager.setSkipAuth(false)
            _authState.value = AuthState.Idle
            onDone?.invoke()
        }
    }

    fun skipAuth() {
        viewModelScope.launch { userSessionManager.setSkipAuth(true) }
    }

    fun resetSkip() {
        viewModelScope.launch { userSessionManager.setSkipAuth(false) }
    }

    fun resetAuthState() {
        _authState.value = AuthState.Idle
    }

    fun refreshUserFromSession() {
        viewModelScope.launch {
            val id = userSessionManager.userId.firstOrNull()
            val email = userSessionManager.userEmail.firstOrNull()
            val name = userSessionManager.userName.firstOrNull()
            val pic = userSessionManager.userProfilePicture.firstOrNull()

            if (!id.isNullOrEmpty() && !email.isNullOrEmpty() && !name.isNullOrEmpty()) {
                _authState.value = AuthState.Success(User(id, email, name, profilePicture = pic))
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
