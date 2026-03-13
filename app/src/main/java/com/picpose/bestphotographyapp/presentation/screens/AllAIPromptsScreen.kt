/**
 * ---
 * File: AllAIPromptsScreen.kt
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

package com.picpose.bestphotographyapp.presentation.screens

import android.app.Activity
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Visibility
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.SubcomposeAsyncImage
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.picpose.bestphotographyapp.R
import com.picpose.bestphotographyapp.core.constants.Constants
import com.picpose.bestphotographyapp.presentation.ads.AdsManager
import com.picpose.bestphotographyapp.data.admob.AdMobConfigManager
import com.picpose.bestphotographyapp.data.database.entities.EngagementEntity
import com.picpose.bestphotographyapp.data.models.AIPrompt
import com.picpose.bestphotographyapp.presentation.ads.InlineNativeAdCard
import com.picpose.bestphotographyapp.presentation.ads.LargeNativeAdCard
import com.picpose.bestphotographyapp.presentation.ads.LargeNativeAdCardForGrid
import com.picpose.bestphotographyapp.presentation.components.AIPromptCard
import com.picpose.bestphotographyapp.presentation.search.SearchMatchers
import com.picpose.bestphotographyapp.presentation.viewmodels.AIPromptViewModel
import com.picpose.bestphotographyapp.core.utils.displayViews
import com.picpose.bestphotographyapp.core.utils.setText
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

enum class ViewMode { GRID, LIST }

// Local style enum for this screen
private enum class NativeAdStyle {
    LargeMedia,
    Compact
}

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


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllAIPromptsScreen(
    onBack: () -> Unit,
    onPromptClick: (String) -> Unit = {},
    viewModel: AIPromptViewModel = hiltViewModel(),
    initialCategory: String? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val localStates by viewModel.localEngagementStates.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity
    val clipboard = LocalClipboard.current
    val snackBarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var showSearch by remember { mutableStateOf(false) }
    var viewMode by remember { mutableStateOf(ViewMode.GRID) }

    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()

    val categoryListState = rememberLazyListState()

    // 🔁 Native Ad pool (preload multiple)
    var nativeAds by remember { mutableStateOf<List<NativeAd>>(emptyList()) }
    val adContext = LocalContext.current

    DisposableEffect(adContext) {
        var disposed = false
        val loadedAds = mutableListOf<NativeAd>()

        val adUnitId = if (AdsManager.canShowAds()) {
            AdsManager.getAdUnitId(AdsManager.KEY_NATIVE_AD)
        } else {
            null
        }

        if (!adUnitId.isNullOrBlank()) {
            val adLoader = AdLoader.Builder(
                adContext,
                adUnitId
            )
                .forNativeAd { ad ->
                    if (disposed) {
                        // Safety: if composable already disposed, don't keep the ad
                        ad.destroy()
                    } else {
                        loadedAds.add(ad)
                        nativeAds = loadedAds.toList()
                    }
                }
                .withNativeAdOptions(
                    NativeAdOptions.Builder().build()
                )
                .build()

            // Preload a small pool (e.g. 3 ads)
            repeat(3) {
                adLoader.loadAd(AdRequest.Builder().build())
            }
        }

        onDispose {
            disposed = true
            nativeAds.forEach { it.destroy() }
            nativeAds = emptyList()
        }
    }

    LaunchedEffect(initialCategory) {

        // 🧠 initialCategory already decoded in NavGraph
        val category = initialCategory

        if (!category.isNullOrBlank() && category != "All") {
            viewModel.onCategorySelected(category)
        } else {
            viewModel.onCategorySelected("All")
        }

        // Categories chips ke liye
        viewModel.loadCategories()
    }



    // Error handling
    LaunchedEffect(uiState.error) {
        uiState.error?.let { msg ->
            coroutineScope.launch {
                snackBarHostState.showSnackbar(msg)
                viewModel.clearError()
            }
        }
    }


    // Infinite scroll listener for LIST
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { lastVisible ->
                val totalItems = uiState.allPrompts.size
                if (lastVisible >= totalItems - 5) {
                    viewModel.onListEndReached()
                }
            }
    }

    // Infinite scroll listener for GRID
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { lastVisible ->
                val totalItems = uiState.allPrompts.size
                if (lastVisible >= totalItems - 4) {
                    viewModel.onListEndReached()
                }
            }
    }

    val normalizedQuery = remember(uiState.searchQuery) { uiState.searchQuery.trim() }
    val displayPrompts = remember(uiState.allPrompts, normalizedQuery) {
        uiState.allPrompts.filter { prompt ->
            SearchMatchers.matchesAIPrompt(prompt, normalizedQuery)
        }
    }

    val categories = uiState.categories

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val total = uiState.totalPrompts.takeIf { it > 0 } ?: displayPrompts.size
                    Text(
                        text = if (uiState.selectedCategory != "All")
                            stringResource(
                                R.string.prompts_for_category_count,
                                uiState.selectedCategory,
                                displayPrompts.size
                            )
                        else
                            stringResource(R.string.all_prompts_count, total)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.loadAllPrompts(
                            page = 1,
                            forceRefresh = true
                        )
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(
                            if (showSearch) Icons.Default.SearchOff else Icons.Default.Search,
                            contentDescription = stringResource(R.string.search)
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
                            contentDescription = stringResource(R.string.change_view)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackBarHostState) },
        contentWindowInsets = WindowInsets(0)
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
                )
        ) {

            // Search Bar
            if (showSearch) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    placeholder = { Text(stringResource(R.string.search_prompts_placeholder)) },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear))
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // Category Chips
            if (categories.isNotEmpty()) {
                LazyRow(
                    state = categoryListState,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(categories) { index, category ->

                        FilterChip(
                            selected = uiState.selectedCategory == category,
                            onClick = {
                                // 🔥 1️⃣ Scroll CONTENT to top
                                coroutineScope.launch {
                                    listState.scrollToItem(0)
                                    gridState.scrollToItem(0)
                                }

                                // 🔥 2️⃣ Scroll CATEGORY CHIP to center
                                coroutineScope.launch {
                                    categoryListState.animateScrollToItem(
                                        index = index,
                                        scrollOffset = -200 // 🔥 center feel (adjust if needed)
                                    )
                                }

                                // 🔥 3️⃣ Load category data
                                viewModel.onCategorySelected(category)
                            },
                            label = { Text(category) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF6366F1),
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }

                when {
                uiState.isLoading && displayPrompts.isEmpty() -> {
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
                            viewModel.onCategorySelected("All")
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 80.dp)
                    )
                }

                else -> {
                    when (viewMode) {
                        ViewMode.GRID -> {
                            LazyVerticalGrid(
                                state = gridState,
                                columns = GridCells.Adaptive(minSize = 160.dp),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start = 12.dp,
                                    end = 12.dp,
                                    top = 8.dp,
                                    bottom = 24.dp
                                ),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                itemsIndexed(
                                    items = displayPrompts,
                                    key = { _, item -> item.id }
                                ) { index, prompt ->

                                    val gap = dynamicGap(index)
                                    val adToShow = if (nativeAds.isNotEmpty()) {
                                        nativeAds[(index / gap) % nativeAds.size]
                                    } else null

                                    val shouldShowAdHere =
                                        adToShow != null &&
                                                index >= gap &&
                                                (index % gap == gap - 1)

                                    if (shouldShowAdHere) {
                                        val style = chooseMixedAdStyle(
                                            key = prompt.id,
                                            mode = ViewMode.GRID
                                        )
                                        when (style) {
                                            NativeAdStyle.LargeMedia -> {
                                                LargeNativeAdCardForGrid(
                                                    nativeAd = adToShow,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }

                                            NativeAdStyle.Compact -> {
                                                InlineNativeAdCard(
                                                    nativeAd = adToShow,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        }
                                    } else {

                                        // 🔥 STEP-4.3: get local engagement for THIS prompt
                                        val local = localStates[prompt.id]

                                        GridPromptItem(
                                            prompt = prompt,
                                            localEngagement = local, // 🔥 NEW (CENTRAL SOURCE)
                                            onClick = {
                                            val id = prompt.id
                                            if (id.isNotBlank()) {
                                                onPromptClick(id)
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    context.getString(R.string.invalid_id),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                            }
                                        )
                                    }

                                }
                            }
                        }

                        ViewMode.LIST -> {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 8.dp,
                                    bottom = 24.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                itemsIndexed(
                                    items = displayPrompts,
                                    key = { _, item -> item.id }
                                ) { index, prompt ->

                                    val local = localStates[prompt.id]

                                    AIPromptCard(
                                        prompt = prompt,
                                        localEngagement = local, // 🔥 STEP-4: CENTRAL VIEW SOURCE

                                        // AllAIPromptsScreen.kt में - onClick handler update
                                        onClick = {
                                            val id = prompt.id
                                            if (id.isNotBlank()) {
                                                // 🔥 IMPORTANT: Don't call loadPromptById here
                                                // Navigation should only navigate, not load data
                                                onPromptClick(id)

                                                Log.d("ListScreen", "Navigating to detail: $id")
                                                Log.d("ListScreen", "Current views: ${prompt.views}")
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    context.getString(R.string.invalid_id),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        },

                                        onCopy = {
                                            val textToCopy =
                                                prompt.shortPrompt ?: prompt.fullPrompt ?: ""
                                            coroutineScope.launch {
                                                clipboard.setText(textToCopy, label = "prompt")
                                            }
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.prompt_copied_toast),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        },

                                        // 👍 LIKE (ID based)
                                        onLikeClick = { _ ->
                                            viewModel.onLikeClicked(prompt)
                                        },

                                        // ⭐ FAVORITE (BOOKMARK)
                                        onFavoriteClick = { _ ->
                                            viewModel.onFavoriteClicked(prompt)
                                        },

                                        showFavoriteIcon = true,
                                        isCompact = false
                                    )


                                    val gap = dynamicGap(index)
                                    val adToShow = if (nativeAds.isNotEmpty()) {
                                        nativeAds[(index / gap) % nativeAds.size]
                                    } else null

                                    val shouldShowAdHere =
                                        adToShow != null &&
                                                index >= gap &&
                                                (index % gap == gap - 1)

                                    if (shouldShowAdHere) {
                                        Spacer(modifier = Modifier.height(8.dp))

                                        val style = chooseMixedAdStyle(
                                            key = prompt.id,
                                            mode = ViewMode.LIST
                                        )

                                        when (style) {
                                            NativeAdStyle.Compact -> {
                                                InlineNativeAdCard(
                                                    nativeAd = adToShow,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }

                                            NativeAdStyle.LargeMedia -> {
                                                LargeNativeAdCard(
                                                    nativeAd = adToShow,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))
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
   Mixed Style Selection
------------------------ */
private fun chooseMixedAdStyle(
    key: String,
    mode: ViewMode
): NativeAdStyle {
    val bucket = kotlin.math.abs(key.hashCode()) % 10

    return when (mode) {

        ViewMode.LIST -> {
            // LIST: 0–3 compact (40%), 4–9 large (60%)
            if (bucket < 4) NativeAdStyle.Compact else NativeAdStyle.LargeMedia
        }

        ViewMode.GRID -> {
            // GRID: 0% compact → ALWAYS LargeMedia
            NativeAdStyle.LargeMedia
        }
    }
}


