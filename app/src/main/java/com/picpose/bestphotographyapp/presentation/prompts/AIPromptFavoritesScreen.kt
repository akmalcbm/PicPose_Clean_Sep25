/**
 * ---
 * File: AIPromptFavoritesScreen.kt
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

package com.picpose.bestphotographyapp.presentation.prompts

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.picpose.bestphotographyapp.R
import com.picpose.bestphotographyapp.utils.setText
import com.picpose.bestphotographyapp.components.common.AIPromptCard
import com.picpose.bestphotographyapp.presentation.search.SearchMatchers
import com.picpose.bestphotographyapp.presentation.prompts.AIPromptViewModel
import kotlinx.coroutines.delay
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
    var favoritesSearchQuery by rememberSaveable { mutableStateOf("") }
    var debouncedFavoritesQuery by rememberSaveable { mutableStateOf("") }

    // ✅ ONLY THIS FIX: Add scroll state to maintain position
    val lazyListState = rememberLazyListState()

    LaunchedEffect(favoritesSearchQuery) {
        delay(300)
        debouncedFavoritesQuery = favoritesSearchQuery
    }

    val normalizedFavoritesQuery = remember(debouncedFavoritesQuery) {
        debouncedFavoritesQuery.trim()
    }
    val filteredFavoritePrompts = remember(favoritePrompts, normalizedFavoritesQuery) {
        favoritePrompts.filter { SearchMatchers.matchesAIPrompt(it, normalizedFavoritesQuery) }
    }

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
                        text = pluralStringResource(
                            R.plurals.favorites_count,
                            filteredFavoritePrompts.size,
                            filteredFavoritePrompts.size
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    if (filteredFavoritePrompts.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                val allPrompts = filteredFavoritePrompts.joinToString("\n\n") { prompt ->
                                    "${prompt.title}\n${prompt.fullPrompt.orEmpty()}"
                                }
                                coroutineScope.launch {
                                    clipboard.setText(allPrompts, label = "favorites")
                                }
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.all_favorites_copied),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        ) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.share_all))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
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
                    Column(modifier = Modifier.fillMaxSize()) {
                        OutlinedTextField(
                            value = favoritesSearchQuery,
                            onValueChange = { favoritesSearchQuery = it },
                            placeholder = { Text(stringResource(R.string.search_prompts_placeholder)) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = {
                                if (favoritesSearchQuery.isNotBlank()) {
                                    IconButton(onClick = { favoritesSearchQuery = "" }) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = stringResource(R.string.clear)
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )

                        if (normalizedFavoritesQuery.isNotBlank() && filteredFavoritePrompts.isEmpty()) {
                            EmptyPromptsSearchState(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp)
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    top = 12.dp,
                                    bottom = 90.dp,
                                    start = 16.dp,
                                    end = 16.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                state = lazyListState
                            ) {
                                items(
                                    items = filteredFavoritePrompts,
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
                                                context.getString(R.string.prompt_copied_toast),
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
    }
}

@Composable
private fun EmptyPromptsSearchState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.no_results_found),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.try_different_search_terms_or_clear_filters),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
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
        Text(stringResource(R.string.empty_favorites_icon), style = MaterialTheme.typography.displayLarge)

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.no_favorite_prompts_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.no_favorite_prompts_description),
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
            Text(stringResource(R.string.browse_prompts_button))
        }
    }
}
