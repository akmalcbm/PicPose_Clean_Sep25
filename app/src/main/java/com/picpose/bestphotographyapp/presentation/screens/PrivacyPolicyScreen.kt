package com.picpose.bestphotographyapp.presentation.screens

import android.webkit.WebView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.picpose.bestphotographyapp.presentation.viewmodels.AppSettingsViewModel
import com.picpose.bestphotographyapp.presentation.viewmodels.AppSettingsUiState

/**
 * Privacy Policy Screen
 * Displays privacy policy content from API
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(
    onNavigateBack: () -> Unit,
    appSettingsViewModel: AppSettingsViewModel = hiltViewModel()
) {
    val state by appSettingsViewModel.state
    
    // Load settings if not already loaded
    LaunchedEffect(Unit) {
        appSettingsViewModel.loadAppSettings()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy Policy", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (state) {
                is AppSettingsUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                
                is AppSettingsUiState.Success -> {
                    val privacyPolicyHtml = (state as AppSettingsUiState.Success).settings.policies.privacyPolicyHtml
                    
                    if (privacyPolicyHtml.isNotBlank()) {
                        AndroidView(
                            factory = { context ->
                                WebView(context).apply {
                                    settings.javaScriptEnabled = false
                                    settings.loadWithOverviewMode = true
                                    settings.useWideViewPort = true
                                }
                            },
                            update = { webView ->
                                val htmlContent = """
                                    <!DOCTYPE html>
                                    <html>
                                    <head>
                                        <meta name="viewport" content="width=device-width, initial-scale=1.0">
                                        <style>
                                            body {
                                                font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                                                padding: 16px;
                                                line-height: 1.6;
                                                color: #333;
                                            }
                                            h1, h2, h3 { color: #1976D2; }
                                            p { margin-bottom: 12px; }
                                        </style>
                                    </head>
                                    <body>
                                        $privacyPolicyHtml
                                    </body>
                                    </html>
                                """.trimIndent()
                                webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp)
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                "No Privacy Policy Available",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                is AppSettingsUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "Failed to load Privacy Policy",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { appSettingsViewModel.loadAppSettings(forceRefresh = true) }) {
                            Text("Retry")
                        }
                    }
                }
                
                AppSettingsUiState.Idle -> {
                    // Initial state - loading will trigger automatically
                }
            }
        }
    }
}