/* ------------------------
   Grid Prompt Card
------------------------ */
@Composable
private fun GridPromptItem(
    prompt: AIPrompt,
    localEngagement: EngagementEntity?,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {

            // IMAGE
            SubcomposeAsyncImage(
                model = prompt.imageUrl,
                contentDescription = prompt.title,
                modifier = Modifier
                    .height(195.dp)
                    .fillMaxWidth()
                    .clip(
                        RoundedCornerShape(
                            topStart = 14.dp,
                            topEnd = 14.dp
                        )
                    ),
                contentScale = ContentScale.Crop
            )

            // CONTENT
            Column(
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxWidth()
            ) {

                // TITLE
                Text(
                    text = prompt.title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                // SHORT PROMPT
                Text(
                    text = prompt.shortPrompt ?: "",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                // 👁 VIEWS — SINGLE SOURCE OF TRUTH
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = stringResource(R.string.views),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    Text(
                        text = prompt.displayViews(localEngagement).toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}


/* ------------------------
   Empty State
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
        Text(stringResource(R.string.search_icon_emoji), style = MaterialTheme.typography.displayLarge)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (searchQuery.isNotEmpty()) stringResource(R.string.no_results_found) else stringResource(R.string.no_prompts_available),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        val message = when {
            searchQuery.isNotEmpty() -> stringResource(R.string.try_different_search_terms_or_clear_filters)
            selectedCategory != "All" -> stringResource(R.string.no_prompts_found_in_this_category)
            else -> stringResource(R.string.check_back_later_for_new_prompts)
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
                Text(stringResource(R.string.clear_filters))
            }
        }
    }
}

/* ------------------------
   Optional Shimmer Brush
------------------------ */
@Suppress("unused")
@Composable
private fun shimmerBrush(): Brush {
    val transition = rememberInfiniteTransition()
    val shimmer by transition.animateFloat(
        initialValue = -200f,
        targetValue = 200f,
        animationSpec = infiniteRepeatable(
            tween(900, easing = LinearEasing),
            RepeatMode.Restart
        )
    )
    return Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        start = Offset(shimmer, shimmer),
        end = Offset(shimmer + 200f, shimmer + 200f)
    )
}
