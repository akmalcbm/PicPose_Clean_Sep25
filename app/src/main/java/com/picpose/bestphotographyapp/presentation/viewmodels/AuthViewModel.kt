/**
 * ---
 * File: AuthViewModel.kt
 * Layer: Presentation (MVVM)
 * Project: PicPose
 *
 * Purpose:
 * Owns screen state and coordinates the MVVM flow between Compose UI and repository/data operations.
 *
 * Interactions:
 * Observed by Compose screens. It transforms repository results into StateFlow values that the UI collects.
 *
 * Data Flow:
 * UI (Compose) -> ViewModel -> Repository -> Local/Remote Data Source -> Room/API
 *
 * Maintainer Notes:
 * - Expose observable UI state here, but keep composable rendering decisions in the UI layer.
 * - Business rules belong in repositories or dedicated domain classes if the project introduces use cases later.
 * ---
 */

package com.picpose.bestphotographyapp.presentation.viewmodels

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facebook.CallbackManager
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.picpose.bestphotographyapp.BuildConfig
import com.picpose.bestphotographyapp.core.analytics.AnalyticsLogger
import com.picpose.bestphotographyapp.core.crash.CrashReporter
import com.picpose.bestphotographyapp.R
import com.picpose.bestphotographyapp.data.datastore.UserSessionManager
import com.picpose.bestphotographyapp.data.models.AccountType
import com.picpose.bestphotographyapp.data.models.UserRole
import com.picpose.bestphotographyapp.data.models.User
import com.picpose.bestphotographyapp.domain.repository.AuthRepository
import com.picpose.bestphotographyapp.core.utils.PKCEUtil
import com.picpose.bestphotographyapp.data.remote.auth.FacebookAuthClient
import com.picpose.bestphotographyapp.data.remote.auth.GoogleAuthUiClient
import com.picpose.bestphotographyapp.data.remote.auth.TwitterAuthClient
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

sealed class OperationState {
    object Idle : OperationState()
    object Loading : OperationState()
    data class Success(val message: String) : OperationState()
    data class Error(val message: String) : OperationState()
}

