package com.picpose.bestphotographyapp.presentation.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.picpose.bestphotographyapp.presentation.components.AIPromptCard
import com.picpose.bestphotographyapp.presentation.components.EdgeToEdgeScaffold
import com.picpose.bestphotographyapp.presentation.viewmodels.AIPromptViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIPromptFavoritesScreen(
    onBack: () -> Unit,
    onPromptClick: (String) -> Unit = {},
    viewModel: AIPromptViewModel = viewModel()
) {
    val favoritePrompts by viewModel.favoritePrompts.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // ✅ Load favorites safely
    LaunchedEffect(Unit) {
        if (favoritePrompts.isEmpty()) viewModel.loadFavoritePrompts()
    }

    // ✅ Error messages once per error
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    // ✅ Use our reusable EdgeToEdgeScaffold
    EdgeToEdgeScaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Favorites (${favoritePrompts.size})",
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (favoritePrompts.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                val allPrompts = favoritePrompts.joinToString("\n\n") { prompt ->
                                    "${prompt.title}\n${prompt.fullPrompt ?: ""}"
                                }
                                clipboardManager.setText(AnnotatedString(allPrompts))
                                Toast.makeText(
                                    context,
                                    "All favorites copied!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                )
            )
        },
        snackbarHostState = snackbarHostState
    ) { innerPadding ->

        when {
            uiState.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            favoritePrompts.isEmpty() -> {
                EmptyFavoritesState(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .padding(innerPadding),
                    contentPadding = PaddingValues(bottom = 48.dp, top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(favoritePrompts, key = { it.id ?: it.hashCode() }) { prompt ->
                        AIPromptCard(
                            prompt = prompt,
                            onClick = { prompt.id?.let(onPromptClick) },
                            onCopy = {
                                clipboardManager.setText(AnnotatedString(prompt.fullPrompt ?: ""))
                                Toast.makeText(context, "Prompt copied!", Toast.LENGTH_SHORT).show()
                            },
                            onFavoriteClick = { viewModel.toggleFavorite(prompt) },
                            showFavoriteIcon = true,
                            isCompact = false
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyFavoritesState(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("💔", style = MaterialTheme.typography.displayLarge)

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "No Favorite Prompts",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Start adding your favorite prompts to see them here!",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { /* Navigate to browse */ },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
        ) {
            Icon(Icons.Default.Explore, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Browse Prompts")
        }
    }
}
