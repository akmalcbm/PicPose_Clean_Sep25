package com.picpose.bestphotographyapp.presentation.screens

import android.app.Activity
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
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
import com.picpose.bestphotographyapp.presentation.viewmodels.ContentFilter
import com.picpose.bestphotographyapp.presentation.viewmodels.ExploreContent
import com.picpose.bestphotographyapp.presentation.viewmodels.ExploreLoadState
import com.picpose.bestphotographyapp.presentation.viewmodels.ExploreUiState
import com.picpose.bestphotographyapp.presentation.viewmodels.ExploreViewModel
import com.picpose.bestphotographyapp.presentation.viewmodels.SortOption
import com.picpose.bestphotographyapp.utils.copyToClipboard
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
    val clipboard = LocalClipboard.current
    val snackbarHostState = remember { SnackbarHostState() }

    // 🔹 Track manual refresh status
    var manualRefreshInFlight by rememberSaveable { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Add with your other rememberSaveable flags:
    var hasAttemptedInitialLoad by rememberSaveable { mutableStateOf(false) }
    var sawLoadingOnce by rememberSaveable { mutableStateOf(false) }
    var minShimmerOver by rememberSaveable { mutableStateOf(false) }

    val activity = context as? Activity

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            activity?.window?.let { WindowCompat.setDecorFitsSystemWindows(it, false) }
    }


    // Keep your min shimmer delay (good choice)
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(450)
        minShimmerOver = true
    }

    // Update these effects:
    LaunchedEffect(uiState.isLoading) {
        if (uiState.isLoading) sawLoadingOnce = true
    }

    LaunchedEffect(uiState.isLoading, uiState.content.size, uiState.error) {
        // Mark "attempt done" only after we actually tried or got something
        if (!uiState.isLoading && (sawLoadingOnce || uiState.content.isNotEmpty() || uiState.error != null)) {
            hasAttemptedInitialLoad = true
        }
    }


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

    var showFilters by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        topBar = {
            ExploreTopBar(
                searchQuery = uiState.searchQuery,
                onSearchQueryChange = viewModel::updateSearchQuery,
                isManualRefreshLoading = manualRefreshInFlight,

                onRefresh = {
                    // Start manual refresh
                    manualRefreshInFlight = true
                    viewModel.updateSearchQuery("")
                    viewModel.updateCategory("All")
                    viewModel.updateContentFilter(ContentFilter.ALL)
                    viewModel.updateSortOption(SortOption.NEWEST)
                    viewModel.refresh()
                },

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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0)
    ) { innerPadding ->

        LaunchedEffect(uiState.isLoading, manualRefreshInFlight) {
            if (manualRefreshInFlight && !uiState.isLoading) {
                manualRefreshInFlight = false
                listState.animateScrollToItem(0)
                snackbarHostState.showSnackbar(
                    if (uiState.content.isNotEmpty()) "All filters and content refreshed"
                    else "No new content found"
                )
            }
        }

        val hasContent = uiState.content.isNotEmpty()

        // Show shimmer if:
        //  - content is empty AND (still loading OR haven't attempted initial load yet OR min shimmer time not over)
        val shouldShowInitialShimmer =
            uiState.content.isEmpty() && (!hasAttemptedInitialLoad || uiState.isLoading || !minShimmerOver)

        // Show empty only when:
        //  - not loading, attempted initial load, and still empty
        val shouldShowEmpty =
            !uiState.isLoading && hasAttemptedInitialLoad && uiState.content.isEmpty()


        val shouldShowErrorEmpty =
            uiState.error != null && uiState.content.isEmpty() && !uiState.isLoading


        when (uiState.loadState) {

            ExploreLoadState.INITIAL -> {
                ShimmerLoadingExploreScreen()
            }

            ExploreLoadState.LOADING -> {
                // Loading and nothing loaded yet
                LoadingSection()
            }

            ExploreLoadState.EMPTY -> {
                EmptyStateSection(
                    searchQuery = uiState.searchQuery,
                    selectedCategory = uiState.selectedCategory,
                    onClearFilters = {
                        viewModel.updateSearchQuery("")
                        viewModel.updateCategory("All")
                        viewModel.updateContentFilter(ContentFilter.ALL)
                        viewModel.refresh()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp, bottom = 60.dp)
                )
            }

            ExploreLoadState.ERROR -> {
                EmptyStateSection(
                    searchQuery = uiState.searchQuery,
                    selectedCategory = uiState.selectedCategory,
                    onClearFilters = { viewModel.refresh() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp, bottom = 60.dp)
                )
            }

            ExploreLoadState.SUCCESS -> {

                val sortedContent = remember(uiState.content) {
                    uiState.content.sortedBy {
                        when (it) {
                            is ExploreContent.AIPromptContent -> 0
                            is ExploreContent.GuidePostContent -> 1
                            else -> 2
                        }
                    }
                }

                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(
                        top = 8.dp,
                        start = 12.dp,
                        end = 12.dp,
                        bottom = WindowInsets.navigationBars
                            .asPaddingValues()
                            .calculateBottomPadding() + 24.dp
                    ),
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(innerPadding)
                        .consumeWindowInsets(innerPadding)
                ) {

                    if (uiState.content.isNotEmpty() && showFilters) {
                        stickyHeader {
                            FrostedFiltersStickyHeader(
                                uiState = uiState,
                                viewModel = viewModel
                            )
                        }
                    }

                    itemsIndexed(
                        items = sortedContent,
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
                                        val text = content.prompt.shortPrompt
                                            ?: content.prompt.fullPrompt
                                            ?: ""
                                        copyToClipboard(context, clipboard, text, coroutineScope)
                                    },
                                    onFavoriteClick = viewModel::togglePromptFavorite,
                                    modifier = Modifier.animateItem()
                                )
                            }

                            is ExploreContent.GuidePostContent -> {
                                GuidePostCard(
                                    guidePost = content.guidePost,
                                    onClick = { onNavigateToGuidePostDetail(content.guidePost) },
                                    onFavoriteClick = viewModel::toggleGuidePostFavorite,
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


@Composable
private fun ShimmerLoadingExploreScreen(innerPadding: PaddingValues? = null) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alphaAnim"
    )

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(
            top = 8.dp,
            start = 12.dp,
            end = 12.dp,
            bottom = 24.dp
        ),
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        items(4) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(12.dp)
            ) {
                // Top Row — image placeholder
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.LightGray.copy(alpha = alpha))
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Title Placeholder
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(18.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.LightGray.copy(alpha = alpha))
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Description line
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.LightGray.copy(alpha = alpha))
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Short description line 2
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.LightGray.copy(alpha = alpha))
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom action row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(3) {
                        Box(
                            modifier = Modifier
                                .size(width = 60.dp, height = 26.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.LightGray.copy(alpha = alpha))
                        )
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
    onSortOptionSelected: (SortOption) -> Unit,
    isManualRefreshLoading: Boolean
) {
    var isSearchExpanded by remember { mutableStateOf(false) }
    var isFilterExpanded by remember { mutableStateOf(false) }

    // 🎯 Controlled rotation state
    val rotation = remember { androidx.compose.animation.core.Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    // 💫 Animate rotation only while refresh is in flight
    LaunchedEffect(isManualRefreshLoading) {
        if (isManualRefreshLoading) {
            var completedOneCycle = false
            // keep rotating until at least one full spin done + loading completes
            while (true) {
                rotation.animateTo(
                    targetValue = rotation.value + 360f,
                    animationSpec = tween(durationMillis = 900, easing = LinearEasing)
                )
                completedOneCycle = true
                // stop only after one full spin AND loading has finished
                if (completedOneCycle && !isManualRefreshLoading) break
            }
        } else {
            // Smoothly reset back to 0 (optional, for clean stop)
            rotation.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 300, easing = LinearEasing)
            )
        }
    }

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

                // 🔁 Refresh Icon (controlled rotation)
                IconButton(
                    onClick = {
                        if (!isManualRefreshLoading) { // prevent spam clicks
                            coroutineScope.launch { onRefresh() }
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        modifier = Modifier.rotate(rotation.value)
                    )
                }

                // 🧩 Filter Button Animation
                val filterRotation by animateFloatAsState(
                    targetValue = if (isFilterExpanded) 180f else 0f,
                    animationSpec = spring(stiffness = 300f, dampingRatio = 0.6f),
                    label = "filter_rotation"
                )
                val scale by animateFloatAsState(
                    targetValue = if (isFilterExpanded) 1.1f else 1f,
                    animationSpec = spring(stiffness = 400f),
                    label = "filter_scale"
                )

                FilterButtonWithIndicator(
                    isFilterExpanded = isFilterExpanded,
                    rotation = filterRotation,
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
fun FrostedFiltersStickyHeader(
    uiState: ExploreUiState,
    viewModel: ExploreViewModel
) {
    val isDark = isSystemInDarkTheme()

    AnimatedVisibility(
        visible = true,
        enter = fadeIn() + scaleIn(initialScale = 0.98f),
        exit = fadeOut() + scaleOut(targetScale = 0.98f)
    ) {

        // Outer container matching list padding
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp)
        ) {

            // --- BACKGROUND Frosted Blur Layer ---
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(20.dp))
                    .blur(25.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.30f)
                            )
                        )
                    )
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                        RoundedCornerShape(20.dp)
                    )
                    .drawBehind {
                        drawRect(
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = if (isDark) 0.06f else 0.12f),
                                    Color.Transparent
                                ),
                                startY = 0f,
                                endY = size.height * 0.4f
                            )
                        )
                    }
            )

            // --- FOREGROUND Crisp Content ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (isDark) Color.Black.copy(alpha = 0.10f)
                        else Color.White.copy(alpha = 0.12f)
                    )
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(20.dp),
                        ambientColor = Color.Black.copy(alpha = 0.05f),
                        spotColor = Color.Black.copy(alpha = 0.08f)
                    )
            ) {
                FrostedStickyFilters(
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
    }
}


