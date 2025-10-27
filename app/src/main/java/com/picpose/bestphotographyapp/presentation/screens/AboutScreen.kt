package com.picpose.bestphotographyapp.presentation.screens

import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.picpose.bestphotographyapp.presentation.components.EdgeToEdgeScaffold
import com.picpose.bestphotographyapp.presentation.viewmodels.AppSettingsViewModel
import com.picpose.bestphotographyapp.presentation.viewmodels.AppSettingsUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    viewModel: AppSettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    // Load app settings when first opened
    LaunchedEffect(Unit) {
        viewModel.loadAppSettings()
    }

    val colorScheme = MaterialTheme.colorScheme

    // ✅ Use EdgeToEdgeScaffold for consistent padding and theming
    EdgeToEdgeScaffold(
        topBar = {
            TopAppBar(
                title = { Text("About App", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                is AppSettingsUiState.Success -> {
                    val settings = (state as AppSettingsUiState.Success).settings
                    val aboutHtml = settings.about.html

                    // 🧭 If HTML content available, load it in WebView with theme support
                    if (aboutHtml.isNotBlank()) {
                        AndroidView(
                            factory = { context ->
                                WebView(context).apply {
                                    /*settings.javaScriptEnabled = false
                                    settings.loadWithOverviewMode = true
                                    settings.useWideViewPort = true*/
                                    setBackgroundColor(colorScheme.background.toArgb())
                                }
                            },
                            update = { webView ->
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
                                        $aboutHtml
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
                        // 🧩 Fallback: plain text About info
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item {
                                Text(
                                    text = settings.appName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 22.sp,
                                    color = colorScheme.onSurface
                                )
                                Text(
                                    text = settings.tagline,
                                    fontSize = 14.sp,
                                    color = colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = settings.about.text.ifBlank { settings.description },
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = colorScheme.onBackground,
                                        lineHeight = 22.sp
                                    )
                                )

                                Spacer(modifier = Modifier.height(24.dp))
                                Divider(color = colorScheme.outlineVariant.copy(alpha = 0.3f))
                                Spacer(modifier = Modifier.height(16.dp))

                                // 📦 Version info
                                Text(
                                    text = "Version",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = colorScheme.primary
                                )
                                Text(
                                    text = "1.0.0",
                                    fontSize = 14.sp,
                                    color = colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                // 👨‍💻 Developer info
                                if (settings.adminName.isNotBlank()) {
                                    Text(
                                        text = "Developer",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        color = colorScheme.primary
                                    )
                                    Text(
                                        text = settings.adminName,
                                        fontSize = 14.sp,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                }
                            }
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
                            "Failed to load About information",
                            style = MaterialTheme.typography.titleMedium,
                            color = colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.loadAppSettings(forceRefresh = true) },
                            colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary)
                        ) {
                            Text("Retry", color = colorScheme.onPrimary)
                        }
                    }
                }

                AppSettingsUiState.Idle -> Unit
            }
        }
    }
}
