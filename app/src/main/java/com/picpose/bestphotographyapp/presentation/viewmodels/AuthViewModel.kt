package com.picpose.bestphotographyapp.presentation.viewmodels

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.credentials.GetCredentialResponse
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facebook.CallbackManager
import com.picpose.bestphotographyapp.auth.*
import com.picpose.bestphotographyapp.data.datastore.UserSessionManager
import com.picpose.bestphotographyapp.data.models.AccountType
import com.picpose.bestphotographyapp.data.models.User
import com.picpose.bestphotographyapp.data.repository.AuthRepository
import com.picpose.bestphotographyapp.core.utils.PKCEUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

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

    // ----------------------------------------
    // SOCIAL LOGIN CLIENTS
    // ----------------------------------------
    private var googleClient: GoogleAuthUiClient? = null
    private val facebookClient = FacebookAuthClient()
    private val twitterClient = TwitterAuthClient()

    // ----------------------------------------
    // GOOGLE LOGIN
    // ----------------------------------------
    fun initGoogleClient(context: Context) {
        googleClient = GoogleAuthUiClient(context)
    }

    suspend fun startGoogleSignIn(): Result<GetCredentialResponse?> {
        return try {
            val response = googleClient?.signIn()
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun finishGoogleSignIn(
        response: GetCredentialResponse?,
        onResult: (Result<User>) -> Unit
    ) {
        val googleData = try {
            googleClient?.parseGoogleCredential(response)
        } catch (e: Exception) {
            null
        }

        if (googleData == null) {
            onResult(Result.failure(Exception("Google credential missing")))
            return
        }

        _authState.value = AuthState.Loading

        viewModelScope.launch {
            val result = authRepository.socialLogin(
                provider = "google",
                token = googleData.idToken ?: "",
                email = googleData.email ?: "",
                name = googleData.displayName ?: "",
                profilePicture = googleData.profilePictureUrl
            )

            if (result.isSuccess) {
                val user = result.getOrNull()!!
                saveAndEmitSuccess(user)
                onResult(Result.success(user))
            } else {
                val msg = result.exceptionOrNull()?.message ?: "Google login failed"
                _authState.value = AuthState.Error(msg)
                onResult(Result.failure(Exception(msg)))
            }
        }
    }

    // ----------------------------------------
    // FACEBOOK LOGIN
    // ----------------------------------------
    fun getFacebookCallbackManager(): CallbackManager =
        facebookClient.getCallbackManager()

    fun startFacebookLogin(activity: Activity) {
        _authState.value = AuthState.Loading

        facebookClient.startLogin(
            activity = activity,
            onSuccess = { accessToken ->
                if (accessToken != null) {
                    signInWithFacebook(accessToken.token)
                } else {
                    _authState.value = AuthState.Error("Facebook returned null token")
                }
            },
            onError = { error ->
                _authState.value = AuthState.Error(error)
            }
        )
    }

    private fun signInWithFacebook(token: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = authRepository.socialLogin(
                provider = "facebook",
                token = token,
                email = null,  // optional
                name = null,
                profilePicture = null
            )

            if (result.isSuccess) {
                saveAndEmitSuccess(result.getOrNull()!!)
            } else {
                _authState.value =
                    AuthState.Error(result.exceptionOrNull()?.message ?: "Facebook login failed")
            }
        }
    }

    // ----------------------------------------
    // TWITTER LOGIN (PKCE)
    // ----------------------------------------
    private var twitterVerifier: String? = null
    private var twitterState: String? = null

    fun startTwitterSignIn(context: Context) {
        val verifier = PKCEUtil.generateCodeVerifier()
        val challenge = PKCEUtil.codeChallengeFromVerifier(verifier)
        val state = "st_${Random.nextInt(999999)}"

        twitterVerifier = verifier
        twitterState = state

        val url = twitterClient.buildAuthorizationUrl(
            codeChallenge = challenge,
            state = state
        )

        twitterClient.launchAuth(context, url)
    }

    fun handleTwitterRedirect(uri: Uri) {
        val code = uri.getQueryParameter("code")
        val state = uri.getQueryParameter("state")

        if (code.isNullOrEmpty()) {
            _authState.value = AuthState.Error("Twitter code missing")
            return
        }
        if (state != twitterState) {
            _authState.value = AuthState.Error("Twitter state mismatch")
            return
        }

        val verifier = twitterVerifier ?: run {
            _authState.value = AuthState.Error("Missing PKCE verifier")
            return
        }

        viewModelScope.launch {
            _authState.value = AuthState.Loading

            val tokenResult = twitterClient.exchangeCodeForToken(code, verifier)
            if (tokenResult.isSuccess) {
                val token = tokenResult.getOrNull()?.access_token
                if (token.isNullOrEmpty()) {
                    _authState.value = AuthState.Error("Twitter access token invalid")
                    return@launch
                }

                val loginResult = authRepository.socialLogin(
                    provider = "twitter",
                    token = token,
                    email = null,
                    name = null,
                    profilePicture = null
                )

                if (loginResult.isSuccess) {
                    saveAndEmitSuccess(loginResult.getOrNull()!!)
                } else {
                    _authState.value =
                        AuthState.Error(loginResult.exceptionOrNull()?.message ?: "Twitter login failed")
                }
            } else {
                _authState.value =
                    AuthState.Error(tokenResult.exceptionOrNull()?.message ?: "Twitter token exchange failed")
            }
        }
    }

    // ----------------------------------------
    // AUTH STATE & USER FLOWS
    // ----------------------------------------
    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    val isLoggedIn = userSessionManager.isLoggedIn
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val hasSkippedAuth = userSessionManager.hasSkippedAuth
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val currentUser: StateFlow<User?> = combine(
        userSessionManager.userId,
        userSessionManager.userEmail,
        userSessionManager.userName,
        userSessionManager.userProfilePicture,
        userSessionManager.userBio
    ) { id, email, name, pic, bio ->
        if (!id.isNullOrBlank() && !email.isNullOrBlank() && !name.isNullOrBlank()) {
            User(id, email, name, profilePicture = pic, bio = bio)
        } else null
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _authEvents = MutableSharedFlow<AuthState>()
    val authEvents = _authEvents.asSharedFlow()

    // ----------------------------------------
    // EMAIL + PASSWORD LOGIN
    // ----------------------------------------
    fun login(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = authRepository.login(email, password)

            if (result.isSuccess) {
                saveAndEmitSuccess(result.getOrNull()!!)
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
                saveAndEmitSuccess(result.getOrNull()!!)
            } else {
                _authState.value =
                    AuthState.Error(result.exceptionOrNull()?.message ?: "Registration failed")
            }
        }
    }

    // ----------------------------------------
    // LOGOUT
    // ----------------------------------------
    fun logout(onDone: (() -> Unit)? = null) {
        viewModelScope.launch {
            authRepository.logout()
            userSessionManager.setSkipAuth(false)
            _authState.value = AuthState.Idle
            onDone?.invoke()
        }
    }

    // ----------------------------------------
    // SKIP & RESET
    // ----------------------------------------
    fun skipAuth() {
        viewModelScope.launch { userSessionManager.setSkipAuth(true) }
    }

    fun resetSkip() {
        viewModelScope.launch { userSessionManager.setSkipAuth(false) }
    }

    fun resetAuthState() {
        _authState.value = AuthState.Idle
    }

    // ----------------------------------------
    // PROFILE REFRESH
    // ----------------------------------------
    fun fetchCurrentUser() {
        viewModelScope.launch {
            _authState.value = AuthState.Loading

            val userId = userSessionManager.userId.firstOrNull()
            if (userId == null) {
                _authState.value = AuthState.Error("Not logged in")
                return@launch
            }

            val result = authRepository.getUserProfile(userId)
            if (result.isSuccess) {
                val user = result.getOrNull()!!
                saveSession(user)
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
            val result = authRepository.updateProfile(
                name, bio, profilePictureUri, accountType
            )

            if (result.isSuccess) {
                saveSession(result.getOrNull()!!)
            }

            onResult(result)
        }
    }

    // ----------------------------------------
    // INTERNAL HELPERS
    // ----------------------------------------
    private fun saveAndEmitSuccess(user: User) {
        viewModelScope.launch {
            saveSession(user)
            _authState.value = AuthState.Success(user)
            _authEvents.emit(AuthState.Success(user))
        }
    }

    private fun saveSession(user: User) {
        viewModelScope.launch {
            userSessionManager.saveUserSession(
                userId = user.id,
                email = user.email,
                name = user.displayName,
                profilePicture = user.displayProfilePicture,
                bio = user.bio,
                token = null
            )
        }
    }

    private fun refreshUserFromSession() {
        viewModelScope.launch {
            val id = userSessionManager.userId.firstOrNull()
            val email = userSessionManager.userEmail.firstOrNull()
            val name = userSessionManager.userName.firstOrNull()
            val pic = userSessionManager.userProfilePicture.firstOrNull()
            val bio = userSessionManager.userBio.firstOrNull()

            if (!id.isNullOrBlank() && !email.isNullOrBlank() && !name.isNullOrBlank()) {
                _authState.value =
                    AuthState.Success(User(id, email, name, profilePicture = pic, bio = bio))
            }
        }
    }
}
