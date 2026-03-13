/**
 * ---
 * File: AboutScreen.kt
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.picpose.bestphotographyapp.R
import com.picpose.bestphotographyapp.presentation.settings.AppSettingsViewModel
import com.picpose.bestphotographyapp.presentation.settings.AppSettingsUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    viewModel: AppSettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    // 🔹 Load settings on open
    LaunchedEffect(Unit) {
        viewModel.loadAppSettings()
    }

    val colorScheme = MaterialTheme.colorScheme

    // ✅ Use Scaffold with correct edge-to-edge handling
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_app_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorScheme.surface.copy(alpha = 0.95f),
                    titleContentColor = colorScheme.onSurface
                ),

            )
        },
        contentWindowInsets = WindowInsets(0) // disable auto inset to prevent double space
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
                        Text(
                            stringResource(R.string.loading_about_information),
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }

                is AppSettingsUiState.Success -> {
                    val settings = (state as AppSettingsUiState.Success).settings
                    val aboutHtml = settings.about.html

                    if (aboutHtml.isNotBlank()) {
                        // ✅ Fixed WebView implementation
                        AboutWebView(
                            htmlContent = aboutHtml,
                            backgroundColor = colorScheme.background,
                            textColor = colorScheme.onBackground,
                            accentColor = colorScheme.primary,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // 🧩 Fallback: Plain text About info
                        AboutFallbackContent(
                            settings = settings,
                            colorScheme = colorScheme,
                            modifier = Modifier.fillMaxSize()
                        )
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
                            stringResource(R.string.failed_to_load_about_information),
                            style = MaterialTheme.typography.titleMedium,
                            color = colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = (state as AppSettingsUiState.Error).message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.loadAppSettings(forceRefresh = true) },
                            colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary)
                        ) {
                            Text(stringResource(R.string.retry), color = colorScheme.onPrimary)
                        }

                        // Show cached data if available
                        (state as AppSettingsUiState.Error).cachedSettings?.let { cachedSettings ->
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                stringResource(R.string.showing_cached_information),
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant
                            )
                            AboutFallbackContent(
                                settings = cachedSettings,
                                colorScheme = colorScheme,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(300.dp)
                            )
                        }
                    }
                }

                AppSettingsUiState.Idle -> {
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
 * WebView for displaying HTML content
 */
@Composable
@SuppressLint("SetJavaScriptEnabled")
fun AboutWebView(
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
                    padding: 24px 20px;
                    line-height: 1.6;
                    background-color: $bgHex;
                    color: $textHex;
                    font-size: 16px;
                }
                h1 {
                    font-size: 28px;
                    margin-top: 24px;
                    margin-bottom: 16px;
                    color: $accentHex;
                    font-weight: 600;
                }
                h2 {
                    font-size: 22px;
                    margin-top: 20px;
                    margin-bottom: 12px;
                    color: $accentHex;
                    font-weight: 600;
                }
                h3 {
                    font-size: 18px;
                    margin-top: 16px;
                    margin-bottom: 10px;
                    color: $accentHex;
                    font-weight: 600;
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
                .app-name {
                    font-size: 24px;
                    font-weight: 700;
                    color: $accentHex;
                    margin-bottom: 8px;
                }
                .tagline {
                    font-size: 16px;
                    color: $textHex;
                    opacity: 0.8;
                    margin-bottom: 24px;
                }
                @media (max-width: 600px) {
                    body {
                        padding: 20px 16px;
                        font-size: 15px;
                    }
                    h1 { font-size: 24px; }
                    h2 { font-size: 20px; }
                    h3 { font-size: 17px; }
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

/**
 * Fallback content when HTML is not available
 */
@Composable
fun AboutFallbackContent(
    settings: com.picpose.bestphotographyapp.data.remote.dto.AppSettings,
    colorScheme: ColorScheme,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = settings.appName,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = colorScheme.primary
            )

            if (settings.tagline.isNotBlank()) {
                Text(
                    text = settings.tagline,
                    fontSize = 16.sp,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            val aboutText = settings.about.text.ifBlank { settings.description }
            if (aboutText.isNotBlank()) {
                Text(
                    text = aboutText,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = colorScheme.onBackground,
                        lineHeight = 24.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
            HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(24.dp))

            // App Info Section
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Version
                InfoRow(
                    title = stringResource(R.string.version),
                    value = "1.0.0",
                    colorScheme = colorScheme
                )

                // Developer
                if (settings.adminName.isNotBlank()) {
                    InfoRow(
                        title = stringResource(R.string.developer),
                        value = settings.adminName,
                        colorScheme = colorScheme
                    )
                }

                // Support Email
                if (settings.contact.email.isNotBlank()) {
                    InfoRow(
                        title = stringResource(R.string.support_email),
                        value = settings.contact.email,
                        colorScheme = colorScheme
                    )
                }

                // Support Phone
                if (settings.contact.phone.isNotBlank()) {
                    InfoRow(
                        title = stringResource(R.string.support_phone),
                        value = settings.contact.phone,
                        colorScheme = colorScheme
                    )
                }

                // Google Play
                if (settings.googlePlayUrl.isNotBlank()) {
                    InfoRow(
                        title = stringResource(R.string.google_play),
                        value = stringResource(R.string.download_on_play_store),
                        colorScheme = colorScheme
                    )
                }

                // Last Updated
                if (settings.meta.updatedAt.isNotBlank()) {
                    InfoRow(
                        title = stringResource(R.string.last_updated),
                        value = settings.meta.updatedAt,
                        colorScheme = colorScheme
                    )
                }
            }
        }
    }
}

/**
 * Reusable info row component
 */
@Composable
fun InfoRow(
    title: String,
    value: String,
    colorScheme: ColorScheme
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = colorScheme.primary
        )
        Text(
            text = value,
            fontSize = 16.sp,
            color = colorScheme.onSurfaceVariant
        )
    }
}
