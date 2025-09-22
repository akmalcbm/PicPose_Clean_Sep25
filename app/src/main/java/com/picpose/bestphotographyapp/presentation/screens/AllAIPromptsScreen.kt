package com.picpose.bestphotographyapp.presentation.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.picpose.bestphotographyapp.presentation.components.AIPromptCard
import com.picpose.bestphotographyapp.presentation.viewmodels.AIPromptViewModel
import kotlinx.coroutines.launch

// Define ViewMode enum
enum class ViewMode {
    GRID, LIST
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllAIPromptsScreen(
    onBack: () -> Unit,
    onPromptClick: (String) -> Unit = {}, // navigation callback
    viewModel: AIPromptViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var showSearch by remember { mutableStateOf(false) }
    var viewMode by remember { mutableStateOf(ViewMode.GRID) }

    // Initial load (run once)
    LaunchedEffect(Unit) {
        viewModel.loadAllPrompts()
        viewModel.refreshFavoriteState()
        viewModel.loadCategories()
    }

    // Show errors as Snackbars and clear after showing
    LaunchedEffect(uiState.error) {
        uiState.error?.let { msg ->
            coroutineScope.launch {
                snackbarHostState.showSnackbar(msg)
                viewModel.clearError()
            }
        }
    }

    // Derived display list (filtered)
    val displayPrompts by remember(uiState.allPrompts, uiState.searchQuery, uiState.selectedCategory) {
        derivedStateOf { viewModel.getFilteredPrompts() }
    }

    val categories = uiState.categories

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (uiState.selectedCategory != "All")
                            "${uiState.selectedCategory} Prompts (${displayPrompts.size})"
                        else
                            "All AI Prompts (${displayPrompts.size})"
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Refresh action
                    IconButton(onClick = { viewModel.loadAllPrompts() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }

                    // Search Toggle
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(
                            if (showSearch) Icons.Default.SearchOff else Icons.Default.Search,
                            contentDescription = "Search"
                        )
                    }

                    // View Mode Toggle
                    IconButton(onClick = {
                        viewMode = if (viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID
                    }) {
                        Icon(
                            if (viewMode == ViewMode.GRID) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                            contentDescription = "Change View"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->

        Column(modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
        ) {
            // Search Bar
            if (showSearch) {
                SearchBar(
                    query = uiState.searchQuery,
                    onQueryChange = viewModel::searchPrompts,
                    onClear = { viewModel.searchPrompts("") }, // Clear search
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                )
            }

            // Category Filter Chips
            if (categories.isNotEmpty()) {
                CategoryFilterRow(
                    categories = categories,
                    selectedCategory = uiState.selectedCategory,
                    onCategorySelected = viewModel::filterByCategory,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                // Loading overlay (centered)
                if (uiState.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    // Empty State
                    if (displayPrompts.isEmpty()) {
                        EmptyPromptsState(
                            searchQuery = uiState.searchQuery,
                            selectedCategory = uiState.selectedCategory,
                            onClearFilters = {
                                viewModel.searchPrompts("")
                                viewModel.filterByCategory("All")
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // Content
                        when (viewMode) {
                            ViewMode.GRID -> {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(2),
                                    contentPadding = PaddingValues(16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(displayPrompts, key = { it.id }) { prompt ->
                                        AIPromptCard(
                                            prompt = prompt,
                                            onClick = { onPromptClick(prompt.id) },
                                            onCopy = {
                                                clipboardManager.setText(AnnotatedString(prompt.fullPrompt ?: ""))
                                                Toast.makeText(context, "Prompt copied!", Toast.LENGTH_SHORT).show()
                                            },
                                            onFavoriteClick = { viewModel.toggleFavorite(prompt) },
                                            showFavoriteIcon = true,
                                            isCompact = true // Smaller version for grid
                                        )
                                    }
                                }
                            }
                            ViewMode.LIST -> {
                                LazyColumn(
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(displayPrompts, key = { it.id }) { prompt ->
                                        AIPromptCard(
                                            prompt = prompt,
                                            onClick = { onPromptClick(prompt.id) },
                                            onCopy = {
                                                clipboardManager.setText(AnnotatedString(prompt.fullPrompt ?: ""))
                                                Toast.makeText(context, "Prompt copied!", Toast.LENGTH_SHORT).show()
                                            },
                                            onFavoriteClick = { viewModel.toggleFavorite(prompt) },
                                            showFavoriteIcon = true,
                                            isCompact = false // Full version for list
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
}

/* ------------------------
   Reused helper composables
   (kept mostly same as your original)
   ------------------------ */

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = { Text("Search prompts...") },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null)
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
private fun CategoryFilterRow(
    categories: List<String>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(categories) { category ->
            FilterChip(
                onClick = { onCategorySelected(category) },
                label = { Text(category) },
                selected = selectedCategory == category,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFF6366F1),
                    selectedLabelColor = Color.White
                )
            )
        }
    }
}

@Composable
private fun EmptyPromptsState(
    searchQuery: String,
    selectedCategory: String,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "🔍",
            style = MaterialTheme.typography.displayLarge
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (searchQuery.isNotEmpty()) "No results found" else "No prompts available",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        val message = when {
            searchQuery.isNotEmpty() -> "Try different search terms or clear filters"
            selectedCategory != "All" -> "No prompts found in this category"
            else -> "Check back later for new prompts"
        }

        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = Color(0xFF64748B)
        )

        if (searchQuery.isNotEmpty() || selectedCategory != "All") {
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onClearFilters,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6366F1)
                )
            ) {
                Text("Clear Filters")
            }
        }
    }
}
