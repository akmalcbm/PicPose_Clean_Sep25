/**
 * ---
 * File: LoginScreen.kt
 * Layer: Presentation (UI)
 * Project: PicPose
 *
 * Purpose:
 * Lists the app navigation routes and helper builders used by Navigation Compose.
 *
 * Interactions:
 * Reads immutable state from ViewModels or callbacks and emits user events back up to navigation or state owners.
 *
 * Data Flow:
 * UI (Compose) -> ViewModel -> Repository -> Local/Remote Data Source -> Room/API
 *
 * Maintainer Notes:
 * - Prefer stateless composables and keep side effects inside well-scoped effect APIs.
 * - Document new navigation arguments and remember that recomposition can re-run this code frequently.
 * ---
 */

package com.picpose.bestphotographyapp.presentation.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.picpose.bestphotographyapp.BuildConfig
import com.picpose.bestphotographyapp.R
import com.picpose.bestphotographyapp.components.common.PicPoseTopAppBar
import com.picpose.bestphotographyapp.presentation.auth.AuthState
import com.picpose.bestphotographyapp.presentation.auth.AuthViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onNavigateToHome: () -> Unit,
    onNavigateToForgotPassword: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
    onNavigateToTerms: () -> Unit,
    authViewModel: AuthViewModel = hiltViewModel()
) {
    var isLoginMode by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val authState by authViewModel.authState.collectAsState()
    val hasAcceptedPrivacyTerms by authViewModel.hasAcceptedPrivacyTerms.collectAsState()
    var consentChecked by remember(hasAcceptedPrivacyTerms) { mutableStateOf(hasAcceptedPrivacyTerms) }
    var googleSignInInFlight by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Reset skip state
    LaunchedEffect(Unit) {
        authViewModel.resetSkip()
    }

    // Auto navigate after success
    LaunchedEffect(authState) {
        if (authState is AuthState.Success) {
            authViewModel.fetchCurrentUser()
            onNavigateToHome()
            authViewModel.resetAuthState()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            PicPoseTopAppBar(
                title = if (isLoginMode) stringResource(R.string.login) else stringResource(R.string.register),
                actions = {
                    TextButton(onClick = {
                        authViewModel.skipAuth()
                        onNavigateToHome()
                    }) {
                        Text(stringResource(R.string.skip), color = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        }
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Spacer(Modifier.height(8.dp))

            Image(
                painter = painterResource(id = R.drawable.ic_logo_light),
                contentDescription = stringResource(R.string.app_logo),
                modifier = Modifier.size(110.dp)
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = if (isLoginMode)
                    stringResource(R.string.welcome_back)
                else
                    stringResource(R.string.create_account),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(16.dp))

            // Full Name for Register
            if (!isLoginMode) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.full_name)) },
                    leadingIcon = { Icon(Icons.Default.Person, null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(16.dp))
            }

            // Email
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(stringResource(R.string.email)) },
                leadingIcon = { Icon(Icons.Default.Email, null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(16.dp))

            // Password
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.password)) },
                leadingIcon = { Icon(Icons.Default.Lock, null) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = stringResource(R.string.toggle_password_visibility)
                        )
                    }
                },
                visualTransformation = if (passwordVisible)
                    VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            if (isLoginMode) {
                TextButton(
                    onClick = onNavigateToForgotPassword,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(stringResource(R.string.forgot_password))
                }
            }

            Spacer(Modifier.height(12.dp))

            if (!hasAcceptedPrivacyTerms) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Checkbox(
                        checked = consentChecked,
                        onCheckedChange = { consentChecked = it }
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = 11.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.login_consent_agree_text),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row {
                            TextButton(
                                onClick = onNavigateToPrivacy,
                                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
                            ) {
                                Text(stringResource(R.string.privacy_policy_title))
                            }
                            Spacer(Modifier.width(12.dp))
                            TextButton(
                                onClick = onNavigateToTerms,
                                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
                            ) {
                                Text(stringResource(R.string.terms_conditions_title))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // Submit Button
            Button(
                onClick = {
                    if (authState is AuthState.Loading) return@Button
                    if (!hasAcceptedPrivacyTerms && !consentChecked) {
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                message = context.getString(R.string.please_accept_terms_to_continue)
                            )
                        }
                        if (BuildConfig.DEBUG) {
                            Log.d("AuthFlow", "email_click_blocked_terms agreeChecked=false")
                        }
                        return@Button
                    }

                    if (!hasAcceptedPrivacyTerms) {
                        authViewModel.setPrivacyTermsAccepted(consentChecked)
                    }
                    if (BuildConfig.DEBUG) {
                        Log.d(
                            "AuthFlow",
                            "email_click mode=${if (isLoginMode) "login" else "signup"} agreeChecked=$consentChecked hasAccepted=$hasAcceptedPrivacyTerms"
                        )
                    }
                    if (isLoginMode) authViewModel.login(email, password)
                    else authViewModel.register(email, password, name)
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = authState !is AuthState.Loading
            ) {
                if (authState is AuthState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        if (isLoginMode) stringResource(R.string.login)
                        else stringResource(R.string.sign_up),
                        fontSize = 16.sp
                    )
                }
            }

            if (authState is AuthState.Error) {
                Spacer(Modifier.height(12.dp))
                Text(
                    (authState as AuthState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(16.dp))

            AnimatedContent(
                targetState = isLoginMode,
                label = "auth_mode_cta_transition"
            ) { loginMode ->
                val infoText = if (loginMode) {
                    stringResource(R.string.dont_have_account)
                } else {
                    stringResource(R.string.already_have_account)
                }
                val actionText = if (loginMode) {
                    stringResource(R.string.sign_up)
                } else {
                    stringResource(R.string.login)
                }
                val actionContentDescription = if (loginMode) {
                    stringResource(R.string.dont_have_account_sign_up)
                } else {
                    stringResource(R.string.already_have_account_login)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = infoText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = actionText,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .wrapContentHeight(Alignment.CenterVertically)
                            .clickable { isLoginMode = !isLoginMode }
                            .semantics {
                                role = Role.Button
                                contentDescription = actionContentDescription
                            }
                            .padding(horizontal = 8.dp)
                    )
                }
            }

            //Spacer(Modifier.height(16.dp))

            // Divider
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.or_login_with),
                    modifier = Modifier.padding(horizontal = 12.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                HorizontalDivider(modifier = Modifier.weight(1f))
            }

            Spacer(Modifier.height(16.dp))

            // SOCIAL LOGIN BUTTONS
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {

                // ⭐ GOOGLE SIGN-IN
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = {
                            scope.launch {
                                if (BuildConfig.DEBUG) {
                                    Log.d(
                                        "AuthFlow",
                                        "google_click agreeChecked=$consentChecked hasAccepted=$hasAcceptedPrivacyTerms loading=${authState is AuthState.Loading} inFlight=$googleSignInInFlight"
                                    )
                                }
                                if (authState is AuthState.Loading || googleSignInInFlight) return@launch
                                if (!hasAcceptedPrivacyTerms && !consentChecked) {
                                    snackbarHostState.showSnackbar(
                                        message = context.getString(R.string.please_accept_terms_to_continue)
                                    )
                                    return@launch
                                }
                                if (!hasAcceptedPrivacyTerms) {
                                    authViewModel.setPrivacyTermsAccepted(true)
                                }
                                val activity = context.findActivity()
                                if (activity == null || activity.isFinishing || activity.isDestroyed) {
                                    googleSignInInFlight = false
                                    snackbarHostState.showSnackbar(
                                        message = context.getString(R.string.google_login_failed)
                                    )
                                    return@launch
                                }
                                googleSignInInFlight = true
                                try {
                                    val startResult = authViewModel.startGoogleSignIn(activity)
                                    val response = startResult.getOrElse { error ->
                                        googleSignInInFlight = false
                                        snackbarHostState.showSnackbar(
                                            message = error.localizedMessage
                                                ?: context.getString(R.string.google_login_failed)
                                        )
                                        return@launch
                                    }
                                    authViewModel.finishGoogleSignIn(response) { result ->
                                        googleSignInInFlight = false
                                        result.exceptionOrNull()?.localizedMessage?.let { message ->
                                            scope.launch { snackbarHostState.showSnackbar(message) }
                                        }
                                    }
                                } catch (e: Exception) {
                                    googleSignInInFlight = false
                                    if (BuildConfig.DEBUG) {
                                        Log.e("AuthFlow", "google_click_exception", e)
                                    }
                                    snackbarHostState.showSnackbar(
                                        message = e.localizedMessage
                                            ?: context.getString(R.string.google_login_failed)
                                    )
                                }

                            }
                        },
                        modifier = Modifier
                            .size(56.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                shape = MaterialTheme.shapes.medium
                            ),
                        enabled = authState !is AuthState.Loading && !googleSignInInFlight
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_google),
                            contentDescription = stringResource(R.string.google),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.google), fontSize = 13.sp, color = Color.Gray)
                }

                /*
                //Currently Hide will be UnHide in the Next Version
                // ⭐ FACEBOOK SIGN-IN
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = { authViewModel.startFacebookLogin(activity) },
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color(0xFF1877F2).copy(alpha = 0.15f), MaterialTheme.shapes.medium)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_facebook),
                            contentDescription = "Facebook",
                            tint = Color(0xFF1877F2),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Facebook", fontSize = 13.sp, color = Color.Gray)
                }


                //Currently Hide will be UnHide in the Next Version
                // ⭐ TWITTER SIGN-IN
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = { authViewModel.startTwitterSignIn(context) },
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color(0xFF4F4E4E).copy(alpha = 0.12f), MaterialTheme.shapes.medium)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_twitter),
                            contentDescription = "Twitter",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("X (Twitter)", fontSize = 13.sp, color = Color.Gray)
                }
                */

            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
