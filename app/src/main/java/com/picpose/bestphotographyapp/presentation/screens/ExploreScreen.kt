package com.picpose.bestphotographyapp.presentation.screens

import android.app.Activity
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.picpose.bestphotographyapp.data.models.AIPrompt
import com.picpose.bestphotographyapp.data.models.GuidePost
import com.picpose.bestphotographyapp.presentation.components.AIPromptCard
import com.picpose.bestphotographyapp.presentation.components.GuidePostCard
import com.picpose.bestphotographyapp.presentation.viewmodels.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ExploreScreen(
    onNavigateToPromptDetail: (AIPrompt) -> Unit = {},
    onNavigateToGuidePostDetail: (GuidePost) -> Unit = {},
    viewModel: ExploreViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // ✅ Enable edge-to-edge mode for consistent behavior across devices
    val activity = context as? Activity
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity?.window?.let {
                WindowCompat.setDecorFitsSystemWindows(it, false)
            }
        }
    }

    // Handle error messages
    LaunchedEffect(uiState.error) {
        uiState.error?.let { message ->
            coroutineScope.launch {
                snackbarHostState.showSnackbar(message)
                viewModel.clearError()
            }
        }
    }

    // Infinite scroll handling
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastVisibleIndex ->
                if (lastVisibleIndex != null &&
                    lastVisibleIndex >= uiState.content.size - 3 &&
                    uiState.hasMore &&
                    !uiState.isLoading
                ) {
                    viewModel.loadMore()
                }
            }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(), // Prevent keyboard overlap
        topBar = {
            ExploreTopBar(
                searchQuery = uiState.searchQuery,
                onSearchQueryChange = viewModel::updateSearchQuery,
                onRefresh = viewModel::refresh
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // ✅ Prevent double padding
        contentWindowInsets = WindowInsets(0)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Filters section
            FilterChipsSection(
                categories = uiState.categories,
                selectedCategory = uiState.selectedCategory,
                selectedContentFilter = uiState.selectedContentFilter,
                selectedSortOption = uiState.selectedSortOption,
                onCategorySelected = viewModel::updateCategory,
                onContentFilterSelected = viewModel::updateContentFilter,
                onSortOptionSelected = viewModel::updateSortOption
            )

            // Main content
            when {
                uiState.isLoading && uiState.content.isEmpty() -> {
                    LoadingSection()
                }

                uiState.content.isEmpty() && !uiState.isLoading -> {
                    EmptyStateSection(
                        searchQuery = uiState.searchQuery,
                        selectedCategory = uiState.selectedCategory,
                        onClearFilters = {
                            viewModel.updateSearchQuery("")
                            viewModel.updateCategory("All")
                            viewModel.updateContentFilter(ContentFilter.ALL)
                        }
                    )
                }

                else -> {
                    PullToRefreshBox(
                        isRefreshing = uiState.isRefreshing,
                        onRefresh = viewModel::refresh
                    ) {
                        LazyColumn(
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 8.dp,
                                bottom = 90.dp // ✅ Prevent overlap with bottom nav
                            ),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            itemsIndexed(
                                items = uiState.content,
                                key = { index, content ->
                                    when (content) {
                                        is ExploreContent.AIPromptContent -> "AIPROMPT_${content.prompt.id}_$index"
                                        is ExploreContent.GuidePostContent -> "GUIDEPOST_${content.guidePost.id}_$index"
                                        else -> "CONTENT_${content.hashCode()}_$index"
                                    }
                                }
                            ) { _, content ->
                                when (content) {
                                    is ExploreContent.AIPromptContent -> {
                                        AIPromptCard(
                                            prompt = content.prompt,
                                            onClick = { onNavigateToPromptDetail(content.prompt) },
                                            onCopy = {
                                                val textToCopy = content.prompt.shortPrompt
                                                    ?: content.prompt.fullPrompt
                                                    ?: ""
                                                clipboardManager.setText(AnnotatedString(textToCopy))
                                                Toast.makeText(context, "Prompt copied!", Toast.LENGTH_SHORT).show()
                                            },
                                            onFavoriteClick = { prompt ->
                                                viewModel.togglePromptFavorite(prompt)
                                            },
                                            modifier = Modifier.animateItem()
                                        )
                                    }

                                    is ExploreContent.GuidePostContent -> {
                                        GuidePostCard(
                                            guidePost = content.guidePost,
                                            onClick = { onNavigateToGuidePostDetail(content.guidePost) },
                                            onFavoriteClick = { post ->
                                                viewModel.toggleGuidePostFavorite(post)
                                            },
                                            modifier = Modifier.animateItem()
                                        )
                                    }
                                }
                            }

                            // Pagination loading indicator
                            if (uiState.isLoading && uiState.content.isNotEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(32.dp),
                                            strokeWidth = 3.dp
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExploreTopBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onRefresh: () -> Unit
) {
    var isSearchExpanded by remember { mutableStateOf(false) }

    TopAppBar(
        modifier = Modifier.statusBarsPadding(), // ✅ Prevent overlap with status bar safely
        title = {
            AnimatedContent(
                targetState = isSearchExpanded,
                transitionSpec = {
                    slideInHorizontally() + fadeIn() togetherWith
                            slideOutHorizontally() + fadeOut()
                },
                label = "search_animation"
            ) { expanded ->
                if (expanded) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = { Text("Search content...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = {
                                isSearchExpanded = false
                                onSearchQueryChange("")
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Close search")
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        )
                    )
                } else {
                    Text(
                        "Explore",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        actions = {
            if (!isSearchExpanded) {
                IconButton(onClick = { isSearchExpanded = true }) {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}


@Composable
private fun FilterChipsSection(
    categories: List<String>,
    selectedCategory: String,
    selectedContentFilter: ContentFilter,
    selectedSortOption: SortOption,
    onCategorySelected: (String) -> Unit,
    onContentFilterSelected: (ContentFilter) -> Unit,
    onSortOptionSelected: (SortOption) -> Unit
) {
    // More compact filter section
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 4.dp) // Reduced vertical padding
    ) {
        // Combined Content Type and Categories in single row when possible
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 6.dp) // Reduced bottom padding
        ) {
            // Content Type Filters - more compact
            items(ContentFilter.entries) { filter ->
                FilterChip(
                    onClick = { onContentFilterSelected(filter) },
                    label = {
                        Text(
                            text = filter.displayName,
                            style = MaterialTheme.typography.labelSmall // Smaller text
                        )
                    },
                    selected = selectedContentFilter == filter,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.height(32.dp) // Smaller height
                )
            }

            // Add spacing between content types and categories
            item { Spacer(modifier = Modifier.width(8.dp)) }

            // Categories - only show a few most important ones
            items(categories.take(4)) { category -> // Limit to 4 categories
                FilterChip(
                    onClick = { onCategorySelected(category) },
                    label = {
                        Text(
                            text = category,
                            style = MaterialTheme.typography.labelSmall // Smaller text
                        )
                    },
                    selected = selectedCategory == category,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondary,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondary
                    ),
                    modifier = Modifier.height(32.dp) // Smaller height
                )
            }
        }

        // Sort Options - only show when needed, in a more compact way
        if (selectedContentFilter != ContentFilter.ALL || selectedCategory != "All") {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(SortOption.entries.take(3)) { option -> // Limit sort options
                    FilterChip(
                        onClick = { onSortOptionSelected(option) },
                        label = {
                            Text(
                                text = option.displayName,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        selected = selectedSortOption == option,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.tertiary,
                            selectedLabelColor = MaterialTheme.colorScheme.onTertiary
                        ),
                        modifier = Modifier.height(28.dp) // Even smaller for sort options
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingSection() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                strokeWidth = 4.dp
            )
            Text(
                text = "Loading content...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyStateSection(
    searchQuery: String,
    selectedCategory: String,
    onClearFilters: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            // Empty state icon
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                            )
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SearchOff,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                text = if (searchQuery.isNotEmpty()) "No results found" else "No content available",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            val message = when {
                searchQuery.isNotEmpty() -> "Try different search terms or clear filters"
                selectedCategory != "All" -> "No content found in this category"
                else -> "Check back later for new content"
            }

            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (searchQuery.isNotEmpty() || selectedCategory != "All") {
                Button(
                    onClick = onClearFilters,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clear Filters")
                }
            }
        }
    }
}