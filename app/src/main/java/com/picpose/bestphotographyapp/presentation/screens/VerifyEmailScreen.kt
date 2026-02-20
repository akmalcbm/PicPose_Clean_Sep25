package com.picpose.bestphotographyapp.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.picpose.bestphotographyapp.R
import com.picpose.bestphotographyapp.presentation.viewmodels.AuthViewModel
import com.picpose.bestphotographyapp.presentation.viewmodels.OperationState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerifyEmailScreen(
    token: String,
    onBack: () -> Unit,
    onGoToProfile: () -> Unit,
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val verifyState by authViewModel.verifyEmailTokenState.collectAsState()

    LaunchedEffect(token) {
        if (token.isNotBlank()) {
            authViewModel.verifyEmailToken(token)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.verify_email)) },
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
            when (val state = verifyState) {
                is OperationState.Loading -> {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(stringResource(R.string.verifying_email))
                }
                is OperationState.Success -> {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Start
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = onGoToProfile, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.go_to_profile))
                    }
                }
                is OperationState.Error -> {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Start
                    )
                }
                else -> Unit
            }
        }
    }
}