@Composable
private fun FrostedStickyFilters(
    categories: List<String>,
    selectedCategory: String,
    selectedContentFilter: ContentFilter,
    selectedSortOption: SortOption,
    onCategorySelected: (String) -> Unit,
    onContentFilterSelected: (ContentFilter) -> Unit,
    onSortOptionSelected: (SortOption) -> Unit
) {
    var showMore by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 🔹 Content & Sort Filters Row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(ContentFilter.entries) { filter ->
                FilterChip(
                    onClick = { onContentFilterSelected(filter) },
                    label = {
                        Text(
                            filter.displayName,
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    selected = selectedContentFilter == filter,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                        labelColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }

            item {
                Spacer(modifier = Modifier.width(4.dp))
            }

            items(SortOption.entries.take(3)) { option ->
                FilterChip(
                    onClick = { onSortOptionSelected(option) },
                    label = {
                        Text(
                            option.displayName,
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    selected = selectedSortOption == option,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondary,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondary,
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                        labelColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        }

        // 🔹 Category Filters Row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(categories.take(if (showMore) categories.size else 5)) { category ->
                FilterChip(
                    onClick = { onCategorySelected(category) },
                    label = { Text(category, style = MaterialTheme.typography.labelSmall) },
                    selected = selectedCategory == category,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.tertiary,
                        selectedLabelColor = MaterialTheme.colorScheme.onTertiary,
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                        labelColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }

            if (categories.size > 5) {
                item {
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
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier // ✅ Added this
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