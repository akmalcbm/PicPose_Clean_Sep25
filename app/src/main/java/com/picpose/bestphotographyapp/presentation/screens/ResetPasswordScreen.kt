package com.picpose.bestphotographyapp.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.picpose.bestphotographyapp.R
import com.picpose.bestphotographyapp.presentation.viewmodels.AuthViewModel
import com.picpose.bestphotographyapp.presentation.viewmodels.OperationState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResetPasswordScreen(
    token: String,
    onBack: () -> Unit,
    onResetSuccessNavigateToLogin: () -> Unit,
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }
    val opState by authViewModel.resetPasswordState.collectAsState()

    LaunchedEffect(Unit) {
        authViewModel.resetResetPasswordState()
    }

    LaunchedEffect(opState) {
        if (opState is OperationState.Success) {
            onResetSuccessNavigateToLogin()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.reset_password_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.Top
        ) {
            OutlinedTextField(
                value = newPassword,
                onValueChange = {
                    newPassword = it
                    validationError = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.new_password)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = confirmPassword,
                onValueChange = {
                    confirmPassword = it
                    validationError = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.confirm_password)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    val trimmed = newPassword.trim()
                    validationError = when {
                        token.isBlank() -> context.getString(R.string.reset_token_invalid_or_expired)
                        trimmed.length < 8 -> context.getString(R.string.password_strength_error)
                        !trimmed.any { it.isDigit() } || !trimmed.any { it.isLetter() } -> context.getString(R.string.password_strength_error)
                        trimmed != confirmPassword.trim() -> context.getString(R.string.passwords_do_not_match)
                        else -> null
                    }

                    if (validationError == null) {
                        authViewModel.resetPassword(token = token, newPassword = trimmed)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = opState !is OperationState.Loading
            ) {
                if (opState is OperationState.Loading) {
                    CircularProgressIndicator()
                } else {
                    Text(stringResource(R.string.reset_password_cta))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            validationError?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            if (opState is OperationState.Error) {
                Text(
                    text = (opState as OperationState.Error).message,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
