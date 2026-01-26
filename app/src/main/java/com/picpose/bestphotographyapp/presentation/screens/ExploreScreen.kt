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
import androidx.compose.foundation.layout.*
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
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.picpose.bestphotographyapp.data.admob.AdMobConfigManager
import com.picpose.bestphotographyapp.data.models.AIPrompt
import com.picpose.bestphotographyapp.data.models.GuidePost
import com.picpose.bestphotographyapp.presentation.ads.LargeNativeAdCard
import com.picpose.bestphotographyapp.presentation.components.AIPromptCardWithEffects
import com.picpose.bestphotographyapp.presentation.components.GuidePostCard
import com.picpose.bestphotographyapp.presentation.viewmodels.*
import com.picpose.bestphotographyapp.core.utils.ConnectivityObserver
import com.picpose.bestphotographyapp.core.utils.copyToClipboard

/** 🧠 Smart dynamic frequency (scroll-depth based, 6–8 range) */
/** ⚡ Enhanced adaptive frequency for best UX + Revenue */
private fun dynamicGap(
    position: Int,
    totalItems: Int = 200,   // fallback when unknown
    engagementScore: Float = 1f // 🔥 future use for ML tuning
): Int {

    // 🔐 Protection: Never show ads too early
    if (position < 6) return Int.MAX_VALUE // disables ad

    // 📌 If list/content is small → fewer ads
    if (totalItems < 30) return 10
    if (totalItems < 50) return 8

    // 🧠 Scroll-depth based dynamic range (6–10)
    val base = when {
        position < 15 -> 10   // very gentle at top
        position < 35 -> 8    // moderate
        position < 60 -> 7    // stronger frequency
        position < 120 -> 6   // engaged users
        else -> 6             // highly engaged → max monetization
    }

    // ⚡ Engagement tuning (future powered)
    val adjust = when {
        engagementScore > 1.4f -> -1  // happy scrolling → +ads
        engagementScore < 0.8f -> +1  // scrolling too fast → reduce ads
        else -> 0
    }

    return (base + adjust).coerceIn(6, 10)
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ExploreScreen(
    onNavigateToPromptDetail: (AIPrompt) -> Unit = {},
    onNavigateToGuidePostDetail: (GuidePost) -> Unit = {},
    exploreViewModel: ExploreViewModel = hiltViewModel()
) {
    val uiState by exploreViewModel.uiState.collectAsStateWithLifecycle()
    val localStates by exploreViewModel.localEngagementStates.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val snackbarHostState = remember { SnackbarHostState() }

    // we use a local flag for manual refresh button animation control
    var manualRefreshInFlight by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val connectivityObserver = remember { ConnectivityObserver(context) }
    val networkStatus by connectivityObserver.observe().collectAsState(initial = ConnectivityObserver.Status.Available)

    val activity = context as? Activity
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            activity?.window?.let { WindowCompat.setDecorFitsSystemWindows(it, false) }
    }

    // 🔁 Native Ad pool (preload multiple)
    var nativeAds by remember { mutableStateOf<List<NativeAd>>(emptyList()) }
    val adContext = LocalContext.current

    DisposableEffect(adContext) {
        var disposed = false
        val loadedAds = mutableListOf<NativeAd>()

        val adLoader = AdLoader.Builder(
            adContext,
            AdMobConfigManager.TEST_NATIVE_ID // Test native ad ID
        )
            .forNativeAd { ad ->
                if (disposed) ad.destroy()
                else {
                    loadedAds.add(ad)
                    nativeAds = loadedAds.toList()
                }
            }
            .withNativeAdOptions(NativeAdOptions.Builder().build())
            .build()

        repeat(3) { adLoader.loadAd(AdRequest.Builder().build()) }

        onDispose {
            disposed = true
            nativeAds.forEach { it.destroy() }
            nativeAds = emptyList()
        }
    }


    // pagination detection
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastVisibleIndex ->
                if (lastVisibleIndex != null &&
                    lastVisibleIndex >= uiState.content.size - 3 &&
                    uiState.hasMore &&
                    !uiState.isLoading
                ) {
                    exploreViewModel.loadMore()
                }
            }
    }

    var showFilters by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
        topBar = {
            ExploreTopBar(
                searchQuery = uiState.searchQuery,
                onSearchQueryChange = { exploreViewModel.updateSearchQuery(it) },
                onRefresh = {
                    // request a full reset + refresh (atomic; UI content remains visible)
                    manualRefreshInFlight = true
                    exploreViewModel.refresh(resetFilters = true)
                },
                onToggleFilters = { showFilters = !showFilters },
                categories = uiState.categories,
                selectedCategory = uiState.selectedCategory,
                selectedContentFilter = uiState.selectedContentFilter,
                selectedSortOption = uiState.selectedSortOption,
                onCategorySelected = { exploreViewModel.updateCategory(it) },
                onContentFilterSelected = { exploreViewModel.updateContentFilter(it) },
                onSortOptionSelected = { exploreViewModel.updateSortOption(it) },
                isManualRefreshLoading = manualRefreshInFlight
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0)
    ) { innerPadding ->

        // When ViewModel finishes loading, clear the local manual flag and show snackbar
        LaunchedEffect(uiState.isLoading, uiState.isRefreshing, manualRefreshInFlight) {
            if (manualRefreshInFlight && !uiState.isLoading) {
                manualRefreshInFlight = false
                listState.animateScrollToItem(0)
                snackbarHostState.showSnackbar(
                    if (uiState.content.isNotEmpty()) "All filters and content refreshed"
                    else "No new content found"
                )
            }
        }

        // No internet
        if (networkStatus != ConnectivityObserver.Status.Available && uiState.content.isEmpty() && !uiState.isLoading) {
            NoInternetSection(onRetry = { exploreViewModel.refresh() })
        } else {
            when (uiState.loadState) {
                ExploreLoadState.INITIAL -> {
                    // full screen shimmer
                    ShimmerLoadingExploreScreen()
                }
                ExploreLoadState.LOADING -> {
                    // fallback loading (should be rarely used because INITIAL handles loading with empty content)
                    LoadingSection()
                }
                ExploreLoadState.EMPTY -> {
                    EmptyStateSection(
                        searchQuery = uiState.searchQuery,
                        selectedCategory = uiState.selectedCategory,
                        onClearFilters = {
                            exploreViewModel.updateSearchQuery("")
                            exploreViewModel.updateCategory("All")
                            exploreViewModel.updateContentFilter(ContentFilter.ALL)
                            exploreViewModel.refresh(resetFilters = true)
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 40.dp, bottom = 60.dp)
                    )
                }
                ExploreLoadState.ERROR -> {
                    EmptyStateSection(
                        searchQuery = uiState.searchQuery,
                        selectedCategory = uiState.selectedCategory,
                        onClearFilters = { exploreViewModel.refresh(resetFilters = true) },
                        modifier = Modifier.fillMaxWidth().padding(top = 40.dp, bottom = 60.dp)
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
                            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp
                        ),
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(innerPadding)
                            .consumeWindowInsets(innerPadding)
                    ) {
                        if (uiState.content.isNotEmpty() && showFilters) {
                            stickyHeader {
                                FrostedFiltersStickyHeader(uiState = uiState, viewModel = exploreViewModel)
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
                        ) { index, content ->

                            val gap = dynamicGap(index)
                            val adToShow = if (nativeAds.isNotEmpty()) {
                                nativeAds[(index / gap) % nativeAds.size]
                            } else null

                            val shouldShowAdHere =
                                adToShow != null &&
                                        index >= gap &&
                                        (index % gap == gap - 1)

                            if (shouldShowAdHere) {
                                LargeNativeAdCard(
                                    nativeAd = adToShow,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(Modifier.height(16.dp))
                            }

                            when (content) {
                                is ExploreContent.AIPromptContent -> {

                                    // ✅ ADD THIS LINE
                                    val local = localStates[content.prompt.id]

                                    AIPromptCardWithEffects(
                                        prompt = content.prompt,
                                        localEngagement = local, // ✅ NOW CORRECT
                                        onClick = {
                                            content.prompt.id?.let { id ->
                                                //viewModel.onPromptViewed(id)   // ✅ VIEW INCREMENT
                                                onNavigateToPromptDetail(content.prompt)
                                            }
                                        },
                                        onCopy = {
                                            val text = content.prompt.shortPrompt ?: content.prompt.fullPrompt ?: ""
                                            copyToClipboard(context, clipboard, text, coroutineScope)
                                        },
                                        showFavoriteIcon = true,
                                        onLikeClick = { updatedPrompt ->
                                            exploreViewModel.togglePromptLike(updatedPrompt)
                                        },
                                        onBookmarkClick = { updatedPrompt ->
                                            exploreViewModel.togglePromptBookmark(updatedPrompt)
                                        },
                                        modifier = Modifier.animateItem()
                                    )
                                }

                                is ExploreContent.GuidePostContent -> {
                                    GuidePostCard(
                                        guidePost = content.guidePost,
                                        onClick = { onNavigateToGuidePostDetail(content.guidePost) },
                                        onFavoriteClick = { exploreViewModel.toggleGuidePostFavorite(it) },
                                        modifier = Modifier.animateItem()
                                    )
                                }
                            }
                        }

                        // inline shimmers when loading more
                        if (uiState.isLoading && uiState.content.isNotEmpty()) {
                            item { RepeatInlineShimmers() }
                        }
                    }
                }
            }
        }
    }
}

