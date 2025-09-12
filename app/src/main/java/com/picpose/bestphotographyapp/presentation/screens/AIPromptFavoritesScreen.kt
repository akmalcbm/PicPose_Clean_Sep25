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
import com.picpose.bestphotographyapp.presentation.viewmodels.AIPromptViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIPromptFavoritesScreen(
    onBack: () -> Unit,
    onPromptClick: (String) -> Unit = {}, // Add navigation callback
    viewModel: AIPromptViewModel = viewModel()
) {
    // Refresh favorites when screen opens
    LaunchedEffect(Unit) {
        viewModel.loadFavoritePrompts()
    }

    val favoritePrompts by viewModel.favoritePrompts.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    // Load favorites when screen opens
    LaunchedEffect(Unit) {
        viewModel.loadFavoritePrompts()
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Top App Bar
        TopAppBar(
            title = {
                Text("Favorite Prompts (${favoritePrompts.size})")
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                if (favoritePrompts.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            // Export all favorites to clipboard
                            val allPrompts = favoritePrompts.joinToString("\n\n") { prompt ->
                                "${prompt.title}\n${prompt.fullPrompt}"
                            }
                            clipboardManager.setText(AnnotatedString(allPrompts))
                            Toast.makeText(context, "All favorites exported to clipboard!", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Export All")
                    }
                }
            }
        )

        // Loading State
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        // Empty State
        else if (favoritePrompts.isEmpty()) {
            EmptyFavoritesState(
                modifier = Modifier.fillMaxSize()
            )
        }
        // Favorites List
        else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(favoritePrompts) { prompt ->
                    AIPromptCard(
                        prompt = prompt,
                        onClick = { onPromptClick(prompt.id) }, // Navigate to detail
                        onCopy = {
                            clipboardManager.setText(AnnotatedString(prompt.fullPrompt))
                            Toast.makeText(context, "Prompt copied!", Toast.LENGTH_SHORT).show()
                        },
                        onFavoriteClick = { viewModel.toggleFavorite(prompt) },
                        showFavoriteIcon = true,
                        isCompact = false
                    )
                }
            }
        }

        // Error State
        uiState.error?.let { error ->
            LaunchedEffect(error) {
                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                viewModel.clearError()
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
        // Empty state icon
        Text(
            text = "💔",
            style = MaterialTheme.typography.displayLarge
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "No Favorite Prompts",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1E293B)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Start exploring AI prompts and add your favorites here!",
            style = MaterialTheme.typography.bodyLarge,
            color = Color(0xFF64748B),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { /* Navigate to browse prompts */ },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF6366F1)
            )
        ) {
            Icon(
                Icons.Default.Explore,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Browse Prompts")
        }
    }
}
