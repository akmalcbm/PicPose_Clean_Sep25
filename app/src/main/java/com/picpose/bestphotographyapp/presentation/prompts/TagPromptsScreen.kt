/**
 * ---
 * File: TagPromptsScreen.kt
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

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Label
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import com.google.android.gms.ads.AdLoader
import com.picpose.bestphotographyapp.R
import com.picpose.bestphotographyapp.data.remote.dto.AIPrompt
import com.picpose.bestphotographyapp.components.ads.AdsManager
import com.picpose.bestphotographyapp.components.ads.AdBadge
import com.picpose.bestphotographyapp.components.common.AIPromptCard
import com.picpose.bestphotographyapp.presentation.prompts.AIPromptViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagPromptsScreen(
    tag: String,
    onBack: () -> Unit,
    onPromptClick: (String) -> Unit,
    viewModel: AIPromptViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val listState = rememberLazyListState()

    // Normalize tag (remove leading # for matching & display)
    val normalizedTag = remember(tag) { tag.removePrefix("#") }

    // Sort state
    var sortOption by rememberSaveable(normalizedTag) {
        mutableStateOf(TagSortOption.LATEST)
    }

    // Ensure prompts are loaded
    LaunchedEffect(Unit) {
        if (uiState.allPrompts.isEmpty()) {
            viewModel.loadAllPrompts()
        }
    }

    // Scroll to top when tag or sort changes
    LaunchedEffect(normalizedTag, sortOption) {
        listState.scrollToItem(0)
    }

    // Native Ad state
    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }

    DisposableEffect(normalizedTag) {
        val adUnitId = if (AdsManager.canShowAds()) {
            AdsManager.getAdUnitId(AdsManager.KEY_NATIVE_AD)
        } else {
            null
        }

        if (!adUnitId.isNullOrBlank()) {
            val adLoader = AdLoader.Builder(
                context,
                adUnitId
            )
                .forNativeAd { ad ->
                    nativeAd?.destroy()
                    nativeAd = ad
                }
                .withNativeAdOptions(
                    NativeAdOptions.Builder()
                        .build()
                )
                .build()

            adLoader.loadAd(AdRequest.Builder().build())
        }

        onDispose {
            nativeAd?.destroy()
            nativeAd = null
        }
    }

    // Filter by tag + apply sort
    val taggedPrompts: List<AIPrompt> by remember(
        uiState.allPrompts,
        normalizedTag,
        sortOption
    ) {
        mutableStateOf(
            uiState.allPrompts
                .filter { prompt ->
                    prompt.tags?.any { t ->
                        t.equals(normalizedTag, ignoreCase = true) ||
                                t.equals(tag, ignoreCase = true) // if tags already contain '#'
                    } == true
                }
                .let { list ->
                    when (sortOption) {
                        TagSortOption.LATEST -> list // API already returns latest first
                        TagSortOption.MOST_VIEWED -> list.sortedByDescending { it.views }
                        TagSortOption.MOST_LIKED -> list.sortedByDescending { it.likes }
                    }
                }
        )
    }

    val accentColor = tagAccentColor(normalizedTag)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Label,
                                contentDescription = stringResource(R.string.tag),
                                tint = Color.White,
                                modifier = Modifier.padding(end = 6.dp)
                            )
                            Text(
                                text = "#$normalizedTag",
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        if (taggedPrompts.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.prompts_count, taggedPrompts.size),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    TagSortDropdown(
                        current = sortOption,
                        onChange = { sortOption = it },
                        containerColor = accentColor
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = accentColor,
                    navigationIconContentColor = Color.White,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            TagBannerAd()
        }
    ) { innerPadding ->

        when {
            uiState.isLoading && uiState.allPrompts.isEmpty() -> {
                // 🔄 Shimmer style 2 – full-width grey bars
                TagShimmerList(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }

            taggedPrompts.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.no_prompts_found),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.try_another_tag_or_refresh),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    state = listState,
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 12.dp,
                        bottom = 80.dp // space above banner ad
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {

                    // Small header row with sort info
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.showing_prompts_count, taggedPrompts.size),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = when (sortOption) {
                                    TagSortOption.LATEST -> stringResource(R.string.sorted_latest)
                                    TagSortOption.MOST_VIEWED -> stringResource(R.string.sorted_most_viewed)
                                    TagSortOption.MOST_LIKED -> stringResource(R.string.sorted_most_liked)
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // Cards + inline native ad
                    itemsIndexed(taggedPrompts) { index, prompt ->
                        AIPromptCard(
                            prompt = prompt,
                            localEngagement = null, // ✅ REQUIRED FIX (central system rule)
                            onClick = { onPromptClick(prompt.id) },
                            onCopy = {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(context.getString(R.string.prompt_copied_to_clipboard))
                                }
                            },
                            isCompact = false,
                            showFavoriteIcon = true,

                            // 👍 LIKE — ID SAFE
                            onLikeClick = { promptId ->
                                viewModel.onLikeClicked(
                                    prompt.copy(id = promptId)
                                )
                            },

                            // ⭐ FAVORITE — ID SAFE
                            onFavoriteClick = { promptId ->
                                viewModel.onFavoriteClicked(
                                    prompt.copy(id = promptId)
                                )
                            },

                            modifier = Modifier.fillMaxWidth()
                        )

                        // Insert native ad after 5th prompt (if available)
                        if (index == 4 && nativeAd != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            TagNativeAdCard(nativeAd = nativeAd!!)
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------
// 🎨 Tag accent color (based on tag keywords)
// ---------------------------------------------
@Composable
private fun tagAccentColor(tag: String): Color {
    val lower = tag.lowercase()

    return when {
        // Fashion / style tags – pink
        listOf("fashion", "style", "outfit", "dress", "barbie").any { it in lower } ->
            Color(0xFFFF80AB)

        // Sports tags – blue
        listOf("sport", "cricket", "football", "soccer", "bike", "rider").any { it in lower } ->
            Color(0xFF448AFF)

        // Baby / kids – soft amber
        listOf("baby", "kid", "child", "toddler", "cute").any { it in lower } ->
            Color(0xFFFFC107)

        // Wedding, couples – deep pink
        listOf("wedding", "bride", "groom", "couple", "love").any { it in lower } ->
            Color(0xFFE91E63)

        else -> MaterialTheme.colorScheme.primary
    }
}

// ---------------------------------------------
// 🔽 Sort dropdown
// ---------------------------------------------
private enum class TagSortOption {
    LATEST, MOST_VIEWED, MOST_LIKED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagSortDropdown(
    current: TagSortOption,
    onChange: (TagSortOption) -> Unit,
    containerColor: Color
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        AssistChip(
            onClick = { expanded = true },
            label = {
                Text(
                    text = when (current) {
                        TagSortOption.LATEST -> stringResource(R.string.latest)
                        TagSortOption.MOST_VIEWED -> stringResource(R.string.most_viewed)
                        TagSortOption.MOST_LIKED -> stringResource(R.string.most_liked)
                    },
                    color = Color.White
                )
            },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = containerColor.copy(alpha = 0.35f),
                labelColor = Color.White
            ),
            border = null
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.latest_first)) },
                onClick = {
                    expanded = false
                    onChange(TagSortOption.LATEST)
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.most_viewed)) },
                onClick = {
                    expanded = false
                    onChange(TagSortOption.MOST_VIEWED)
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.most_liked)) },
                onClick = {
                    expanded = false
                    onChange(TagSortOption.MOST_LIKED)
                }
            )
        }
    }
}

// ---------------------------------------------
// 💡 Style-2 full width shimmer list
// ---------------------------------------------
@Composable
private fun TagShimmerList(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(6) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        shape = MaterialTheme.shapes.medium
                    )
            )
        }
    }
}

// ---------------------------------------------
// 📢 Native Ad (simple card style)
// ---------------------------------------------
@Composable
private fun TagNativeAdCard(nativeAd: NativeAd) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
    ) {
        AdBadge(modifier = Modifier.padding(bottom = 6.dp))
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { context ->
                val adView = NativeAdView(context)

                val root = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(32, 32, 32, 32)
                    clipToPadding = false
                    clipChildren = false
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }

            val media = MediaView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (180.dp.value * context.resources.displayMetrics.density).toInt()
                )
            }

            val headline = TextView(context).apply {
                textSize = 16f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }

            val body = TextView(context).apply {
                textSize = 14f
            }

            val cta = Button(context)

            root.addView(media)
            root.addView(headline)
            root.addView(body)
            root.addView(cta)

                adView.apply {
                    addView(root)
                    mediaView = media
                    headlineView = headline
                    bodyView = body
                    callToActionView = cta
                }
            },
            update = { adView ->
                (adView.headlineView as? TextView)?.text = nativeAd.headline
                (adView.bodyView as? TextView)?.apply {
                    text = nativeAd.body ?: ""
                    visibility = if (nativeAd.body.isNullOrBlank()) View.GONE else View.VISIBLE
                }
                (adView.callToActionView as? Button)?.apply {
                    text = nativeAd.callToAction ?: adView.context.getString(R.string.install)
                    visibility = if (nativeAd.callToAction.isNullOrBlank()) View.GONE else View.VISIBLE
                }
                adView.mediaView?.mediaContent = nativeAd.mediaContent
                adView.setNativeAd(nativeAd)
            }
        )
    }
}

// ---------------------------------------------
// 📺 Banner Ad Bottom Bar
// ---------------------------------------------
@Composable
private fun TagBannerAd() {
    val context = LocalContext.current
    if (!AdsManager.canShowAds()) return
    val bannerAdUnitId = AdsManager.getAdUnitId(AdsManager.KEY_HOME_BANNER)
    if (bannerAdUnitId.isNullOrBlank()) return

    Surface(
        tonalElevation = 3.dp
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            factory = {
                AdView(context).apply {
                    adUnitId = bannerAdUnitId
                    setAdSize(AdSize.BANNER)
                    loadAd(AdRequest.Builder().build())
                }
            }
        )
    }
}