@HiltViewModel
/**
 * Authentication state owner for login, logout, session persistence, and social
 * sign-in providers.
 *
 * Compose observes the exposed state flows to decide whether to show loading,
 * error, or authenticated UI. Network work is delegated to `AuthRepository`,
 * while this ViewModel handles SDK callbacks, concurrency control, analytics,
 * and local session updates.
 */
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userSessionManager: UserSessionManager,
    private val analyticsLogger: AnalyticsLogger,
    private val crashReporter: CrashReporter,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    // SDK-specific clients are kept here so Compose screens stay declarative.
    private var googleClient: GoogleAuthUiClient? = null
    private val facebookClient = FacebookAuthClient()
    private val twitterClient = TwitterAuthClient()
    private val authActionMutex = Mutex()

    /**
     * Google sign-in is intentionally split into launch and finish steps:
     * the activity or Compose layer starts credential collection, then the
     * resulting token is exchanged with the backend via the repository.
     */
    fun initGoogleClient(context: Context) {
        googleClient = GoogleAuthUiClient(context)
        debugLog("google_client_initialized")
    }

    suspend fun startGoogleSignIn(): Result<GetCredentialResponse?> {
        val google = googleClient
        if (google == null) {
            debugLog("google_sign_in_not_initialized")
            return Result.failure(IllegalStateException(appContext.getString(R.string.google_login_failed)))
        }
        if (_authState.value is AuthState.Loading) {
            debugLog("google_sign_in_ignored_loading")
            return Result.failure(IllegalStateException(appContext.getString(R.string.auth_action_in_progress)))
        }

        return try {
            debugLog("google_sign_in_launching")
            val response = google.signIn()
            debugLog("google_sign_in_response_received hasResponse=${response != null}")
            Result.success(response)
        } catch (e: NoCredentialException) {
            val msg = appContext.getString(R.string.google_no_credentials_available)
            debugLog("google_sign_in_no_credentials")
            _authState.value = AuthState.Error(msg)
            Result.failure(IllegalStateException(msg, e))
        } catch (e: GetCredentialCancellationException) {
            val msg = appContext.getString(R.string.google_sign_in_cancelled)
            debugLog("google_sign_in_cancelled")
            _authState.value = AuthState.Error(msg)
            Result.failure(IllegalStateException(msg, e))
        } catch (e: Exception) {
            Log.e("AuthViewModel", buildGoogleSignInErrorLog("startGoogleSignIn", e), e)
            Result.failure(e)
        }
    }

    suspend fun finishGoogleSignIn(
        response: GetCredentialResponse?,
        onResult: (Result<User>) -> Unit
    ) {
        debugLog("google_sign_in_finish_called hasResponse=${response != null}")
        if (response == null) {
            val err = appContext.getString(R.string.google_sign_in_cancelled)
            _authState.value = AuthState.Error(err)
            onResult(Result.failure(Exception(err)))
            return
        }

        val googleData = try {
            googleClient?.parseGoogleCredential(response)
        } catch (e: Exception) {
            Log.e("AuthViewModel", buildGoogleSignInErrorLog("finishGoogleSignIn.parseCredential", e), e)
            null
        }

        if (googleData == null) {
            val err = appContext.getString(R.string.google_credential_missing)
            _authState.value = AuthState.Error(err)
            onResult(Result.failure(Exception(err)))
            return
        }

        val idToken = googleData.idToken?.trim().orEmpty()
        if (idToken.isBlank()) {
            val err = appContext.getString(R.string.google_token_missing)
            _authState.value = AuthState.Error(err)
            onResult(Result.failure(Exception(err)))
            return
        }

        _authState.value = AuthState.Loading

        viewModelScope.launch {
            debugLog("google_social_login_request emailPresent=${!googleData.email.isNullOrBlank()} namePresent=${!googleData.displayName.isNullOrBlank()}")
            val result = authRepository.socialLogin(
                provider = "google",
                token = idToken,
                email = googleData.email ?: "",
                name = googleData.displayName ?: "",
                profilePicture = googleData.profilePictureUrl
            )

            if (result.isSuccess) {
                val user = result.getOrNull()!!
                saveAndEmitSuccess(user, fallbackAccountType = AccountType.NORMAL)
                    .onSuccess { safeUser ->
                        analyticsLogger.logLoginSuccess("google")
                        onResult(Result.success(safeUser))
                    }
                    .onFailure { error ->
                        onResult(Result.failure(error))
                    }
            } else {
                val msg = result.exceptionOrNull()?.message
                    ?: appContext.getString(R.string.google_login_failed)
                Log.e("AuthViewModel", "Google authRepository.socialLogin failed: $msg")
                _authState.value = AuthState.Error(msg)
                onResult(Result.failure(Exception(msg)))
            }
        }
    }

    private fun buildGoogleSignInErrorLog(stage: String, throwable: Throwable): String {
        val apiException = throwable.findApiException()
        val statusDetails = apiException?.let {
            " apiStatusCode=${it.statusCode} apiStatusName=${CommonStatusCodes.getStatusCodeString(it.statusCode)}"
        }.orEmpty()
        return "Google Sign-In failure stage=$stage class=${throwable.javaClass.simpleName} message=${throwable.localizedMessage}$statusDetails"
    }

    private fun Throwable.findApiException(): ApiException? {
        if (this is ApiException) return this
        val directCause = cause
        if (directCause is ApiException) return directCause
        val nestedCause = directCause?.cause
        return nestedCause as? ApiException
    }

    // ----------------------------------------
    // FACEBOOK LOGIN
    // ----------------------------------------
    fun getFacebookCallbackManager(): CallbackManager =
        facebookClient.getCallbackManager()

    fun startFacebookLogin(activity: Activity) {
        if (_authState.value is AuthState.Loading) return
        _authState.value = AuthState.Loading

        facebookClient.startLogin(
            activity = activity,
            onSuccess = { accessToken ->
                if (accessToken != null) {
                    signInWithFacebook(accessToken.token)
                } else {
                    _authState.value = AuthState.Error(appContext.getString(R.string.facebook_null_token))
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
                saveAndEmitSuccess(result.getOrNull()!!, fallbackAccountType = AccountType.NORMAL)
                    .onSuccess {
                        analyticsLogger.logLoginSuccess("facebook")
                    }
            } else {
                _authState.value =
                    AuthState.Error(result.exceptionOrNull()?.message ?: appContext.getString(R.string.facebook_login_failed))
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
            _authState.value = AuthState.Error(appContext.getString(R.string.twitter_code_missing))
            return
        }
        if (state != twitterState) {
            _authState.value = AuthState.Error(appContext.getString(R.string.twitter_state_mismatch))
            return
        }

        val verifier = twitterVerifier ?: run {
            _authState.value = AuthState.Error(appContext.getString(R.string.missing_pkce_verifier))
            return
        }

        viewModelScope.launch {
            _authState.value = AuthState.Loading

            val tokenResult = twitterClient.exchangeCodeForToken(code, verifier)
            if (tokenResult.isSuccess) {
                val token = tokenResult.getOrNull()?.access_token
                if (token.isNullOrEmpty()) {
                    _authState.value = AuthState.Error(appContext.getString(R.string.twitter_access_token_invalid))
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
                    saveAndEmitSuccess(loginResult.getOrNull()!!, fallbackAccountType = AccountType.NORMAL)
                        .onSuccess {
                            analyticsLogger.logLoginSuccess("twitter")
                        }
                } else {
                    _authState.value =
                        AuthState.Error(loginResult.exceptionOrNull()?.message ?: appContext.getString(R.string.twitter_login_failed))
                }
            } else {
                _authState.value =
                    AuthState.Error(tokenResult.exceptionOrNull()?.message ?: appContext.getString(R.string.twitter_token_exchange_failed))
            }
        }
    }

    // ----------------------------------------
    // AUTH STATE & USER FLOWS
    // ----------------------------------------
    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()
    private val _isDeletingAccount = MutableStateFlow(false)
    val isDeletingAccount: StateFlow<Boolean> = _isDeletingAccount.asStateFlow()

    val isLoggedIn = userSessionManager.isLoggedIn
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val hasSkippedAuth = userSessionManager.hasSkippedAuth
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val hasAcceptedPrivacyTerms = userSessionManager.hasAcceptedPrivacyTerms
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val baseCurrentUserFlow = combine(
        userSessionManager.userId,
        userSessionManager.userEmail,
        userSessionManager.userName,
        userSessionManager.userProfilePicture,
        userSessionManager.userBio
    ) { id, email, name, pic, bio ->
        if (!id.isNullOrBlank() && !email.isNullOrBlank() && !name.isNullOrBlank()) {
            User(
                id = id,
                email = email,
                nameRaw = name,
                profilePicture = pic,
                bio = bio
            )
        } else null
    }

    val currentUser: StateFlow<User?> = combine(
        baseCurrentUserFlow,
        userSessionManager.userEmailVerified
    ) { user, emailVerified ->
        user?.copy(emailVerified = if (emailVerified) 1 else 0)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _authEvents = MutableSharedFlow<AuthState>()
    val authEvents = _authEvents.asSharedFlow()
    private val _passwordResetRequestState = MutableStateFlow<OperationState>(OperationState.Idle)
    val passwordResetRequestState: StateFlow<OperationState> = _passwordResetRequestState.asStateFlow()
    private val _resetPasswordState = MutableStateFlow<OperationState>(OperationState.Idle)
    val resetPasswordState: StateFlow<OperationState> = _resetPasswordState.asStateFlow()
    private val _emailVerificationRequestState = MutableStateFlow<OperationState>(OperationState.Idle)
    val emailVerificationRequestState: StateFlow<OperationState> = _emailVerificationRequestState.asStateFlow()
    private val _verifyEmailTokenState = MutableStateFlow<OperationState>(OperationState.Idle)
    val verifyEmailTokenState: StateFlow<OperationState> = _verifyEmailTokenState.asStateFlow()

    // ----------------------------------------
    // EMAIL + PASSWORD LOGIN
    // ----------------------------------------
    fun login(email: String, password: String) {
        if (_authState.value is AuthState.Loading) return
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            debugLog("email_login_request emailPresent=${email.isNotBlank()}")
            val result = authRepository.login(email, password)

            if (result.isSuccess) {
                saveAndEmitSuccess(result.getOrNull()!!, fallbackAccountType = AccountType.NORMAL)
                    .onSuccess {
                        analyticsLogger.logLoginSuccess("password")
                    }
            } else {
                _authState.value =
                    AuthState.Error(result.exceptionOrNull()?.message ?: appContext.getString(R.string.login_failed))
            }
        }
    }

    fun register(email: String, password: String, name: String) {
        if (_authState.value is AuthState.Loading) return
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            debugLog("email_signup_request emailPresent=${email.isNotBlank()} namePresent=${name.isNotBlank()}")
            val result = authRepository.register(email, password, name)

            if (result.isSuccess) {
                saveAndEmitSuccess(result.getOrNull()!!, fallbackAccountType = AccountType.NORMAL)
                    .onSuccess {
                        analyticsLogger.logSignupSuccess("password")
                    }
            } else {
                _authState.value =
                    AuthState.Error(result.exceptionOrNull()?.message ?: appContext.getString(R.string.registration_failed))
            }
        }
    }

    fun requestPasswordReset(email: String) {
        viewModelScope.launch {
            _passwordResetRequestState.value = OperationState.Loading
            val result = authRepository.requestPasswordReset(email.trim())
            _passwordResetRequestState.value = if (result.isSuccess) {
                OperationState.Success(result.getOrNull() ?: appContext.getString(R.string.reset_password_email_sent_generic))
            } else {
                OperationState.Error(
                    result.exceptionOrNull()?.message ?: appContext.getString(R.string.network_error_try_again)
                )
            }
        }
    }

    fun resetPassword(token: String, newPassword: String) {
        viewModelScope.launch {
            _resetPasswordState.value = OperationState.Loading
            val result = authRepository.resetPassword(token, newPassword)
            _resetPasswordState.value = if (result.isSuccess) {
                OperationState.Success(result.getOrNull() ?: appContext.getString(R.string.password_reset_success))
            } else {
                OperationState.Error(
                    result.exceptionOrNull()?.message ?: appContext.getString(R.string.reset_token_invalid_or_expired)
                )
            }
        }
    }

    fun requestEmailVerification() {
        viewModelScope.launch {
            val userId = userSessionManager.userId.firstOrNull()
            if (userId.isNullOrBlank()) {
                _emailVerificationRequestState.value = OperationState.Error(appContext.getString(R.string.not_logged_in))
                return@launch
            }
            _emailVerificationRequestState.value = OperationState.Loading
            val result = authRepository.requestEmailVerification(userId)
            _emailVerificationRequestState.value = if (result.isSuccess) {
                OperationState.Success(result.getOrNull() ?: appContext.getString(R.string.verification_email_sent))
            } else {
                OperationState.Error(
                    result.exceptionOrNull()?.message ?: appContext.getString(R.string.network_error_try_again)
                )
            }
        }
    }

    fun verifyEmailToken(token: String, onDone: (() -> Unit)? = null) {
        viewModelScope.launch {
            _verifyEmailTokenState.value = OperationState.Loading
            val result = authRepository.verifyEmailToken(token)
            _verifyEmailTokenState.value = if (result.isSuccess) {
                fetchCurrentUser()
                onDone?.invoke()
                OperationState.Success(result.getOrNull() ?: appContext.getString(R.string.email_verification_success))
            } else {
                OperationState.Error(
                    result.exceptionOrNull()?.message ?: appContext.getString(R.string.email_verification_invalid_or_expired)
                )
            }
        }
    }

    // ----------------------------------------
    // LOGOUT
    // ----------------------------------------
    fun logout(onDone: (() -> Unit)? = null) {
        viewModelScope.launch {
            authRepository.logout()
            crashReporter.setUserIdentifier(null)
            crashReporter.setAccountType("unknown")
            userSessionManager.setSkipAuth(false)
            _authState.value = AuthState.Idle
            onDone?.invoke()
        }
    }

    fun deleteAccount(
        reason: String = "user_requested_in_app",
        onResult: (Result<Unit>) -> Unit
    ) {
        if (_isDeletingAccount.value) return

        viewModelScope.launch {
            _isDeletingAccount.value = true
            val result = authRepository.deleteAccount(reason)

            if (result.isSuccess) {
                userSessionManager.setSkipAuth(false)
                _authState.value = AuthState.Idle
            }

            _isDeletingAccount.value = false
            onResult(result)
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

    fun resetPasswordResetRequestState() {
        _passwordResetRequestState.value = OperationState.Idle
    }

    fun resetResetPasswordState() {
        _resetPasswordState.value = OperationState.Idle
    }

    fun resetEmailVerificationRequestState() {
        _emailVerificationRequestState.value = OperationState.Idle
    }

    fun resetVerifyEmailTokenState() {
        _verifyEmailTokenState.value = OperationState.Idle
    }

    fun setPrivacyTermsAccepted(accepted: Boolean = true) {
        viewModelScope.launch {
            userSessionManager.setPrivacyTermsAccepted(accepted)
        }
    }

    // ----------------------------------------
    // PROFILE REFRESH
    // ----------------------------------------
    fun fetchCurrentUser() {
        viewModelScope.launch {
            _authState.value = AuthState.Loading

            val userId = userSessionManager.userId.firstOrNull()
            if (userId == null) {
                _authState.value = AuthState.Error(appContext.getString(R.string.not_logged_in))
                return@launch
            }

            val result = authRepository.getUserProfile(userId)
            if (result.isSuccess) {
                val user = result.getOrNull()!!
                val safeUser = normalizeAuthUser(user, fallbackAccountType = AccountType.NORMAL)
                saveSession(safeUser)
                _authState.value = AuthState.Success(safeUser)
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
                val safeUser = normalizeAuthUser(result.getOrNull()!!, fallbackAccountType = AccountType.NORMAL)
                saveSession(safeUser)
            }

            onResult(result)
        }
    }

    // ----------------------------------------
    // INTERNAL HELPERS
    // ----------------------------------------
    private fun saveAndEmitSuccess(user: User) {
        viewModelScope.launch {
            saveAndEmitSuccess(user, fallbackAccountType = AccountType.NORMAL)
        }
    }

    private suspend fun saveAndEmitSuccess(
        user: User,
        fallbackAccountType: AccountType
    ): Result<User> = authActionMutex.withLock {
        try {
            val safeUser = normalizeAuthUser(user, fallbackAccountType)
            val accountTypeValue = runCatching { safeUser.accountType.value }
                .getOrDefault(fallbackAccountType.value)
            debugLog(
                "save_and_emit_success id=${safeUser.id} " +
                    "emailPresent=${safeUser.email.isNotBlank()} " +
                    "accountType=$accountTypeValue provider=${safeUser.provider ?: "unknown"}"
            )

            saveSession(safeUser)
            crashReporter.setUserIdentifier(safeUser.id)
            crashReporter.setAccountType(accountTypeValue)
            _authState.value = AuthState.Success(safeUser)
            _authEvents.emit(AuthState.Success(safeUser))
            Result.success(safeUser)
        } catch (e: Exception) {
            Log.e("AuthViewModel", "saveAndEmitSuccess failed", e)
            val msg = e.localizedMessage ?: appContext.getString(R.string.auth_invalid_user_data)
            _authState.value = AuthState.Error(msg)
            Result.failure(e)
        }
    }

    private suspend fun saveSession(user: User) {
        userSessionManager.saveUserSession(
            userId = user.id,
            email = user.email,
            name = user.displayName,
            profilePicture = user.displayProfilePicture,
            bio = user.bio,
            token = null,
            emailVerified = user.isEmailVerified
        )
    }

    private fun normalizeAuthUser(user: User, fallbackAccountType: AccountType): User {
        val safeId = user.id.trim()
        val safeEmail = user.email.trim()

        if (safeId.isEmpty() || safeEmail.isEmpty()) {
            throw IllegalStateException(appContext.getString(R.string.auth_invalid_user_data))
        }

        val safeAccountType = runCatching { user.accountType }.getOrNull() ?: fallbackAccountType
        val safeRole = runCatching { user.role }.getOrNull() ?: UserRole.USER
        return user.copy(
            id = safeId,
            email = safeEmail,
            accountType = safeAccountType,
            role = safeRole
        )
    }

    private fun debugLog(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d("AuthFlow", message)
        }
    }

    private fun refreshUserFromSession() {
        viewModelScope.launch {
            val id = userSessionManager.userId.firstOrNull()
            val email = userSessionManager.userEmail.firstOrNull()
            val name = userSessionManager.userName.firstOrNull()
            val pic = userSessionManager.userProfilePicture.firstOrNull()
            val bio = userSessionManager.userBio.firstOrNull()
            val emailVerified = userSessionManager.userEmailVerified.firstOrNull() ?: false

            if (!id.isNullOrBlank() && !email.isNullOrBlank() && !name.isNullOrBlank()) {
                _authState.value =
                    AuthState.Success(
                        User(
                            id = id,
                            email = email,
                            nameRaw = name,
                            profilePicture = pic,
                            bio = bio,
                            emailVerified = if (emailVerified) 1 else 0
                        )
                    )
            }
        }
    }
}
