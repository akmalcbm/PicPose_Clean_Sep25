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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.picpose.bestphotographyapp.presentation.components.EdgeToEdgeScaffold
import com.picpose.bestphotographyapp.presentation.viewmodels.AppSettingsViewModel
import com.picpose.bestphotographyapp.presentation.viewmodels.AppSettingsUiState

/**
 * Terms & Conditions Screen
 * Displays themed terms & conditions from API with edge-to-edge layout.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermsScreen(
    onNavigateBack: () -> Unit,
    appSettingsViewModel: AppSettingsViewModel = hiltViewModel()
) {
    val state by appSettingsViewModel.state.collectAsState()

    // Load terms if not already loaded
    LaunchedEffect(Unit) {
        appSettingsViewModel.loadAppSettings()
    }

    val colorScheme = MaterialTheme.colorScheme

    // 🧭 Use EdgeToEdgeScaffold for correct spacing and adaptive theme
    EdgeToEdgeScaffold(
        topBar = {
            TopAppBar(
                title = { Text("Terms & Conditions", fontWeight = FontWeight.Bold) },
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
                    val termsHtml =
                        (state as AppSettingsUiState.Success).settings.policies.termsConditionsHtml

                    if (termsHtml.isNotBlank()) {
                        AndroidView(
                            factory = { context ->
                                WebView(context).apply {
                                    settings.javaScriptEnabled = false
                                    settings.loadWithOverviewMode = true
                                    settings.useWideViewPort = true
                                    setBackgroundColor(colorScheme.background.toArgb())
                                }
                            },
                            update = { webView ->
                                // Theme-aware HTML styling for dark/light mode
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
                                        $termsHtml
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
                                "No Terms & Conditions Available",
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
                            "Failed to load Terms & Conditions",
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
                    // Initial state - handled automatically
                }
            }
        }
    }
}
