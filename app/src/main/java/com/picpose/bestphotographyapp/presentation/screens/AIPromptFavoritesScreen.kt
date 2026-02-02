package com.picpose.bestphotographyapp.presentation.screens

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.picpose.bestphotographyapp.core.utils.setText
import com.picpose.bestphotographyapp.presentation.components.AIPromptCard
import com.picpose.bestphotographyapp.presentation.viewmodels.AIPromptViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIPromptFavoritesScreen(
    onBack: () -> Unit,
    onPromptClick: (String) -> Unit = {},
    onNavigateToAllPrompts: () -> Unit,
    viewModel: AIPromptViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val favoritePrompts by viewModel.favoritePromptsFlow.collectAsState()
    val localEngagementMap by viewModel.localEngagementStates.collectAsState()

    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // ✅ ONLY THIS FIX: Add scroll state to maintain position
    val lazyListState = rememberLazyListState()

    LaunchedEffect(Unit) {
        Log.e("FAV_DEBUG", "FavoritesScreen OPENED - Count: ${favoritePrompts.size}")
    }

    // ✅ Show error only once
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    Scaffold(
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
                                    "${prompt.title}\n${prompt.fullPrompt.orEmpty()}"
                                }
                                coroutineScope.launch {
                                    clipboard.setText(allPrompts, label = "favorites")
                                }
                                Toast.makeText(
                                    context,
                                    "All favorites copied!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share All")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top)
                )
            )
        },
        contentWindowInsets = WindowInsets(0)
    ) { innerPadding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(
                    WindowInsets.safeDrawing
                        .only(WindowInsetsSides.Horizontal)
                        .asPaddingValues()
                )
        ) {

            when {
                // ✅ Loader only when nothing loaded yet
                uiState.isLoading && favoritePrompts.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                // ✅ Empty state
                favoritePrompts.isEmpty() -> {
                    EmptyFavoritesState(
                        onNavigateToAllPrompts = onNavigateToAllPrompts,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // ✅ Favorites list - WITH SCROLL STATE ONLY
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = 12.dp,
                            bottom = 90.dp,
                            start = 16.dp,
                            end = 16.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        state = lazyListState // ✅ ONLY THIS CHANGE
                    ) {
                        items(
                            items = favoritePrompts,
                            key = { it.id }
                        ) { prompt ->

                            val local = localEngagementMap[prompt.id]

                            AIPromptCard(
                                prompt = prompt,
                                localEngagement = local,

                                onClick = {
                                    onPromptClick(prompt.id)
                                },

                                onCopy = {
                                    coroutineScope.launch {
                                        clipboard.setText(
                                            prompt.fullPrompt.orEmpty(),
                                            label = "prompt"
                                        )
                                    }
                                    Toast.makeText(
                                        context,
                                        "Prompt copied!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },

                                onLikeClick = {
                                    viewModel.onLikeClicked(prompt)
                                },

                                onFavoriteClick = {
                                    viewModel.onFavoriteClicked(prompt)
                                },

                                showFavoriteIcon = true,
                                isCompact = false
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyFavoritesState(
    onNavigateToAllPrompts: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 20.dp),
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
            onClick = onNavigateToAllPrompts,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF6366F1)
            )
        ) {
            Icon(Icons.Default.Explore, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Browse Prompts")
        }
    }
}
