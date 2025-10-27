package com.picpose.bestphotographyapp.presentation.screens

import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.picpose.bestphotographyapp.presentation.components.EdgeToEdgeScaffold
import com.picpose.bestphotographyapp.presentation.viewmodels.AppSettingsViewModel
import com.picpose.bestphotographyapp.presentation.viewmodels.AppSettingsUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(
    onNavigateBack: () -> Unit,
    appSettingsViewModel: AppSettingsViewModel = hiltViewModel()
) {
    val state by appSettingsViewModel.state.collectAsState()

    // Load settings when first opened
    LaunchedEffect(Unit) {
        appSettingsViewModel.loadAppSettings()
    }

    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val isDarkTheme = colorScheme.background.luminance() < 0.5

    // 🧭 Use EdgeToEdgeScaffold for perfect top/bottom layout
    EdgeToEdgeScaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy Policy", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorScheme.surface.copy(alpha = 0.95f),
                    titleContentColor = colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colorScheme.background)
                .padding(innerPadding)
        ) {
            when (state) {
                is AppSettingsUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                is AppSettingsUiState.Success -> {
                    val privacyPolicyHtml =
                        (state as AppSettingsUiState.Success).settings.policies.privacyPolicyHtml

                    if (privacyPolicyHtml.isNotBlank()) {
                        AndroidView(
                            factory = { context ->
                                WebView(context).apply {
                                    setBackgroundColor(colorScheme.background.toArgb())
                                    settings.javaScriptEnabled = false
                                    settings.loadWithOverviewMode = true
                                    settings.useWideViewPort = true
                                }
                            },
                            update = { webView ->
                                // ✅ Dark/Light mode aware HTML style
                                val bgColor = colorScheme.background.toArgb()
                                val textColor = colorScheme.onBackground.toArgb()
                                val linkColor = colorScheme.primary.toArgb()

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
                                                background-color: #${Integer.toHexString(bgColor).substring(2)};
                                                color: #${Integer.toHexString(textColor).substring(2)};
                                                transition: background-color 0.3s, color 0.3s;
                                            }
                                            h1, h2, h3 {
                                                color: #${Integer.toHexString(linkColor).substring(2)};
                                            }
                                            a {
                                                color: #${Integer.toHexString(linkColor).substring(2)};
                                                text-decoration: none;
                                            }
                                            a:hover {
                                                text-decoration: underline;
                                            }
                                            p {
                                                margin-bottom: 12px;
                                            }
                                        </style>
                                    </head>
                                    <body>
                                        $privacyPolicyHtml
                                    </body>
                                    </html>
                                """.trimIndent()

                                webView.loadDataWithBaseURL(
                                    null,
                                    htmlContent,
                                    "text/html",
                                    "UTF-8",
                                    null
                                )
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
                                color = colorScheme.onSurfaceVariant
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
                            color = colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { appSettingsViewModel.loadAppSettings(forceRefresh = true) },
                            colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary)
                        ) {
                            Text("Retry", color = colorScheme.onPrimary)
                        }
                    }
                }

                AppSettingsUiState.Idle -> {
                    // Idle state (nothing to show yet)
                }
            }
        }
    }
}
