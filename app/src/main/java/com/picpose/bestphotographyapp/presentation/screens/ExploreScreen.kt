package com.picpose.bestphotographyapp.presentation.screens

import android.app.Activity
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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

    // ✅ Edge-to-edge support
    val activity = context as? Activity
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            activity?.window?.let { WindowCompat.setDecorFitsSystemWindows(it, false) }
    }

    // ✅ Error handling
    LaunchedEffect(uiState.error) {
        uiState.error?.let { message ->
            coroutineScope.launch {
                snackbarHostState.showSnackbar(message)
                viewModel.clearError()
            }
        }
    }

    // ✅ Infinite scroll
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

    // 🔹 Controls filter visibility
    var showFilters by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        topBar = {
            ExploreTopBar(
                searchQuery = uiState.searchQuery,
                onSearchQueryChange = viewModel::updateSearchQuery,
                onRefresh = viewModel::refresh,
                onToggleFilters = { showFilters = !showFilters },
                categories = uiState.categories,
                selectedCategory = uiState.selectedCategory,
                selectedContentFilter = uiState.selectedContentFilter,
                selectedSortOption = uiState.selectedSortOption,
                onCategorySelected = viewModel::updateCategory,
                onContentFilterSelected = viewModel::updateContentFilter,
                onSortOptionSelected = viewModel::updateSortOption
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->

        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 100.dp), // ✅ space for nav bar
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

            // 🔹 Sticky header with animated filter visibility
            stickyHeader {
                AnimatedVisibility(
                    visible = showFilters,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { -80 }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { -80 })
                ) {
                    TranslucentStickyFilters(
                        categories = uiState.categories,
                        selectedCategory = uiState.selectedCategory,
                        selectedContentFilter = uiState.selectedContentFilter,
                        selectedSortOption = uiState.selectedSortOption,
                        onCategorySelected = viewModel::updateCategory,
                        onContentFilterSelected = viewModel::updateContentFilter,
                        onSortOptionSelected = viewModel::updateSortOption
                    )
                }
            }

            when {
                uiState.isLoading && uiState.content.isEmpty() -> {
                    item { LoadingSection() }
                }

                uiState.content.isEmpty() && !uiState.isLoading -> {
                    item {
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
                }

                else -> {
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
                                        val text =
                                            content.prompt.shortPrompt ?: content.prompt.fullPrompt ?: ""
                                        clipboardManager.setText(AnnotatedString(text))
                                        Toast.makeText(context, "Prompt copied!", Toast.LENGTH_SHORT).show()
                                    },
                                    onFavoriteClick = { prompt -> viewModel.togglePromptFavorite(prompt) },
                                    modifier = Modifier.animateItem()
                                )
                            }

                            is ExploreContent.GuidePostContent -> {
                                GuidePostCard(
                                    guidePost = content.guidePost,
                                    onClick = { onNavigateToGuidePostDetail(content.guidePost) },
                                    onFavoriteClick = { post -> viewModel.toggleGuidePostFavorite(post) },
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }
                    }

                    if (uiState.isLoading && uiState.content.isNotEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(32.dp))
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
    onRefresh: () -> Unit,
    onToggleFilters: () -> Unit,
    categories: List<String>,
    selectedCategory: String,
    selectedContentFilter: ContentFilter,
    selectedSortOption: SortOption,
    onCategorySelected: (String) -> Unit,
    onContentFilterSelected: (ContentFilter) -> Unit,
    onSortOptionSelected: (SortOption) -> Unit
) {
    var isSearchExpanded by remember { mutableStateOf(false) }
    var isFilterExpanded by remember { mutableStateOf(false) }

    TopAppBar(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)),
        title = {
            AnimatedContent(
                targetState = isSearchExpanded,
                transitionSpec = {
                    slideInHorizontally() + fadeIn() togetherWith slideOutHorizontally() + fadeOut()
                },
                label = "search_anim"
            ) { expanded ->
                if (expanded) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = { Text("Search content...") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = {
                            IconButton(onClick = {
                                isSearchExpanded = false
                                onSearchQueryChange("")
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Close search")
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        "Explore",
                        style = MaterialTheme.typography.headlineSmall,
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

                // 🧩 Animated Filter Button
                val rotation by animateFloatAsState(
                    targetValue = if (isFilterExpanded) 180f else 0f,
                    animationSpec = spring(stiffness = 300f, dampingRatio = 0.6f),
                    label = "filter_rotation"
                )

                val scale by animateFloatAsState(
                    targetValue = if (isFilterExpanded) 1.1f else 1f,
                    animationSpec = spring(stiffness = 400f),
                    label = "filter_scale"
                )

                // ✅ FIX: Move AnimatedVisibility to standalone composable
                FilterButtonWithIndicator(
                    isFilterExpanded = isFilterExpanded,
                    rotation = rotation,
                    scale = scale,
                    onClick = {
                        isFilterExpanded = !isFilterExpanded
                        onToggleFilters()
                    }
                )
            }

        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            titleContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
    //
}

@Composable
private fun FilterButtonWithIndicator(
    isFilterExpanded: Boolean,
    rotation: Float,
    scale: Float,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier.wrapContentSize(),
        contentAlignment = Alignment.Center
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.graphicsLayer {
                rotationZ = rotation
                scaleX = scale
                scaleY = scale
            }
        ) {
            Icon(
                imageVector = Icons.Default.FilterList,
                contentDescription = if (isFilterExpanded) "Hide filters" else "Show filters"
            )
        }

        // ✅ Now AnimatedVisibility has its own neutral scope
        Box(contentAlignment = Alignment.Center) {
            AnimatedVisibility(
                visible = isFilterExpanded,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronLeft,
                    contentDescription = null,
                    modifier = Modifier
                        .size(12.dp)
                        .offset(x = 16.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun TranslucentStickyFilters(
    categories: List<String>,
    selectedCategory: String,
    selectedContentFilter: ContentFilter,
    selectedSortOption: SortOption,
    onCategorySelected: (String) -> Unit,
    onContentFilterSelected: (ContentFilter) -> Unit,
    onSortOptionSelected: (SortOption) -> Unit
) {
    var showMore by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                    )
                )
            )
            .shadow(4.dp),
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // 🔹 Content Filters
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ContentFilter.entries) { filter ->
                    FilterChip(
                        onClick = { onContentFilterSelected(filter) },
                        label = { Text(filter.displayName, style = MaterialTheme.typography.labelSmall) },
                        selected = selectedContentFilter == filter,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }

                // 🔹 Sort Options
                items(SortOption.entries.take(3)) { option ->
                    FilterChip(
                        onClick = { onSortOptionSelected(option) },
                        label = { Text(option.displayName, style = MaterialTheme.typography.labelSmall) },
                        selected = selectedSortOption == option,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondary,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 🔹 Categories
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories.take(if (showMore) categories.size else 5)) { category ->
                    FilterChip(
                        onClick = { onCategorySelected(category) },
                        label = { Text(category, style = MaterialTheme.typography.labelSmall) },
                        selected = selectedCategory == category,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.tertiary,
                            selectedLabelColor = MaterialTheme.colorScheme.onTertiary
                        )
                    )
                }

                if (categories.size > 5) {
                    item {
                        // 🔹 Replace vertical arrows with horizontal icons ▶️◀️
                        AssistChip(
                            onClick = { showMore = !showMore },
                            label = { Text(if (showMore) "Less" else "More") },
                            leadingIcon = {
                                Icon(
                                    if (showMore) Icons.Default.ChevronLeft else Icons.Default.ChevronRight,
                                    contentDescription = null
                                )
                            }
                        )
                    }
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