/* -------------------------
   UI helpers: NoInternet, Shimmers, TopBar (with continuous spinner)
   ------------------------- */

@Composable
fun NoInternetSection(onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(imageVector = Icons.Default.WifiOff, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(72.dp))
            Text(text = "No Internet Connection", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(text = "Connect to Wi-Fi or mobile data to continue.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onRetry) { Text("Retry") }
        }
    }
}

@Composable
private fun ShimmerLoadingExploreScreen(innerPadding: PaddingValues? = null) {
    val infiniteTransition = rememberInfiniteTransition()
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(animation = tween(900, easing = LinearEasing), repeatMode = RepeatMode.Reverse)
    )

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 8.dp, start = 12.dp, end = 12.dp, bottom = 24.dp),
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    ) {
        items(4) {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface).padding(12.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(12.dp)).background(Color.LightGray.copy(alpha = alpha)))
                Spacer(modifier = Modifier.height(12.dp))
                Box(modifier = Modifier.fillMaxWidth(0.7f).height(18.dp).clip(RoundedCornerShape(4.dp)).background(Color.LightGray.copy(alpha = alpha)))
                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth(0.9f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(Color.LightGray.copy(alpha = alpha)))
                Spacer(modifier = Modifier.height(4.dp))
                Box(modifier = Modifier.fillMaxWidth(0.6f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(Color.LightGray.copy(alpha = alpha)))
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    repeat(3) {
                        Box(modifier = Modifier.size(width = 60.dp, height = 26.dp).clip(RoundedCornerShape(6.dp)).background(Color.LightGray.copy(alpha = alpha)))
                    }
                }
            }
        }
    }
}

