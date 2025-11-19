package com.picpose.bestphotographyapp.presentation.screens

import android.app.Activity
import android.os.Build
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.SubcomposeAsyncImage
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.picpose.bestphotographyapp.data.models.AIPrompt
import com.picpose.bestphotographyapp.presentation.ads.InlineNativeAdCard
import com.picpose.bestphotographyapp.presentation.ads.LargeNativeAdCard
import com.picpose.bestphotographyapp.presentation.ads.LargeNativeAdCardForGrid
import com.picpose.bestphotographyapp.presentation.components.AIPromptCard
import com.picpose.bestphotographyapp.presentation.viewmodels.AIPromptViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs

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
    val context = LocalContext.current
    val activity = context as? Activity
    val clipboardManager = LocalClipboardManager.current
    val snackBarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var showSearch by remember { mutableStateOf(false) }
    var viewMode by remember { mutableStateOf(ViewMode.GRID) }

    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()

    // Edge-to-edge for Android 11+
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity?.window?.let { WindowCompat.setDecorFitsSystemWindows(it, false) }
        }
    }

    // 🔁 Native Ad pool (preload multiple)
    var nativeAds by remember { mutableStateOf<List<NativeAd>>(emptyList()) }
    val adContext = LocalContext.current

    DisposableEffect(adContext) {
        var disposed = false
        val loadedAds = mutableListOf<NativeAd>()

        val adLoader = AdLoader.Builder(
            adContext,
            "ca-app-pub-3940256099942544/2247696110" // ✅ Test native ad ID
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

        onDispose {
            disposed = true
            nativeAds.forEach { it.destroy() }
            nativeAds = emptyList()
        }
    }

    // Initial data load
    LaunchedEffect(Unit) {
        viewModel.loadAllPrompts(page = 1, forceRefresh = true)
        viewModel.loadCategories()
        initialCategory?.takeIf { it.isNotBlank() && it != "All" }?.let {
            viewModel.updateSelectedCategory(it)
        }
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val total = uiState.totalPrompts.takeIf { it > 0 } ?: displayPrompts.size
                    Text(
                        text = if (uiState.selectedCategory != "All")
                            "${uiState.selectedCategory} Prompts (${displayPrompts.size})"
                        else
                            "All Prompts ($total)"
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
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

            // Category Chips
            if (categories.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(categories) { _, category ->
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
                            viewModel.updateSelectedCategory("All")
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
                                    bottom = 100.dp
                                ),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                itemsIndexed(
                                    items = displayPrompts,
                                    key = { index, item -> item.id ?: index.toString() }
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
                                            key = prompt.id ?: index.toString(),
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
                                        GridPromptItem(
                                            prompt = prompt,
                                            onClick = {
                                                val id = prompt.id
                                                if (id != null) {
                                                    viewModel.selectPromptForDetail(prompt)
                                                    onPromptClick(id)
                                                } else {
                                                    Toast.makeText(
                                                        context,
                                                        "Invalid ID",
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
                                    bottom = 100.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                itemsIndexed(
                                    items = displayPrompts,
                                    key = { index, item -> item.id ?: index.toString() }
                                ) { index, prompt ->

                                    AIPromptCard(
                                        prompt = prompt,
                                        onClick = {
                                            val id = prompt.id
                                            if (id != null) {
                                                viewModel.selectPromptForDetail(prompt)
                                                onPromptClick(id)
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    "Invalid prompt ID",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        },
                                        onCopy = {
                                            val textToCopy =
                                                prompt.shortPrompt ?: prompt.fullPrompt ?: ""
                                            clipboardManager.setText(AnnotatedString(textToCopy))
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
                                            key = prompt.id ?: index.toString(),
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
            SubcomposeAsyncImage(
                model = prompt.imageUrl,
                contentDescription = prompt.title,
                modifier = Modifier
                    .height(190.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)),
                contentScale = ContentScale.Crop
            )

            Column(
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = prompt.title ?: "",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold
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

/* ------------------------
   Optional Shimmer Brush
------------------------ */
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
