package com.picpose.bestphotographyapp.presentation.screens

import android.app.Activity
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.SubcomposeAsyncImage
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import com.picpose.bestphotographyapp.presentation.components.AIPromptCard
import com.picpose.bestphotographyapp.presentation.viewmodels.AIPromptViewModel
import kotlinx.coroutines.launch

enum class ViewMode { GRID, LIST }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllAIPromptsScreen(
    onBack: () -> Unit,
    onPromptClick: (String) -> Unit = {},
    viewModel: AIPromptViewModel = hiltViewModel(),
    initialCategory: String? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var showSearch by remember { mutableStateOf(false) }
    var viewMode by remember { mutableStateOf(ViewMode.GRID) }

    // ✅ Edge-to-edge setup for Android 11+
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity?.window?.let { WindowCompat.setDecorFitsSystemWindows(it, false) }
        }
    }

    // ✅ Initial data load
    LaunchedEffect(Unit) {
        viewModel.loadAllPrompts()
        viewModel.loadCategories()
        initialCategory?.takeIf { it.isNotBlank() && it != "All" }?.let {
            viewModel.updateSelectedCategory(it)
        }
    }

    // ✅ Error handling
    LaunchedEffect(uiState.error) {
        uiState.error?.let { msg ->
            coroutineScope.launch {
                snackbarHostState.showSnackbar(msg)
                viewModel.clearError()
            }
        }
    }

    val displayPrompts = remember(uiState.allPrompts, uiState.searchQuery, uiState.selectedCategory) {
        uiState.allPrompts.filter { prompt ->
            val matchesSearch = uiState.searchQuery.isBlank() ||
                    prompt.title?.contains(uiState.searchQuery, true) == true ||
                    prompt.fullPrompt?.contains(uiState.searchQuery, true) == true ||
                    prompt.shortPrompt?.contains(uiState.searchQuery, true) == true
            val matchesCategory = uiState.selectedCategory == "All" ||
                    prompt.category == uiState.selectedCategory
            matchesSearch && matchesCategory
        }
    }

    val categories = uiState.categories

    // ✅ Proper Scaffold (edge-to-edge)
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (uiState.selectedCategory != "All")
                            "${uiState.selectedCategory} Prompts (${uiState.totalPromptsCount})"
                        else
                            "All Prompts (${uiState.totalPromptsCount})"
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadAllPrompts() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(
                            if (showSearch) Icons.Default.SearchOff else Icons.Default.Search,
                            contentDescription = "Search"
                        )
                    }
                    IconButton(onClick = {
                        viewMode = if (viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID
                    }) {
                        Icon(
                            if (viewMode == ViewMode.GRID)
                                Icons.AutoMirrored.Filled.ViewList
                            else
                                Icons.Default.GridView,
                            contentDescription = "Change View"
                        )
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0) // 🚫 disable auto padding
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                // ✅ horizontal safe area only
                .padding(
                    WindowInsets.safeDrawing
                        .only(WindowInsetsSides.Horizontal)
                        .asPaddingValues()
                )
        ) {

            if (showSearch) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    placeholder = { Text("Search prompts...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            if (categories.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categories) { category ->
                        FilterChip(
                            onClick = { viewModel.updateSelectedCategory(category) },
                            label = { Text(category) },
                            selected = uiState.selectedCategory == category,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF6366F1),
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }

            when {
                uiState.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                displayPrompts.isEmpty() -> {
                    EmptyPromptsState(
                        searchQuery = uiState.searchQuery,
                        selectedCategory = uiState.selectedCategory,
                        onClearFilters = {
                            viewModel.updateSearchQuery("")
                            viewModel.updateSelectedCategory("All")
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 80.dp)
                    )
                }

                else -> {
                    val manager = clipboardManager
                    when (viewMode) {
                        ViewMode.GRID -> {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 160.dp),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start = 12.dp,
                                    end = 12.dp,
                                    top = 8.dp,
                                    bottom = 100.dp // ✅ enough for bottom nav
                                ),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(displayPrompts, key = { it.id ?: it.hashCode() }) { prompt ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                Log.d("PromptClick", "Clicked Prompt → id=${prompt.id}")
                                                onPromptClick(prompt.id?.toString() ?: "")
                                                       },
                                        shape = RoundedCornerShape(14.dp),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            SubcomposeAsyncImage(
                                                model = prompt.imageUrl,
                                                contentDescription = prompt.title,
                                                modifier = Modifier
                                                    .height(160.dp)
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)),
                                                contentScale = ContentScale.Crop,
                                                loading = {
                                                    Box(
                                                        Modifier
                                                            .fillMaxSize()
                                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        CircularProgressIndicator(
                                                            color = MaterialTheme.colorScheme.primary,
                                                            strokeWidth = 2.dp,
                                                            modifier = Modifier.size(28.dp)
                                                        )
                                                    }
                                                },
                                                error = {
                                                    Box(
                                                        Modifier
                                                            .fillMaxSize()
                                                            .background(MaterialTheme.colorScheme.errorContainer),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            Icons.Default.BrokenImage,
                                                            contentDescription = "Image not found",
                                                            tint = MaterialTheme.colorScheme.error
                                                        )
                                                    }
                                                }
                                            )

                                            Column(
                                                modifier = Modifier
                                                    .padding(12.dp)
                                                    .fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = prompt.title ?: "",
                                                    style = MaterialTheme.typography.titleSmall.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    ),
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = prompt.shortPrompt ?: "",
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    ),
                                                    maxLines = 3,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        ViewMode.LIST -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 8.dp,
                                    bottom = 100.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(displayPrompts, key = { it.id ?: it.hashCode() }) { prompt ->
                                    AIPromptCard(
                                        prompt = prompt,
                                        onClick = { onPromptClick(prompt.id?.toString() ?: "") },
                                        onCopy = {
                                            val textToCopy =
                                                prompt.shortPrompt ?: prompt.fullPrompt ?: ""
                                            manager.setText(AnnotatedString(textToCopy))
                                            Toast.makeText(
                                                context,
                                                "Prompt copied!",
                                                Toast.LENGTH_SHORT
                                            ).show()
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
        }
    }
}

/* ------------------------
   Helper Composables
   ------------------------ */

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
        Text("🔍", style = MaterialTheme.typography.displayLarge)
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
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
            ) {
                Text("Clear Filters")
            }
        }
    }
}