@Composable
private fun InlineShimmerItem() {
    val infinite = rememberInfiniteTransition()
    val alpha by infinite.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(animation = tween(900, easing = LinearEasing), repeatMode = RepeatMode.Reverse)
    )

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface).padding(12.dp)) {
        Box(modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(12.dp)).background(Color.LightGray.copy(alpha = alpha)))
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth(0.6f).height(18.dp).clip(RoundedCornerShape(4.dp)).background(Color.LightGray.copy(alpha = alpha)))
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth(0.85f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(Color.LightGray.copy(alpha = alpha)))
    }
}

@Composable
fun RepeatInlineShimmers() { Column { repeat(3) { InlineShimmerItem() } } }

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

    // continuous rotation via infinite transition, only applied when loading
    val infinite = rememberInfiniteTransition()
    val spin by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(900, easing = LinearEasing))
    )

    TopAppBar(
        modifier = Modifier, // ❗ No inset padding here
        title = {
            AnimatedContent(targetState = isSearchExpanded, transitionSpec = {
                slideInHorizontally() + fadeIn() togetherWith slideOutHorizontally() + fadeOut()
            }) { expanded ->
                if (expanded) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = { Text("Search content...") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = {
                            IconButton(onClick = { isSearchExpanded = false; onSearchQueryChange("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Close search")
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text("Explore", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
            }
        },
        actions = {
            if (!isSearchExpanded) {
                IconButton(onClick = { isSearchExpanded = true }) {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                }

                IconButton(onClick = { onRefresh() }) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        modifier = Modifier.rotate(if (isManualRefreshLoading) spin else 0f)
                    )
                }

                val filterRotation by animateFloatAsState(targetValue = if (isFilterExpanded) 180f else 0f, animationSpec = spring(stiffness = 300f, dampingRatio = 0.6f))
                val scale by animateFloatAsState(targetValue = if (isFilterExpanded) 1.1f else 1f, animationSpec = spring(stiffness = 400f))
                FilterButtonWithIndicator(isFilterExpanded = isFilterExpanded, rotation = filterRotation, scale = scale, onClick = { isFilterExpanded = !isFilterExpanded; onToggleFilters() })
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
    )
}



@Composable
private fun EmptyStateSection(
    searchQuery: String,
    selectedCategory: String,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier
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
