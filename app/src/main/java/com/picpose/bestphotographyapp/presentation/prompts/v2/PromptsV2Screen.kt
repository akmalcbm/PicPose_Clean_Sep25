/**
 * ---
 * File: PromptsV2Screen.kt
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

package com.picpose.bestphotographyapp.presentation.prompts.v2

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.picpose.bestphotographyapp.R
import com.picpose.bestphotographyapp.components.ads.AdsConfigState
import com.picpose.bestphotographyapp.components.ads.AdsManager
import com.picpose.bestphotographyapp.components.ads.InlineNativeAdCard
import com.picpose.bestphotographyapp.components.ads.LargeNativeAdCard
import com.picpose.bestphotographyapp.components.ads.LargeNativeAdCardForGrid
import com.picpose.bestphotographyapp.components.common.PicPoseTopAppBar
import com.picpose.bestphotographyapp.components.common.PicPoseTopBarActionButton
import com.picpose.bestphotographyapp.components.common.ScrollAwareTopControls
import com.picpose.bestphotographyapp.components.common.rememberScrollAwareTopControlsVisibility
import com.picpose.bestphotographyapp.data.remote.dto.v2.V2PromptDto
import com.picpose.bestphotographyapp.data.repository.EngagementRepository
import com.picpose.bestphotographyapp.domain.model.isPackOnlyPrompt
import com.picpose.bestphotographyapp.domain.model.supportsCreditsUnlock
import com.picpose.bestphotographyapp.domain.model.supportsRewardedUnlock
import com.picpose.bestphotographyapp.presentation.search.SearchMatchers
import com.picpose.bestphotographyapp.utils.setText
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private const val CATEGORY_ALL = "All"

private enum class V2ViewMode { GRID, LIST }

private enum class PromptNativeAdStyle {
    LargeMedia,
    Compact,
}

private sealed interface PromptGridFeedItem {
    data class PromptItem(val prompt: V2PromptDto) : PromptGridFeedItem
    data class AdItem(val slotKey: String, val adIndex: Int, val anchorPromptId: String) : PromptGridFeedItem
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptsV2Screen(
    onBack: () -> Unit,
    onPromptClick: (String) -> Unit,
    initialCategory: String? = null,
    viewModel: PromptsV2ViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val adsConfigState by AdsManager.configState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()

    var showSearch by rememberSaveable { mutableStateOf(false) }
    var viewMode by rememberSaveable { mutableStateOf(V2ViewMode.GRID) }

    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val categoryListState = rememberLazyListState()

    val normalizedQuery = remember(uiState.query) { SearchMatchers.normalizeQuery(uiState.query) }

    val displayPrompts = remember(
        uiState.prompts,
        normalizedQuery,
        uiState.selectedCategory,
        uiState.selectedFilter,
    ) {
        uiState.prompts.filter { prompt ->
            matchesSelectedCategory(prompt, uiState.selectedCategory) &&
                matchesSelectedFilter(prompt, uiState.selectedFilter) &&
            SearchMatchers.matchesV2Prompt(prompt, normalizedQuery)
        }
    }

    val showFilterControls = rememberScrollAwareTopControlsVisibility(
        enabled = displayPrompts.isNotEmpty(),
        scrollPositionProvider = {
            if (viewMode == V2ViewMode.LIST) {
                listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
            } else {
                gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset
            }
        },
        resetKey = viewMode,
    )

    val canShowNativeAds = remember(adsConfigState) {
        adsConfigState is AdsConfigState.Ready && AdsManager.canShowAds()
    }

    var nativeAds by remember { mutableStateOf<List<NativeAd>>(emptyList()) }
    val adContext = LocalContext.current
    val adUnitId = remember(canShowNativeAds, adsConfigState) {
        if (canShowNativeAds) AdsManager.getAdUnitId(AdsManager.KEY_NATIVE_AD) else null
    }

    DisposableEffect(adContext, adUnitId) {
        var disposed = false
        val loadedAds = mutableListOf<NativeAd>()

        if (!adUnitId.isNullOrBlank()) {
            val adLoader = AdLoader.Builder(adContext, adUnitId)
                .forNativeAd { ad ->
                    if (disposed) {
                        ad.destroy()
                    } else {
                        loadedAds.add(ad)
                        nativeAds = loadedAds.toList()
                    }
                }
                .withNativeAdOptions(NativeAdOptions.Builder().build())
                .build()

            repeat(3) {
                adLoader.loadAd(AdRequest.Builder().build())
            }
        }

        onDispose {
            disposed = true
            loadedAds.forEach { it.destroy() }
            nativeAds = emptyList()
        }
    }

    val gridFeed = remember(displayPrompts, nativeAds) {
        buildGridFeed(displayPrompts, includeAds = nativeAds.isNotEmpty())
    }

    LaunchedEffect(initialCategory) {
        viewModel.initialize(initialCategory)
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    LaunchedEffect(uiState.query) {
        if (uiState.query.isNotBlank()) {
            showSearch = true
        }
    }

    LaunchedEffect(uiState.selectedCategory, uiState.selectedFilter, viewMode) {
        if (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0) {
            listState.scrollToItem(0)
        }
        if (gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0) {
            gridState.scrollToItem(0)
        }
    }

    LaunchedEffect(viewMode, listState, normalizedQuery, uiState.hasMore, uiState.isLoadingMore, displayPrompts.size) {
        if (viewMode != V2ViewMode.LIST) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { lastVisible ->
                if (
                    normalizedQuery.isBlank() &&
                    uiState.hasMore &&
                    !uiState.isLoadingMore &&
                    lastVisible >= (displayPrompts.size - 5).coerceAtLeast(0)
                ) {
                    viewModel.loadNextPage()
                }
            }
    }

    LaunchedEffect(viewMode, gridState, normalizedQuery, uiState.hasMore, uiState.isLoadingMore, gridFeed.size) {
        if (viewMode != V2ViewMode.GRID) return@LaunchedEffect
        observeGridEndReached(
            gridState = gridState,
            itemCount = gridFeed.size,
            canLoadMore = normalizedQuery.isBlank() && uiState.hasMore && !uiState.isLoadingMore,
            onEndReached = { viewModel.loadNextPage() },
        )
    }

    val totalForTitle = if (uiState.totalPrompts > 0) uiState.totalPrompts else displayPrompts.size
    val titleText = if (!uiState.selectedCategory.equals(CATEGORY_ALL, ignoreCase = true)) {
        stringResource(
            R.string.prompts_for_category_count,
            uiState.selectedCategory,
            displayPrompts.size,
        )
    } else {
        stringResource(R.string.all_prompts_count, totalForTitle)
    }

    Scaffold(
        topBar = {
            PromptsV2TopBar(
                title = titleText,
                viewMode = viewMode,
                showSearch = showSearch,
                onBack = onBack,
                onRefresh = { viewModel.refresh() },
                onSearchToggle = {
                    if (showSearch) {
                        showSearch = false
                        if (uiState.query.isNotBlank()) {
                            viewModel.onQueryChanged("")
                        }
                    } else {
                        showSearch = true
                    }
                },
                onViewToggle = {
                    viewMode = if (viewMode == V2ViewMode.GRID) V2ViewMode.LIST else V2ViewMode.GRID
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(
                    WindowInsets.safeDrawing
                        .only(WindowInsetsSides.Horizontal)
                        .asPaddingValues()
                ),
        ) {
            if (showSearch) {
                PromptsV2SearchBar(
                    query = uiState.query,
                    onQueryChange = viewModel::onQueryChanged,
                    onClear = { viewModel.onQueryChanged("") },
                )
            }

            ScrollAwareTopControls(visible = showFilterControls) {
                Column {
                    PromptsV2CategoryRow(
                        categories = uiState.categories,
                        selectedCategory = uiState.selectedCategory,
                        listState = categoryListState,
                        onCategorySelected = { index, category ->
                            coroutineScope.launch {
                                categoryListState.animateScrollToItem(index = index, scrollOffset = -200)
                            }
                            coroutineScope.launch {
                                listState.scrollToItem(0)
                                gridState.scrollToItem(0)
                            }
                            viewModel.onCategorySelected(category)
                        },
                    )

                    PromptsV2FilterRow(
                        selectedFilter = uiState.selectedFilter,
                        onFilterSelected = viewModel::onFilterSelected,
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                }
            }

            when {
                uiState.isLoading && uiState.prompts.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                displayPrompts.isEmpty() -> {
                    PromptsV2EmptyState(
                        query = normalizedQuery,
                        selectedCategory = uiState.selectedCategory,
                        selectedFilter = uiState.selectedFilter,
                        onClearFilters = {
                            showSearch = false
                            viewModel.clearFiltersAndReload()
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 64.dp),
                    )
                }

                viewMode == V2ViewMode.LIST -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        itemsIndexed(
                            items = displayPrompts,
                            key = { _, item -> item.id },
                        ) { index, prompt ->
                            val engagement = uiState.engagementByPromptId[prompt.id]

                            PromptV2ListCard(
                                prompt = prompt,
                                engagement = engagement,
                                pointsBalance = uiState.pointsBalance,
                                isLoggedIn = isLoggedIn,
                                isUnlocking = prompt.id in uiState.unlockingPromptIds,
                                onOpen = { onPromptClick(prompt.id) },
                                onUnlockWithPoints = { viewModel.unlockPromptWithPoints(prompt.id) },
                                onWatchAd = { onPromptClick(prompt.id) },
                                onCopy = {
                                    val copyText = prompt.copyTextForList()
                                    if (copyText.isNotBlank()) {
                                        coroutineScope.launch {
                                            clipboard.setText(copyText, label = "prompt")
                                            snackbarHostState.showSnackbar(context.getString(R.string.prompt_copied_toast))
                                        }
                                        viewModel.trackPromptUsage(
                                            promptId = prompt.id,
                                            action = EngagementRepository.PromptUsageAction.COPY
                                        )
                                    }
                                },
                                onLike = { viewModel.onLikeClicked(prompt.id) },
                                onFavorite = { viewModel.onFavoriteClicked(prompt.id) },
                            )

                            val ad = nativeAdForPosition(
                                index = index,
                                totalItems = displayPrompts.size,
                                nativeAds = nativeAds,
                            )

                            if (ad != null) {
                                Spacer(modifier = Modifier.height(2.dp))
                                when (chooseMixedAdStyle(prompt.id, V2ViewMode.LIST)) {
                                    PromptNativeAdStyle.Compact -> {
                                        InlineNativeAdCard(nativeAd = ad, modifier = Modifier.fillMaxWidth())
                                    }

                                    PromptNativeAdStyle.LargeMedia -> {
                                        LargeNativeAdCard(nativeAd = ad, modifier = Modifier.fillMaxWidth())
                                    }
                                }
                            }
                        }

                        if (uiState.isLoadingMore) {
                            item(key = "loading_more_list") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp),
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 2.dp)
                                }
                            }
                        }
                    }
                }

                else -> {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Adaptive(minSize = 160.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(
                            items = gridFeed,
                            key = { item ->
                                when (item) {
                                    is PromptGridFeedItem.PromptItem -> item.prompt.id
                                    is PromptGridFeedItem.AdItem -> item.slotKey
                                }
                            },
                            span = { item ->
                                when (item) {
                                    is PromptGridFeedItem.AdItem -> GridItemSpan(maxLineSpan)
                                    is PromptGridFeedItem.PromptItem -> GridItemSpan(1)
                                }
                            },
                        ) { item ->
                            when (item) {
                                is PromptGridFeedItem.PromptItem -> {
                                    val prompt = item.prompt
                                    val engagement = uiState.engagementByPromptId[prompt.id]
                                    PromptV2GridCard(
                                        prompt = prompt,
                                        engagement = engagement,
                                        pointsBalance = uiState.pointsBalance,
                                        isLoggedIn = isLoggedIn,
                                        isUnlocking = prompt.id in uiState.unlockingPromptIds,
                                        onOpen = { onPromptClick(prompt.id) },
                                        onUnlockWithPoints = { viewModel.unlockPromptWithPoints(prompt.id) },
                                        onWatchAd = { onPromptClick(prompt.id) },
                                        onCopy = {
                                            val copyText = prompt.copyTextForList()
                                            if (copyText.isNotBlank()) {
                                                coroutineScope.launch {
                                                    clipboard.setText(copyText, label = "prompt")
                                                    snackbarHostState.showSnackbar(context.getString(R.string.prompt_copied_toast))
                                                }
                                                viewModel.trackPromptUsage(
                                                    promptId = prompt.id,
                                                    action = EngagementRepository.PromptUsageAction.COPY
                                                )
                                            }
                                        },
                                        onLike = { viewModel.onLikeClicked(prompt.id) },
                                        onFavorite = { viewModel.onFavoriteClicked(prompt.id) },
                                    )
                                }

                                is PromptGridFeedItem.AdItem -> {
                                    val ad = nativeAds.getOrNull(item.adIndex % nativeAds.size)
                                    when (chooseMixedAdStyle(item.anchorPromptId, V2ViewMode.GRID)) {
                                        PromptNativeAdStyle.Compact -> {
                                            InlineNativeAdCard(nativeAd = ad, modifier = Modifier.fillMaxWidth())
                                        }

                                        PromptNativeAdStyle.LargeMedia -> {
                                            LargeNativeAdCardForGrid(nativeAd = ad, modifier = Modifier.fillMaxWidth())
                                        }
                                    }
                                }
                            }
                        }

                        if (uiState.isLoadingMore) {
                            item(span = { GridItemSpan(maxLineSpan) }, key = "loading_more_grid") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp),
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 2.dp)
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
private fun PromptsV2TopBar(
    title: String,
    viewMode: V2ViewMode,
    showSearch: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSearchToggle: () -> Unit,
    onViewToggle: () -> Unit,
) {
    PicPoseTopAppBar(
        title = title,
        eyebrow = stringResource(R.string.prompts),
        onBack = onBack,
        actions = {
            PicPoseTopBarActionButton(
                icon = Icons.Default.Refresh,
                contentDescription = stringResource(R.string.refresh),
                onClick = onRefresh,
            )
            PicPoseTopBarActionButton(
                icon = if (showSearch) Icons.Default.SearchOff else Icons.Default.Search,
                contentDescription = stringResource(R.string.search),
                onClick = onSearchToggle,
                active = showSearch,
            )
            PicPoseTopBarActionButton(
                icon = if (viewMode == V2ViewMode.GRID) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                contentDescription = stringResource(R.string.change_view),
                onClick = onViewToggle,
                active = viewMode == V2ViewMode.LIST,
            )
        },
    )
}

@Composable
private fun PromptsV2SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f)),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = {
                Text(
                    text = stringResource(R.string.search_prompts_placeholder),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge,
            leadingIcon = {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(6.dp)
                            .size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            trailingIcon = {
                if (query.isNotBlank()) {
                    PromptActionIconButton(
                        icon = Icons.Default.Clear,
                        contentDescription = stringResource(R.string.clear),
                        onClick = onClear,
                        compact = true,
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PromptsV2CategoryRow(
    categories: List<String>,
    selectedCategory: String,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onCategorySelected: (index: Int, category: String) -> Unit,
) {
    if (categories.isEmpty()) return

    LazyRow(
        state = listState,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
    ) {
        itemsIndexed(items = categories, key = { _, item -> item }) { index, category ->
            val isSelected = selectedCategory.equals(category, ignoreCase = true)
            PromptSelectableChip(
                text = category,
                selected = isSelected,
                onClick = { onCategorySelected(index, category) },
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun PromptsV2FilterRow(
    selectedFilter: PromptChipFilter,
    onFilterSelected: (PromptChipFilter) -> Unit,
) {
    LazyRow(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
    ) {
        itemsIndexed(items = PromptChipFilter.entries.toList(), key = { _, item -> item.name }) { _, filter ->
            val isSelected = selectedFilter == filter
            PromptSelectableChip(
                text = when (filter) {
                    PromptChipFilter.All -> stringResource(R.string.all)
                    PromptChipFilter.Free -> stringResource(R.string.free)
                    PromptChipFilter.Premium -> stringResource(R.string.premium)
                    PromptChipFilter.Featured -> stringResource(R.string.featured)
                },
                selected = isSelected,
                onClick = { onFilterSelected(filter) },
                selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                selectedContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}

@Composable
private fun PromptSelectableChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    selectedContainerColor: Color,
    selectedContentColor: Color,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingIcon = {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
            }
        },
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = if (selected) 0.dp else 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f),
        ),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = selectedContainerColor,
            selectedLabelColor = selectedContentColor,
            selectedLeadingIconColor = selectedContentColor,
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}

@Composable
private fun PromptV2ListCard(
    prompt: V2PromptDto,
    engagement: PromptEngagementUi?,
    pointsBalance: Int?,
    isLoggedIn: Boolean,
    isUnlocking: Boolean,
    onOpen: () -> Unit,
    onUnlockWithPoints: () -> Unit,
    onWatchAd: () -> Unit,
    onCopy: () -> Unit,
    onLike: () -> Unit,
    onFavorite: () -> Unit,
) {
    val likes = engagement?.likesCount ?: prompt.likes.coerceAtLeast(0)
    val favorites = engagement?.favoritesCount ?: prompt.favorites.coerceAtLeast(0)
    val views = engagement?.viewsCount ?: prompt.views.coerceAtLeast(0)
    val liked = engagement?.isLiked == true
    val favorited = engagement?.isFavorited == true

    PromptCardSurface(
        onClick = onOpen,
        featured = prompt.isFeatured,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            PromptHeroImage(
                imageUrl = prompt.imageUrl ?: prompt.imageUrl2,
                title = prompt.title,
                height = 228.dp,
                isLocked = prompt.isLocked,
                isFeatured = prompt.isFeatured,
                unlockCostPoints = prompt.premiumUnlockCostPoints,
            )

            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp)) {
                Text(
                    text = prompt.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(9.dp))

                Text(
                    text = prompt.previewText(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(12.dp))

                PromptMetaChipsRow(prompt = prompt)

                Spacer(modifier = Modifier.height(12.dp))

                PromptStatsActionSection(
                    views = views,
                    likes = likes,
                    favorites = favorites,
                    liked = liked,
                    favorited = favorited,
                    onCopy = onCopy,
                    onLike = onLike,
                    onFavorite = onFavorite,
                )

                Spacer(modifier = Modifier.height(12.dp))

                PromptCardCtaSection(
                    isLocked = prompt.isLocked,
                    supportsCreditsUnlock = prompt.supportsCreditsUnlock(),
                    supportsRewardedUnlock = prompt.supportsRewardedUnlock(),
                    isPackOnly = prompt.isPackOnlyPrompt(),
                    unlockCostPoints = prompt.premiumUnlockCostPoints,
                    pointsBalance = pointsBalance,
                    isLoggedIn = isLoggedIn,
                    isUnlocking = isUnlocking,
                    onOpen = onOpen,
                    onUnlockWithPoints = onUnlockWithPoints,
                    onWatchAd = onWatchAd,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun PromptV2GridCard(
    prompt: V2PromptDto,
    engagement: PromptEngagementUi?,
    pointsBalance: Int?,
    isLoggedIn: Boolean,
    isUnlocking: Boolean,
    onOpen: () -> Unit,
    onUnlockWithPoints: () -> Unit,
    onWatchAd: () -> Unit,
    onCopy: () -> Unit,
    onLike: () -> Unit,
    onFavorite: () -> Unit,
) {
    val likes = engagement?.likesCount ?: prompt.likes.coerceAtLeast(0)
    val favorites = engagement?.favoritesCount ?: prompt.favorites.coerceAtLeast(0)
    val views = engagement?.viewsCount ?: prompt.views.coerceAtLeast(0)
    val liked = engagement?.isLiked == true
    val favorited = engagement?.isFavorited == true

    PromptCardSurface(
        onClick = onOpen,
        featured = prompt.isFeatured,
        modifier = Modifier.fillMaxWidth(),
        compact = true,
    ) {
        Column {
            PromptHeroImage(
                imageUrl = prompt.imageUrl ?: prompt.imageUrl2,
                title = prompt.title,
                height = 164.dp,
                isLocked = prompt.isLocked,
                isFeatured = prompt.isFeatured,
                unlockCostPoints = prompt.premiumUnlockCostPoints,
                compact = true,
            )

            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp)) {
                Text(
                    text = prompt.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    minLines = 2,
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = prompt.previewText(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    minLines = 2,
                )

                Spacer(modifier = Modifier.height(8.dp))

                PromptMetaChipsRow(
                    prompt = prompt,
                    compact = true,
                )

                Spacer(modifier = Modifier.height(8.dp))

                PromptStatsActionSection(
                    views = views,
                    likes = likes,
                    favorites = favorites,
                    liked = liked,
                    favorited = favorited,
                    onCopy = onCopy,
                    onLike = onLike,
                    onFavorite = onFavorite,
                    compact = true,
                )

                Spacer(modifier = Modifier.height(8.dp))

                PromptCardCtaSection(
                    isLocked = prompt.isLocked,
                    supportsCreditsUnlock = prompt.supportsCreditsUnlock(),
                    supportsRewardedUnlock = prompt.supportsRewardedUnlock(),
                    isPackOnly = prompt.isPackOnlyPrompt(),
                    unlockCostPoints = prompt.premiumUnlockCostPoints,
                    pointsBalance = pointsBalance,
                    isLoggedIn = isLoggedIn,
                    isUnlocking = isUnlocking,
                    onOpen = onOpen,
                    onUnlockWithPoints = onUnlockWithPoints,
                    onWatchAd = onWatchAd,
                    compact = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun PromptStatsActionSection(
    views: Int,
    likes: Int,
    favorites: Int,
    liked: Boolean,
    favorited: Boolean,
    onCopy: () -> Unit,
    onLike: () -> Unit,
    onFavorite: () -> Unit,
    compact: Boolean = false,
) {
    Surface(
        shape = RoundedCornerShape(if (compact) 12.dp else 14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (compact) 0.28f else 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (compact) 8.dp else 10.dp, vertical = if (compact) 6.dp else 8.dp),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PromptStatsRow(
                views = views,
                likes = likes,
                favorites = favorites,
                compact = compact,
                modifier = Modifier.weight(1f),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)) {
                PromptActionIconButton(
                    icon = Icons.Default.ContentCopy,
                    contentDescription = stringResource(R.string.ai_prompt_action_copy_prompt),
                    onClick = onCopy,
                    compact = compact,
                )
                PromptActionIconButton(
                    icon = if (liked) Icons.Default.ThumbUp else Icons.Outlined.ThumbUp,
                    contentDescription = stringResource(R.string.like),
                    onClick = onLike,
                    active = liked,
                    compact = compact,
                )
                PromptActionIconButton(
                    icon = if (favorited) Icons.Default.BookmarkAdded else Icons.Default.BookmarkBorder,
                    contentDescription = stringResource(R.string.favorite),
                    onClick = onFavorite,
                    active = favorited,
                    compact = compact,
                )
            }
        }
    }
}

@Composable
private fun PromptCardCtaSection(
    isLocked: Boolean,
    supportsCreditsUnlock: Boolean,
    supportsRewardedUnlock: Boolean,
    isPackOnly: Boolean,
    unlockCostPoints: Int,
    pointsBalance: Int?,
    isLoggedIn: Boolean,
    isUnlocking: Boolean,
    onOpen: () -> Unit,
    onUnlockWithPoints: () -> Unit,
    onWatchAd: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    if (isLocked) {
        PremiumUnlockPanel(
            supportsCreditsUnlock = supportsCreditsUnlock,
            supportsRewardedUnlock = supportsRewardedUnlock,
            isPackOnly = isPackOnly,
            unlockCostPoints = unlockCostPoints,
            pointsBalance = pointsBalance,
            isLoggedIn = isLoggedIn,
            isUnlocking = isUnlocking,
            onOpen = onOpen,
            onUnlockWithPoints = onUnlockWithPoints,
            onWatchAd = onWatchAd,
            modifier = modifier,
            compact = compact,
        )
    } else {
        PromptPrimaryButton(
            text = stringResource(R.string.prompt_open),
            onClick = onOpen,
            modifier = modifier,
            compact = compact,
        )
    }
}

@Composable
private fun PremiumUnlockPanel(
    supportsCreditsUnlock: Boolean,
    supportsRewardedUnlock: Boolean,
    isPackOnly: Boolean,
    unlockCostPoints: Int,
    pointsBalance: Int?,
    isLoggedIn: Boolean,
    isUnlocking: Boolean,
    onOpen: () -> Unit,
    onUnlockWithPoints: () -> Unit,
    onWatchAd: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val unlockCost = unlockCostPoints.coerceAtLeast(0)
    val hasEnoughCredits = pointsBalance?.let { it >= unlockCost } ?: true
    val canUnlockWithCredits = supportsCreditsUnlock && isLoggedIn && hasEnoughCredits && !isUnlocking
    val hasAnyDirectUnlock = supportsCreditsUnlock || supportsRewardedUnlock

    val primaryButtonText = when {
        isPackOnly && !hasAnyDirectUnlock -> stringResource(R.string.pack_open_pack)
        isUnlocking -> stringResource(R.string.pack_unlocking)
        !isLoggedIn -> stringResource(R.string.prompt_unlock_login_required)
        supportsCreditsUnlock && unlockCost > 0 -> stringResource(R.string.pack_unlock_for_credits, unlockCost)
        else -> stringResource(R.string.prompt_unlock_now)
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(if (compact) 14.dp else 18.dp),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(if (compact) 4.dp else 5.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.28f)),
        tonalElevation = if (compact) 3.dp else 4.dp,
        shadowElevation = if (compact) 2.dp else 3.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.24f),
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        ),
                    ),
                )
                .padding(horizontal = if (compact) 10.dp else 12.dp, vertical = if (compact) 10.dp else 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PromptPremiumBadge(compact = compact)
                pointsBalance?.let { balance ->
                    PromptMetaChip(
                        text = stringResource(R.string.prompt_balance_credits, balance),
                        compact = true,
                    )
                }
            }

            Spacer(modifier = Modifier.height(if (compact) 8.dp else 10.dp))

            Text(
                text = stringResource(R.string.prompt_unlock_premium_title),
                style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = when {
                    isPackOnly && !hasAnyDirectUnlock -> stringResource(R.string.pack_locked_prompt_title)
                    !isLoggedIn -> stringResource(R.string.prompt_unlock_login_hint)
                    supportsCreditsUnlock && pointsBalance != null && !hasEnoughCredits -> stringResource(
                        R.string.prompt_unlock_not_enough_hint,
                        unlockCost,
                    )
                    else -> stringResource(R.string.prompt_unlock_instant_hint)
                },
                style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(if (compact) 8.dp else 10.dp))

            PromptPrimaryButton(
                text = primaryButtonText,
                onClick = {
                    when {
                        isPackOnly && !hasAnyDirectUnlock -> onOpen()
                        !isLoggedIn -> onOpen()
                        supportsRewardedUnlock && !supportsCreditsUnlock -> onWatchAd()
                        canUnlockWithCredits -> onUnlockWithPoints()
                        else -> onOpen()
                    }
                },
                enabled = !isUnlocking && (isPackOnly || !isLoggedIn || !supportsCreditsUnlock || hasEnoughCredits),
                modifier = Modifier.fillMaxWidth(),
                compact = compact,
                leadingIcon = if (isPackOnly && !hasAnyDirectUnlock) Icons.Default.Lock else Icons.Default.MonetizationOn,
                showLoading = isUnlocking,
            )

            if (supportsRewardedUnlock || !isPackOnly) {
                Spacer(modifier = Modifier.height(if (compact) 6.dp else 8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp),
                ) {
                    PromptSecondaryButton(
                        text = stringResource(R.string.prompt_open),
                        icon = Icons.Default.Lock,
                        onClick = onOpen,
                        modifier = Modifier.weight(1f),
                        compact = compact,
                    )
                    if (supportsRewardedUnlock) {
                        PromptSecondaryButton(
                            text = stringResource(R.string.rewards_watch_ad_short),
                            icon = Icons.Default.VideoLibrary,
                            onClick = onWatchAd,
                            modifier = Modifier.weight(1f),
                            compact = compact,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PromptCardSurface(
    onClick: () -> Unit,
    featured: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(if (compact) 20.dp else 24.dp)

    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(if (featured) 5.dp else 2.dp),
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (featured) {
                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.42f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (featured) 7.dp else 4.dp),
    ) {
        Box {
            content()
            if (featured) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(if (compact) 2.dp else 3.dp)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.88f),
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.66f),
                                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.88f),
                                ),
                            ),
                        ),
                )
            }
        }
    }
}

@Composable
private fun PromptHeroImage(
    imageUrl: String?,
    title: String,
    height: androidx.compose.ui.unit.Dp,
    isLocked: Boolean,
    isFeatured: Boolean,
    unlockCostPoints: Int,
    compact: Boolean = false,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f),
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.32f),
                        ),
                    ),
                ),
        )

        AsyncImage(
            model = imageUrl,
            contentDescription = title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )

        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.06f),
                            Color.Transparent,
                            Color.Black.copy(alpha = if (compact) 0.34f else 0.40f),
                        ),
                    ),
                ),
        )

        if (isLocked) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = if (compact) 0.05f else 0.07f)),
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (isLocked) {
                PromptPremiumBadge(compact = compact)
            }
            if (isFeatured) {
                PromptFeaturedBadge(compact = compact)
            }
        }

        if (unlockCostPoints > 0) {
            PromptMetaChip(
                text = "$unlockCostPoints ${stringResource(R.string.rewards_credits)}",
                compact = true,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp),
            )
        }
    }
}

@Composable
private fun PromptPremiumBadge(compact: Boolean = false) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        shape = RoundedCornerShape(if (compact) 10.dp else 12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (compact) 8.dp else 10.dp, vertical = if (compact) 4.dp else 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(if (compact) 12.dp else 14.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.premium),
                style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun PromptFeaturedBadge(compact: Boolean = false) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.92f),
        shape = RoundedCornerShape(if (compact) 10.dp else 12.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (compact) 8.dp else 10.dp, vertical = if (compact) 4.dp else 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Star,
                contentDescription = null,
                modifier = Modifier.size(if (compact) 12.dp else 14.dp),
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            if (!compact) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.featured),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun PromptMetaChipsRow(
    prompt: V2PromptDto,
    compact: Boolean = false,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        prompt.category?.takeIf { it.isNotBlank() }?.let { category ->
            PromptMetaChip(
                text = category,
                compact = compact,
                maxLines = 1,
            )
        }
        if (prompt.premiumUnlockCostPoints > 0) {
            PromptMetaChip(
                text = "${prompt.premiumUnlockCostPoints} ${stringResource(R.string.rewards_credits)}",
                compact = compact,
            )
        }
    }
}

@Composable
private fun PromptMetaChip(
    text: String,
    compact: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(if (compact) 9.dp else 12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = if (compact) 0.40f else 0.46f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.26f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = if (compact) 8.dp else 10.dp, vertical = if (compact) 4.dp else 6.dp),
            style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PromptStatsRow(
    views: Int,
    likes: Int,
    favorites: Int,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatChip(icon = Icons.Default.Visibility, value = views, compact = compact)
        StatChip(icon = Icons.Default.ThumbUp, value = likes, compact = compact)
        StatChip(icon = Icons.Default.BookmarkAdded, value = favorites, compact = compact)
    }
}

@Composable
private fun StatChip(
    icon: ImageVector,
    value: Int,
    compact: Boolean = false,
) {
    Surface(
        shape = RoundedCornerShape(if (compact) 9.dp else 11.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = if (compact) 0.34f else 0.52f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (compact) 6.dp else 8.dp, vertical = if (compact) 3.dp else 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(if (compact) 12.dp else 13.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = formatCompactCount(value),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PromptActionIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    active: Boolean = false,
    compact: Boolean = false,
) {
    val size = if (compact) 30.dp else 38.dp
    Surface(
        shape = RoundedCornerShape(if (compact) 10.dp else 12.dp),
        color = if (active) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceColorAtElevation(if (compact) 2.dp else 3.dp)
        },
        border = BorderStroke(
            1.dp,
            if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.28f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f),
        ),
        tonalElevation = if (active) 2.dp else 0.dp,
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(size),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(if (compact) 15.dp else 17.dp),
                tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PromptPrimaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    leadingIcon: ImageVector? = null,
    showLoading: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(if (compact) 38.dp else 44.dp),
        shape = RoundedCornerShape(if (compact) 12.dp else 14.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = if (compact) 6.dp else 9.dp),
    ) {
        when {
            showLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(if (compact) 14.dp else 16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            leadingIcon != null -> {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(if (compact) 14.dp else 16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
        }
        Text(
            text = text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun PromptSecondaryButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(if (compact) 38.dp else 44.dp),
        shape = RoundedCornerShape(if (compact) 12.dp else 14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f)),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = if (compact) 6.dp else 8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(if (compact) 14.dp else 16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun PromptsV2EmptyState(
    query: String,
    selectedCategory: String,
    selectedFilter: PromptChipFilter,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasFilter = query.isNotBlank() || !selectedCategory.equals(CATEGORY_ALL, ignoreCase = true) || selectedFilter != PromptChipFilter.All

    val title = when {
        query.isNotBlank() -> stringResource(R.string.no_results_found)
        else -> stringResource(R.string.no_prompts_available)
    }

    val subtitle = when {
        query.isNotBlank() -> stringResource(R.string.try_different_search_terms_or_clear_filters)
        !selectedCategory.equals(CATEGORY_ALL, ignoreCase = true) -> stringResource(R.string.no_prompts_found_in_this_category)
        selectedFilter != PromptChipFilter.All -> stringResource(R.string.please_check_back_later)
        else -> stringResource(R.string.check_back_later_for_new_prompts)
    }

    Column(
        modifier = modifier.padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.26f)),
            tonalElevation = 2.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f)),
                ) {
                    Text(
                        text = stringResource(R.string.search_icon_emoji),
                        style = MaterialTheme.typography.displayMedium,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )

                if (hasFilter) {
                    Spacer(modifier = Modifier.height(20.dp))
                    PromptPrimaryButton(
                        text = stringResource(R.string.clear_filters),
                        onClick = onClearFilters,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

private fun formatCompactCount(value: Int): String {
    val safeValue = value.coerceAtLeast(0)
    return when {
        safeValue >= 1_000_000 -> {
            val millions = safeValue / 1_000_000f
            if (millions >= 10f) "${millions.toInt()}M" else String.format("%.1fM", millions)
        }

        safeValue >= 1_000 -> {
            val thousands = safeValue / 1_000f
            if (thousands >= 10f) "${thousands.toInt()}K" else String.format("%.1fK", thousands)
        }

        else -> safeValue.toString()
    }
}

private fun matchesSelectedCategory(prompt: V2PromptDto, selectedCategory: String): Boolean {
    if (selectedCategory.equals(CATEGORY_ALL, ignoreCase = true)) return true
    return prompt.category?.equals(selectedCategory, ignoreCase = true) == true
}

private fun matchesSelectedFilter(prompt: V2PromptDto, filter: PromptChipFilter): Boolean {
    return when (filter) {
        PromptChipFilter.All -> true
        PromptChipFilter.Free -> !prompt.isPremium
        PromptChipFilter.Premium -> prompt.isPremium
        PromptChipFilter.Featured -> prompt.isFeatured
    }
}

private fun V2PromptDto.previewText(): String {
    return if (isLocked) {
        teaserText.orEmpty().ifBlank { shortPrompt.orEmpty() }
    } else {
        shortPrompt.orEmpty().ifBlank { fullPrompt.orEmpty() }
    }
}

private fun V2PromptDto.copyTextForList(): String {
    return if (isLocked) {
        teaserText.orEmpty().ifBlank { shortPrompt.orEmpty() }
    } else {
        fullPrompt.orEmpty().ifBlank { shortPrompt.orEmpty() }
    }
}

private fun nativeAdForPosition(
    index: Int,
    totalItems: Int,
    nativeAds: List<NativeAd>,
): NativeAd? {
    if (nativeAds.isEmpty()) return null
    val gap = dynamicGap(position = index, totalItems = totalItems)
    if (gap == Int.MAX_VALUE || gap <= 0) return null

    val shouldShowAd = index >= gap && (index % gap == gap - 1)
    if (!shouldShowAd) return null

    val adIndex = (index / gap) % nativeAds.size
    return nativeAds.getOrNull(adIndex)
}

private fun buildGridFeed(
    prompts: List<V2PromptDto>,
    includeAds: Boolean,
): List<PromptGridFeedItem> {
    if (prompts.isEmpty()) return emptyList()

    val feed = ArrayList<PromptGridFeedItem>(prompts.size + 8)
    prompts.forEachIndexed { index, prompt ->
        feed.add(PromptGridFeedItem.PromptItem(prompt))
        if (includeAds) {
            val gap = dynamicGap(position = index, totalItems = prompts.size)
            if (gap != Int.MAX_VALUE && gap > 0 && index >= gap && (index % gap == gap - 1)) {
                feed.add(
                    PromptGridFeedItem.AdItem(
                        slotKey = "grid_ad_${prompt.id}_$index",
                        adIndex = index / gap,
                        anchorPromptId = prompt.id,
                    ),
                )
            }
        }
    }
    return feed
}

private fun chooseMixedAdStyle(
    key: String,
    mode: V2ViewMode,
): PromptNativeAdStyle {
    val bucket = kotlin.math.abs(key.hashCode()) % 10
    return when (mode) {
        V2ViewMode.LIST -> if (bucket < 4) PromptNativeAdStyle.Compact else PromptNativeAdStyle.LargeMedia
        V2ViewMode.GRID -> PromptNativeAdStyle.LargeMedia
    }
}

private fun dynamicGap(
    position: Int,
    totalItems: Int = 200,
): Int {
    if (position < 6) return Int.MAX_VALUE
    if (totalItems < 30) return 10
    if (totalItems < 50) return 8

    return when {
        position < 15 -> 10
        position < 35 -> 8
        position < 60 -> 7
        position < 120 -> 6
        else -> 6
    }
}

private suspend fun observeGridEndReached(
    gridState: LazyGridState,
    itemCount: Int,
    canLoadMore: Boolean,
    onEndReached: () -> Unit,
) {
    if (!canLoadMore) return
    snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
        .distinctUntilChanged()
        .collect { lastVisible ->
            if (canLoadMore && lastVisible >= (itemCount - 6).coerceAtLeast(0)) {
                onEndReached()
            }
        }
}
