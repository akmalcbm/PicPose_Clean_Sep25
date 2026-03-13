/**
 * ---
 * File: PrivacyPolicyScreen.kt
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

package com.picpose.bestphotographyapp.presentation.about

import android.annotation.SuppressLint
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.picpose.bestphotographyapp.R
import com.picpose.bestphotographyapp.presentation.settings.AppSettingsViewModel
import com.picpose.bestphotographyapp.presentation.settings.AppSettingsUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(
    onNavigateBack: () -> Unit,
    appSettingsViewModel: AppSettingsViewModel = hiltViewModel()
) {
    val state by appSettingsViewModel.state.collectAsState()

    // 🔹 Load settings once when opened
    LaunchedEffect(Unit) {
        appSettingsViewModel.loadAppSettings()
    }

    val colorScheme = MaterialTheme.colorScheme

    // ✅ Proper Scaffold for consistent edge-to-edge layout
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.privacy_policy_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorScheme.surface.copy(alpha = 0.95f),
                    titleContentColor = colorScheme.onSurface
                ),
            )
        },
        contentWindowInsets = WindowInsets(0) // disable auto inset to avoid double spacing
    ) { innerPadding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colorScheme.background)
                .padding(innerPadding)
                .padding(
                    WindowInsets.safeDrawing
                        .only(WindowInsetsSides.Horizontal)
                        .asPaddingValues()
                )
        ) {
            when (state) {
                is AppSettingsUiState.Loading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringResource(R.string.loading_privacy_policy), color = colorScheme.onSurfaceVariant)
                    }
                }

                is AppSettingsUiState.Success -> {
                    val privacyPolicyHtml =
                        (state as AppSettingsUiState.Success).settings.policies.privacyPolicyHtml

                    if (privacyPolicyHtml.isNotBlank()) {
                        // ✅ Fixed WebView implementation
                        PrivacyPolicyWebView(
                            htmlContent = privacyPolicyHtml,
                            backgroundColor = colorScheme.background,
                            textColor = colorScheme.onBackground,
                            accentColor = colorScheme.primary,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // 🧩 Fallback text if HTML is blank
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 20.dp, vertical = 24.dp)
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = stringResource(R.string.no_privacy_policy_available),
                                style = MaterialTheme.typography.titleMedium,
                                color = colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = stringResource(R.string.please_check_back_later),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp)
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
                            text = stringResource(R.string.failed_to_load_privacy_policy),
                            style = MaterialTheme.typography.titleMedium,
                            color = colorScheme.error
                        )
                        val errorState = state as AppSettingsUiState.Error
                        if (errorState.message.isNotEmpty()) {
                            Text(
                                text = errorState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))

                        // Show cached data if available
                        errorState.cachedSettings?.let { cachedSettings ->
                            if (cachedSettings.policies.privacyPolicyHtml.isNotBlank()) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = stringResource(R.string.showing_cached_version),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(bottom = 16.dp)
                                    )
                                    PrivacyPolicyWebView(
                                        htmlContent = cachedSettings.policies.privacyPolicyHtml,
                                        backgroundColor = colorScheme.background,
                                        textColor = colorScheme.onBackground,
                                        accentColor = colorScheme.primary,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = { appSettingsViewModel.loadAppSettings(forceRefresh = true) },
                            colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary)
                        ) {
                            Text(stringResource(R.string.retry), color = colorScheme.onPrimary)
                        }
                    }
                }

                AppSettingsUiState.Idle -> {
                    // Show loading initially
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

/**
 * Helper function to convert Color to hex string without alpha
 */
private fun Color.toHexWithoutAlpha(): String {
    val rgb = toArgb() and 0xFFFFFF // Remove alpha component
    return String.format("#%06X", rgb)
}

/**
 * Separate composable for WebView to handle dark/light theme properly
 */
@Composable
@SuppressLint("SetJavaScriptEnabled")
fun PrivacyPolicyWebView(
    htmlContent: String,
    backgroundColor: Color,
    textColor: Color,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val bgHex = backgroundColor.toHexWithoutAlpha()
    val textHex = textColor.toHexWithoutAlpha()
    val accentHex = accentColor.toHexWithoutAlpha()

    val styledHtml = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <style>
                * {
                    margin: 0;
                    padding: 0;
                    box-sizing: border-box;
                }
                body {
                    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                    padding: 20px;
                    line-height: 1.6;
                    background-color: $bgHex;
                    color: $textHex;
                    font-size: 16px;
                }
                h1 {
                    font-size: 24px;
                    margin-top: 24px;
                    margin-bottom: 16px;
                    color: $accentHex;
                    border-bottom: 2px solid $accentHex;
                    padding-bottom: 8px;
                }
                h2 {
                    font-size: 20px;
                    margin-top: 20px;
                    margin-bottom: 12px;
                    color: $accentHex;
                }
                h3 {
                    font-size: 18px;
                    margin-top: 16px;
                    margin-bottom: 10px;
                    color: $accentHex;
                }
                p {
                    margin-bottom: 16px;
                    text-align: justify;
                }
                ul, ol {
                    margin-bottom: 16px;
                    padding-left: 24px;
                }
                li {
                    margin-bottom: 8px;
                }
                a {
                    color: $accentHex;
                    text-decoration: none;
                    font-weight: 500;
                }
                a:hover {
                    text-decoration: underline;
                }
                strong, b {
                    font-weight: 600;
                }
                .container {
                    max-width: 800px;
                    margin: 0 auto;
                }
                @media (max-width: 600px) {
                    body {
                        padding: 16px;
                        font-size: 15px;
                    }
                    h1 { font-size: 22px; }
                    h2 { font-size: 18px; }
                    h3 { font-size: 16px; }
                }
            </style>
        </head>
        <body>
            <div class="container">
                $htmlContent
            </div>
        </body>
        </html>
    """.trimIndent()

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.apply {
                    javaScriptEnabled = false
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    builtInZoomControls = true
                    displayZoomControls = false
                    setSupportZoom(true)
                    domStorageEnabled = true
                    loadsImagesAutomatically = true
                }
                setBackgroundColor(backgroundColor.toArgb())
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(
                null,
                styledHtml,
                "text/html",
                "UTF-8",
                null
            )
        },
        modifier = modifier
    )
}
