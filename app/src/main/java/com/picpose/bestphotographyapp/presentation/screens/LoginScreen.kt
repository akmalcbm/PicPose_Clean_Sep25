package com.picpose.bestphotographyapp.presentation.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.picpose.bestphotographyapp.R
import com.picpose.bestphotographyapp.presentation.viewmodels.AuthState
import com.picpose.bestphotographyapp.presentation.viewmodels.AuthViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onNavigateToHome: () -> Unit,
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Reset skip state
    LaunchedEffect(Unit) {
        authViewModel.resetSkip()
        authViewModel.initGoogleClient(context)
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
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isLoginMode) stringResource(R.string.login)
                        else stringResource(R.string.register)
                    )
                },
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

            Spacer(Modifier.height(16.dp))

            Image(
                painter = painterResource(id = R.drawable.ic_logo_light),
                contentDescription = stringResource(R.string.app_logo),
                modifier = Modifier.size(110.dp)
            )

            Spacer(Modifier.height(24.dp))

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

            Spacer(Modifier.height(24.dp))

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
                    if (!hasAcceptedPrivacyTerms) {
                        authViewModel.setPrivacyTermsAccepted(consentChecked)
                    }
                    if (isLoginMode) authViewModel.login(email, password)
                    else authViewModel.register(email, password, name)
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = authState !is AuthState.Loading && (hasAcceptedPrivacyTerms || consentChecked)
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

            TextButton(onClick = { isLoginMode = !isLoginMode }) {
                Text(
                    if (isLoginMode)
                        stringResource(R.string.dont_have_account_sign_up)
                    else
                        stringResource(R.string.already_have_account_login)
                )
            }

            Spacer(Modifier.height(16.dp))

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

            Spacer(Modifier.height(24.dp))

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
                                if (!hasAcceptedPrivacyTerms && !consentChecked) return@launch
                                if (!hasAcceptedPrivacyTerms) {
                                    authViewModel.setPrivacyTermsAccepted(true)
                                }
                                try {
                                    val response = authViewModel.startGoogleSignIn().getOrNull()
                                    authViewModel.finishGoogleSignIn(response) { }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }

                            }
                        },
                        modifier = Modifier
                            .size(56.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                shape = MaterialTheme.shapes.medium
                            ),
                        enabled = hasAcceptedPrivacyTerms || consentChecked
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
