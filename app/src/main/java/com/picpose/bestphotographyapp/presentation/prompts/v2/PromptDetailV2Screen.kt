/**
 * ---
 * File: PromptDetailV2Screen.kt
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

import android.app.Activity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.google.android.gms.ads.LoadAdError
import com.picpose.bestphotographyapp.R
import com.picpose.bestphotographyapp.components.ads.AdsConfigState
import com.picpose.bestphotographyapp.components.ads.AdsManager
import com.picpose.bestphotographyapp.components.ads.InlineNativeAdCard
import com.picpose.bestphotographyapp.components.ads.NativeAdSection
import com.picpose.bestphotographyapp.components.ads.NativeAdController
import com.picpose.bestphotographyapp.components.ads.NativeAdUiState
import com.picpose.bestphotographyapp.components.common.PicPoseTopAppBar
import com.picpose.bestphotographyapp.components.common.PicPoseTopBarActionButton
import com.picpose.bestphotographyapp.data.remote.dto.v2.V2PromptDto
import com.picpose.bestphotographyapp.data.service.ads.AdManager
import com.picpose.bestphotographyapp.data.service.ads.RewardedAdManager
import com.picpose.bestphotographyapp.presentation.prompts.detail.FullScreenImageDialog
import com.picpose.bestphotographyapp.presentation.prompts.detail.openGemini
import com.picpose.bestphotographyapp.utils.ShareUtils
import com.picpose.bestphotographyapp.utils.setText
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PromptDetailV2Screen(
    promptId: String,
    onBack: () -> Unit,
    onRequireLogin: () -> Unit,
    onOpenSubscribe: () -> Unit,
    onPromptClick: (String) -> Unit = {},
    onTagClick: (String) -> Unit = {},
    viewModel: PromptDetailV2ViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val adsConfigState by AdsManager.configState.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = LocalClipboard.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val rewardedAdManager = remember { RewardedAdManager() }
    val rewardedAdState by rewardedAdManager.uiState.collectAsState()
    val adManager = remember { AdManager.getInstance() }
    val allowNativeAds = adsConfigState is AdsConfigState.Ready && AdsManager.canShowAds()

    var currentPromptId by remember { mutableStateOf(promptId) }
    var showImageDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        adManager.initialize(clickFrequency = 3)
        activity?.let { adManager.preloadAds(it) }
    }

    LaunchedEffect(promptId) {
        if (promptId != currentPromptId) {
            listState.scrollToItem(0)
            currentPromptId = promptId
        }
        viewModel.loadPrompt(promptId, forceRefresh = true)
    }

    LaunchedEffect(promptId) {
        rewardedAdManager.loadRewardedAd(context, AdsManager.KEY_REWARDED_AD)
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    if (showImageDialog) {
        val imageUrl = uiState.prompt?.imageUrl ?: uiState.prompt?.imageUrl2
        if (!imageUrl.isNullOrBlank()) {
            FullScreenImageDialog(
                imageUrl = imageUrl,
                onDismiss = { showImageDialog = false },
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            PicPoseTopAppBar(
                title = stringResource(R.string.ai_prompt_details_title),
                eyebrow = uiState.prompt?.category?.takeIf { it.isNotBlank() },
                onBack = onBack,
                actions = {
                    uiState.prompt?.let { prompt ->
                        PicPoseTopBarActionButton(
                            icon = Icons.Default.Share,
                            contentDescription = stringResource(R.string.share),
                            onClick = {
                                coroutineScope.launch {
                                    runCatching {
                                        ShareUtils.sharePrompt(
                                            context = context,
                                            promptText = prompt.fullPrompt ?: prompt.shortPrompt.orEmpty(),
                                            imageUrl = prompt.imageUrl ?: prompt.imageUrl2,
                                            title = prompt.title.ifBlank { context.getString(R.string.app_name) },
                                            chooserTitle = context.getString(R.string.share_prompt_via),
                                        )
                                    }.onFailure { throwable ->
                                        snackbarHostState.showSnackbar(
                                            throwable.message ?: context.getString(R.string.something_went_wrong),
                                        )
                                    }
                                }
                            },
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            AnimatedContent(
                targetState = currentPromptId,
                transitionSpec = {
                    fadeIn(animationSpec = tween(500)).togetherWith(fadeOut(animationSpec = tween(300)))
                },
                label = "prompt_v2_content",
            ) { targetPromptId ->
                when {
                    uiState.isLoading && uiState.prompt == null -> {
                        LoadingState()
                    }

                    uiState.prompt == null -> {
                        ErrorState(onRetry = { viewModel.loadPrompt(targetPromptId, forceRefresh = true) })
                    }

                    else -> {
                        PromptContent(
                            prompt = uiState.prompt!!,
                            similarPrompts = uiState.similarPrompts,
                            isLoadingMore = uiState.isLoadingMoreSimilar,
                            hasMoreSimilar = uiState.hasMoreSimilar,
                            isLoggedIn = isLoggedIn,
                            rewardedAdReady = rewardedAdState.isReady,
                            rewardedAdLoading = rewardedAdState.isLoading,
                            unlockState = uiState,
                            viewsCount = uiState.viewsCount,
                            likesCount = uiState.likesCount,
                            favoritesCount = uiState.favoritesCount,
                            isLiked = uiState.isLikedLocal,
                            isFavorite = uiState.isFavoriteLocal,
                            listState = listState,
                            onImageClick = { showImageDialog = true },
                            onOpenGemini = {
                                openGemini(context, promptText = uiState.prompt?.fullPrompt.orEmpty())
                                viewModel.setMessage(context.getString(R.string.ai_prompt_toast_copied_opening_gemini))
                            },
                            onCopyPrompt = {
                                val fullPrompt = uiState.prompt?.fullPrompt.orEmpty()
                                if (fullPrompt.isBlank()) return@PromptContent
                                coroutineScope.launch {
                                    clipboard.setText(fullPrompt, label = "prompt")
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    snackbarHostState.showSnackbar(context.getString(R.string.prompt_copied_to_clipboard))
                                }
                            },
                            onRequireLogin = onRequireLogin,
                            onOpenSubscribe = onOpenSubscribe,
                            onUnlockWithAd = {
                                val hostActivity = activity
                                if (hostActivity == null) {
                                    viewModel.setMessage(context.getString(R.string.rewards_ad_requires_activity))
                                } else {
                                    rewardedAdManager.showRewardedAd(
                                        activity = hostActivity,
                                        placementKey = AdsManager.KEY_REWARDED_AD,
                                        onRewardEarned = { adRewardId ->
                                            viewModel.unlockWithAd(promptId = uiState.prompt!!.id, adRewardId = adRewardId)
                                        },
                                        onUnavailable = viewModel::setMessage,
                                    )
                                }
                            },
                            onUnlockWithPoints = { viewModel.unlockWithPoints(uiState.prompt!!.id) },
                            onUnlockWithToken = { viewModel.unlockWithToken(uiState.prompt!!.id) },
                            onLikeClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.onLikeClicked(uiState.prompt!!.id)
                            },
                            onFavoriteClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.onFavoriteClicked(uiState.prompt!!.id)
                            },
                            onTagClick = onTagClick,
                            onSimilarPromptClick = { similarPrompt ->
                                coroutineScope.launch {
                                    listState.scrollToItem(0)
                                    viewModel.onSimilarPromptClicked()
                                    if (viewModel.shouldShowInterstitial() && AdsManager.canShowAds() && activity != null) {
                                        viewModel.setShowAdLoader(true)
                                        adManager.showInterstitialAndWait(activity)
                                        viewModel.setShowAdLoader(false)
                                    }
                                    onPromptClick(similarPrompt.id)
                                }
                            },
                            onLoadMore = viewModel::loadMoreSimilarPrompts,
                            allowNativeAds = allowNativeAds,
                        )
                    }
                }
            }

            if (uiState.showAdLoader) {
                AdLoadingOverlay()
            }
        }
    }
}

@Composable
private fun LoadingState() {
    val shimmerAlpha by rememberInfiniteTransition(label = "detail_shimmer").animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "detail_shimmer_alpha",
    )
    val shimmerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = shimmerAlpha)

    fun Modifier.shimmerBar(): Modifier = this.background(
        color = shimmerColor,
        shape = RoundedCornerShape(8.dp),
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                    .shimmerBar(),
            )
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .shimmerBar(),
            )
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(modifier = Modifier.fillMaxWidth(0.65f).height(24.dp).shimmerBar())
                    Box(modifier = Modifier.fillMaxWidth(0.95f).height(16.dp).shimmerBar())
                    Box(modifier = Modifier.fillMaxWidth(0.82f).height(16.dp).shimmerBar())
                }
            }
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(modifier = Modifier.fillMaxWidth(0.5f).height(22.dp).shimmerBar())
                    Box(modifier = Modifier.fillMaxWidth().height(110.dp).shimmerBar())
                    Box(modifier = Modifier.fillMaxWidth().height(52.dp).shimmerBar())
                    Box(modifier = Modifier.fillMaxWidth().height(48.dp).shimmerBar())
                }
            }
        }
    }
}

@Composable
private fun ErrorState(onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                imageVector = Icons.Default.BrokenImage,
                contentDescription = stringResource(R.string.error),
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(52.dp),
            )
            Text(text = stringResource(R.string.prompt_not_found), style = MaterialTheme.typography.titleMedium)
            Button(onClick = onRetry) {
                Text(stringResource(R.string.retry))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PromptContent(
    prompt: V2PromptDto,
    similarPrompts: List<V2PromptDto>,
    isLoadingMore: Boolean,
    hasMoreSimilar: Boolean,
    isLoggedIn: Boolean,
    rewardedAdReady: Boolean,
    rewardedAdLoading: Boolean,
    unlockState: PromptDetailV2UiState,
    viewsCount: Int,
    likesCount: Int,
    favoritesCount: Int,
    isLiked: Boolean,
    isFavorite: Boolean,
    listState: LazyListState,
    onImageClick: () -> Unit,
    onOpenGemini: () -> Unit,
    onCopyPrompt: () -> Unit,
    onRequireLogin: () -> Unit,
    onOpenSubscribe: () -> Unit,
    onUnlockWithAd: () -> Unit,
    onUnlockWithPoints: () -> Unit,
    onUnlockWithToken: () -> Unit,
    onLikeClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onTagClick: (String) -> Unit,
    onSimilarPromptClick: (V2PromptDto) -> Unit,
    onLoadMore: () -> Unit,
    allowNativeAds: Boolean,
) {
    val context = LocalContext.current
    val feedSeed = remember(prompt.id, similarPrompts) {
        buildString(capacity = prompt.id.length + (similarPrompts.size * 12)) {
            append(prompt.id)
            similarPrompts.forEach { item ->
                append('_')
                append(item.id)
            }
        }.hashCode()
    }
    val similarFeed = remember(similarPrompts, feedSeed, allowNativeAds) {
        buildSimilarPromptFeed(
            prompts = similarPrompts,
            seed = feedSeed,
            includeInlineAds = allowNativeAds,
        )
    }
    val inlineAdKeys = remember(similarFeed) {
        similarFeed.mapNotNull { feedItem ->
            (feedItem as? SimilarPromptFeedItem.NativeAdItem)?.slotKey
        }.toSet()
    }
    val inlineAdStates = remember(prompt.id) { mutableStateMapOf<String, NativeAdUiState>() }
    val inlineAdControllers = remember(prompt.id) { mutableStateMapOf<String, NativeAdController>() }

    LaunchedEffect(prompt.id, allowNativeAds, inlineAdKeys) {
        if (!allowNativeAds) {
            inlineAdControllers.values.forEach { controller -> controller.clear() }
            inlineAdControllers.clear()
            inlineAdStates.clear()
            return@LaunchedEffect
        }

        inlineAdControllers.keys.toList().forEach { key ->
            if (key !in inlineAdKeys) {
                inlineAdControllers.remove(key)?.clear()
                inlineAdStates.remove(key)
            }
        }

        inlineAdKeys.forEach { key ->
            when (inlineAdStates[key]) {
                is NativeAdUiState.Loaded,
                NativeAdUiState.Loading -> return@forEach
                else -> Unit
            }

            inlineAdStates[key] = NativeAdUiState.Loading
            val controller = inlineAdControllers.getOrPut(key) {
                NativeAdController(placementKey = AdsManager.KEY_DETAIL_NATIVE)
            }
            controller.load(
                context = context,
                callbacks = object : NativeAdController.Callbacks {
                    override fun onLoaded(ad: com.google.android.gms.ads.nativead.NativeAd) {
                        inlineAdStates[key] = NativeAdUiState.Loaded(ad)
                    }

                    override fun onFailed(error: LoadAdError) {
                        inlineAdStates[key] = NativeAdUiState.Failed
                    }

                    override fun onUnavailable(reason: String) {
                        inlineAdStates[key] = if (
                            reason == "ADS_DISABLED" ||
                            reason == "FREQUENCY_BLOCK" ||
                            reason == "NO_UNIT"
                        ) {
                            NativeAdUiState.Disabled
                        } else {
                            NativeAdUiState.Failed
                        }
                    }
                },
            )
        }
    }

    DisposableEffect(prompt.id) {
        onDispose {
            inlineAdControllers.values.forEach { controller -> controller.clear() }
            inlineAdControllers.clear()
            inlineAdStates.clear()
        }
    }

    val showStandaloneNativeAd = allowNativeAds && inlineAdKeys.isEmpty()
    val displaySimilarFeed = similarFeed.filter { feedItem ->
        when (feedItem) {
            is SimilarPromptFeedItem.PromptItem -> true
            is SimilarPromptFeedItem.NativeAdItem -> {
                when (inlineAdStates[feedItem.slotKey]) {
                    is NativeAdUiState.Loaded,
                    NativeAdUiState.Loading -> true
                    else -> false
                }
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "header_${prompt.id}") {
            PromptHeroHeader(
                prompt = prompt,
                onImageClick = onImageClick,
            )
        }

        item(key = "stats_${prompt.id}") {
            StatsRowV2(
                promptId = prompt.id,
                category = prompt.category,
                likes = likesCount,
                views = viewsCount,
                copies = prompt.copies,
                favorites = favoritesCount,
                isLiked = isLiked,
                isBookmarked = isFavorite,
                onLikeClick = { onLikeClick() },
                onBookmarkClick = { onFavoriteClick() },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }

        item(key = "prompt_meta_${prompt.id}") {
            PromptMetaCard(
                prompt = prompt,
            )
        }

        item(key = "full_prompt_${prompt.id}") {
            PromptBodyCard(
                prompt = prompt,
                isLoggedIn = isLoggedIn,
                rewardedAdReady = rewardedAdReady,
                rewardedAdLoading = rewardedAdLoading,
                unlockState = unlockState,
                onOpenGemini = onOpenGemini,
                onCopyPrompt = onCopyPrompt,
                onRequireLogin = onRequireLogin,
                onOpenSubscribe = onOpenSubscribe,
                onUnlockWithAd = onUnlockWithAd,
                onUnlockWithPoints = onUnlockWithPoints,
                onUnlockWithToken = onUnlockWithToken,
                showNativeActionAd = showStandaloneNativeAd,
            )
        }

        if (prompt.tags.isNotEmpty()) {
            item(key = "tags_${prompt.id}") {
                PromptTagsCard(
                    tags = prompt.tags,
                    onTagClick = onTagClick,
                )
            }
        }

        if (similarPrompts.isNotEmpty()) {
            item(key = "similar_header_${prompt.id}") {
                SimilarPromptsHeader(totalCount = similarPrompts.size)
            }

            items(
                items = displaySimilarFeed,
                key = { feedItem -> feedItem.stableKey },
            ) { feedItem ->
                when (feedItem) {
                    is SimilarPromptFeedItem.NativeAdItem -> {
                        SimilarPromptInlineAdCard(
                            adState = inlineAdStates[feedItem.slotKey],
                        )
                    }

                    is SimilarPromptFeedItem.PromptItem -> {
                        SimilarPromptCardVerticalV2(
                            prompt = feedItem.prompt,
                            onClick = { onSimilarPromptClick(feedItem.prompt) },
                        )
                    }
                }
            }

            if (isLoadingMore) {
                item(key = "similar_loading_${prompt.id}") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (hasMoreSimilar) {
                item(key = "similar_more_${prompt.id}") {
                    OutlinedButton(
                        onClick = onLoadMore,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    ) {
                        Text(stringResource(R.string.load_more))
                    }
                }
            }
        } else if (!isLoadingMore && !hasMoreSimilar) {
            item(key = "similar_empty_${prompt.id}") {
                SimilarPromptsEmptyState()
            }
        }
    }
}

@Composable
private fun PromptHeroHeader(
    prompt: V2PromptDto,
    onImageClick: () -> Unit,
) {
    val imageUrl = prompt.imageUrl ?: prompt.imageUrl2
    val fallbackRatio = 16f / 9f
    var imageRatio by remember(imageUrl) { mutableStateOf(fallbackRatio) }
    val configuration = LocalConfiguration.current
    val minHeight = 180.dp
    val maxHeight = (configuration.screenHeightDp.dp * 0.5f).coerceAtLeast(300.dp)

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val containerHeight = (maxWidth / imageRatio).coerceIn(minHeight, maxHeight)
        val headerModifier = Modifier
            .fillMaxWidth()
            .height(containerHeight)
            .clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
            .let { base ->
                if (!imageUrl.isNullOrBlank()) base.clickable { onImageClick() } else base
            }

        Box(modifier = headerModifier) {
            if (imageUrl.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.BrokenImage,
                        contentDescription = stringResource(R.string.image_error),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(72.dp),
                    )
                }
            } else {
                SubcomposeAsyncImage(
                    model = imageUrl,
                    contentDescription = prompt.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                ) {
                    when (val state = painter.state) {
                        is AsyncImagePainter.State.Loading -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                        }

                        is AsyncImagePainter.State.Error -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BrokenImage,
                                    contentDescription = stringResource(R.string.image_load_error),
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(64.dp),
                                )
                            }
                        }

                        is AsyncImagePainter.State.Success -> {
                            val drawable = state.result.drawable
                            val w = drawable.intrinsicWidth
                            val h = drawable.intrinsicHeight
                            if (w > 0 && h > 0) {
                                val nextRatio = (w.toFloat() / h.toFloat()).coerceIn(0.45f, 2.5f)
                                if (abs(nextRatio - imageRatio) > 0.01f) {
                                    imageRatio = nextRatio
                                }
                            }
                            SubcomposeAsyncImageContent()
                        }

                        else -> SubcomposeAsyncImageContent()
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                            startY = 110f,
                        ),
                    ),
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                prompt.category?.takeIf { it.isNotBlank() }?.let { category ->
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f),
                    ) {
                        Text(
                            text = category,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
                Text(
                    text = prompt.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun PromptMetaCard(
    prompt: V2PromptDto,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .animateContentSize(),
        ) {
            /*Text(
                text = prompt.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )*/

            PromptBadgesRow(prompt = prompt)

            if (!prompt.shortPrompt.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = prompt.shortPrompt,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 22.sp,
                )
            }
        }
    }
}

@Composable
private fun PromptBadgesRow(
    prompt: V2PromptDto,
    modifier: Modifier = Modifier,
) {
    val badges = buildList {
        if (prompt.isPopular) add(stringResource(R.string.popular))
        if (prompt.isFeatured) add(stringResource(R.string.featured))
        if (prompt.isLocked || prompt.tier.equals("PREMIUM", ignoreCase = true)) add(stringResource(R.string.premium))
    }

    if (badges.isEmpty()) return

    Spacer(modifier = Modifier.height(10.dp))
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        badges.forEach { label ->
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
    }
}

@Composable
private fun PromptBodyCard(
    prompt: V2PromptDto,
    isLoggedIn: Boolean,
    rewardedAdReady: Boolean,
    rewardedAdLoading: Boolean,
    unlockState: PromptDetailV2UiState,
    onOpenGemini: () -> Unit,
    onCopyPrompt: () -> Unit,
    onRequireLogin: () -> Unit,
    onOpenSubscribe: () -> Unit,
    onUnlockWithAd: () -> Unit,
    onUnlockWithPoints: () -> Unit,
    onUnlockWithToken: () -> Unit,
    showNativeActionAd: Boolean,
) {
    val localHaptic = LocalHapticFeedback.current
    var showGeminiDialog by remember { mutableStateOf(false) }
    var skipGeminiDialog by rememberSaveable { mutableStateOf(false) }
    var dontAskAgain by rememberSaveable { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (prompt.isLocked) Icons.Default.Lock else Icons.Default.AutoAwesome,
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (prompt.isLocked) stringResource(R.string.premium) else stringResource(R.string.full_ai_prompt),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (prompt.isLocked) {
                LockedPromptPreview(prompt.teaserText.orEmpty())
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Unlock this premium prompt using an ad, credits, or unlock token.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (!isLoggedIn) {
                    Button(
                        onClick = onRequireLogin,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.login))
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onUnlockWithAd,
                            enabled = !unlockState.isUnlockingWithAd,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.VideoLibrary, contentDescription = null)
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                when {
                                    unlockState.isUnlockingWithAd -> stringResource(R.string.pack_unlocking)
                                    rewardedAdLoading && !rewardedAdReady -> stringResource(R.string.rewards_loading_reward_ad)
                                    else -> stringResource(R.string.rewards_watch_ad_short)
                                },
                            )
                        }

                        Button(
                            onClick = onUnlockWithPoints,
                            enabled = !unlockState.isUnlockingWithPoints,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                if (unlockState.isUnlockingWithPoints) {
                                    stringResource(R.string.pack_unlocking)
                                } else {
                                    stringResource(R.string.pack_unlock_for_credits, prompt.premiumUnlockCostPoints)
                                },
                            )
                        }

                        OutlinedButton(
                            onClick = onUnlockWithToken,
                            enabled = !unlockState.isUnlockingWithToken,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.WorkspacePremium, contentDescription = null)
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                if (unlockState.isUnlockingWithToken) stringResource(R.string.loading) else stringResource(R.string.token_prompt_unlock),
                            )
                        }

                        OutlinedButton(
                            onClick = onOpenSubscribe,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Subscribe / Go Pro")
                        }
                    }
                }
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                ) {
                    Text(
                        text = prompt.fullPrompt.orEmpty(),
                        style = MaterialTheme.typography.bodyLarge,
                        lineHeight = 22.sp,
                        modifier = Modifier.padding(14.dp),
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        localHaptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (skipGeminiDialog) {
                            onOpenGemini()
                        } else {
                            showGeminiDialog = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(vertical = 16.dp, horizontal = 14.dp),
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.ai_prompt_action_open),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(R.string.ai_prompt_open_subtitle),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.86f),
                        )
                    }
                }

                if (showNativeActionAd) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                    ) {
                        NativeAdSection(
                            placementKey = AdsManager.KEY_DETAIL_NATIVE,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 10.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                )

                Button(
                    onClick = onCopyPrompt,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(vertical = 14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 1.dp,
                        pressedElevation = 0.dp,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.ai_prompt_action_copy_prompt))
                }
            }
        }
    }

    if (showGeminiDialog) {
        AlertDialog(
            onDismissRequest = {
                showGeminiDialog = false
                dontAskAgain = false
            },
            title = { Text(stringResource(R.string.ai_prompt_dialog_gemini_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.ai_prompt_dialog_gemini_message))
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { dontAskAgain = !dontAskAgain },
                    ) {
                        Checkbox(
                            checked = dontAskAgain,
                            onCheckedChange = { checked -> dontAskAgain = checked },
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.ai_prompt_dialog_dont_ask_again))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showGeminiDialog = false
                        if (dontAskAgain) {
                            skipGeminiDialog = true
                        }
                        onOpenGemini()
                        dontAskAgain = false
                    },
                ) {
                    Text(stringResource(R.string.ai_prompt_action_continue))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showGeminiDialog = false
                        dontAskAgain = false
                    },
                ) {
                    Text(stringResource(R.string.ai_prompt_action_cancel))
                }
            },
        )
    }
}

@Composable
private fun SimilarPromptsHeader(totalCount: Int) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.similar_prompts),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.weight(1f))
            AssistChip(
                onClick = {},
                enabled = false,
                label = { Text(formatCompactNumber(totalCount)) },
                colors = AssistChipDefaults.assistChipColors(
                    disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    disabledLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            )
        }
    }
}

@Composable
private fun SimilarPromptsEmptyState() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.no_prompts_available),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.check_back_later_for_new_prompts),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PromptTagsCard(
    tags: List<String>,
    onTagClick: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringResource(R.string.tags_with_emoji),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(),
            ) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (!expanded) Modifier.heightIn(max = 120.dp) else Modifier),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    tags.forEach { tag ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                            modifier = Modifier.clickable { onTagClick(tag) },
                        ) {
                            Text(
                                text = "#$tag",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }

                if (!expanded && tags.size > 8) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                                    ),
                                ),
                            ),
                    )
                }
            }

            if (tags.size > 8) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (expanded) stringResource(R.string.show_less) else stringResource(R.string.show_more),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun SimilarPromptInlineAdCard(
    adState: NativeAdUiState?,
) {
    when (adState) {
        is NativeAdUiState.Loaded -> {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                InlineNativeAdCard(
                    nativeAd = adState.ad,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        NativeAdUiState.Loading -> {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                InlineNativeAdCard(
                    nativeAd = null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        NativeAdUiState.Failed,
        NativeAdUiState.Disabled,
        null -> Unit
    }
}

@Composable
private fun LockedPromptPreview(teaser: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(20.dp),
            )
            .padding(16.dp),
    ) {
        Text(
            text = teaser.ifBlank { "Unlock to view the full prompt." }, // TODO string key: prompt_unlock_preview
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 5,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AdLoadingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                CircularProgressIndicator()
                Text(text = stringResource(R.string.loading_ad), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun TopBarActionCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = LocalContentColor.current,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
        tonalElevation = 2.dp,
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
            )
        }
    }
}

@Composable
private fun StatsRowV2(
    promptId: String,
    category: String?,
    likes: Int,
    views: Int,
    copies: Int,
    favorites: Int,
    isLiked: Boolean,
    isBookmarked: Boolean,
    onLikeClick: (String) -> Unit,
    onBookmarkClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!category.isNullOrBlank()) {
                CategoryStatV2(category = category)
            }
            ViewsStatV2(views = views)
            CopiesStatV2(copies = copies)
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatPillV2(
                icon = if (isLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                value = formatCompactNumber(likes),
                tint = if (isLiked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = { onLikeClick(promptId) },
            )

            StatPillV2(
                icon = if (isBookmarked) Icons.Default.BookmarkAdded else Icons.Default.BookmarkBorder,
                value = formatCompactNumber(favorites),
                tint = if (isBookmarked) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = { onBookmarkClick(promptId) },
            )
        }
    }
}

@Composable
private fun CopiesStatV2(
    copies: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.ContentCopy,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = formatCompactNumber(copies),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun CategoryStatV2(
    category: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Label,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = category,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun ViewsStatV2(
    views: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Visibility,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = formatCompactNumber(views),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun StatPillV2(
    icon: ImageVector,
    value: String,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        color = tint.copy(alpha = 0.08f),
        shape = RoundedCornerShape(50),
        modifier = modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = tint,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                color = tint,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

private sealed interface SimilarPromptFeedItem {
    val stableKey: String

    data class PromptItem(
        val prompt: V2PromptDto,
    ) : SimilarPromptFeedItem {
        override val stableKey: String = "prompt_${prompt.id}"
    }

    data class NativeAdItem(
        val slotKey: String,
    ) : SimilarPromptFeedItem {
        override val stableKey: String = "ad_$slotKey"
    }
}

private const val SIMILAR_INLINE_AD_MIN_ITEMS = 8
private const val SIMILAR_INLINE_AD_MAX_COUNT = 2
private const val SIMILAR_INLINE_AD_FIRST_MIN_GAP = 4
private const val SIMILAR_INLINE_AD_FIRST_MAX_GAP = 6
private const val SIMILAR_INLINE_AD_NEXT_MIN_GAP = 6
private const val SIMILAR_INLINE_AD_NEXT_MAX_GAP = 8
private const val SIMILAR_INLINE_AD_MIN_TRAILING_ITEMS = 2
private const val SIMILAR_ITEMS_PER_AD_TARGET = 8

private fun buildSimilarPromptFeed(
    prompts: List<V2PromptDto>,
    seed: Int,
    includeInlineAds: Boolean,
): List<SimilarPromptFeedItem> {
    if (prompts.isEmpty()) return emptyList()
    if (!includeInlineAds || prompts.size < SIMILAR_INLINE_AD_MIN_ITEMS) {
        return prompts.map { prompt -> SimilarPromptFeedItem.PromptItem(prompt) }
    }

    val anchors = computeSimilarInlineAdAnchors(
        itemCount = prompts.size,
        seed = seed,
    )
    if (anchors.isEmpty()) {
        return prompts.map { prompt -> SimilarPromptFeedItem.PromptItem(prompt) }
    }

    val stableSeed = seed and Int.MAX_VALUE
    val anchorSet = anchors.toSet()
    return buildList {
        prompts.forEachIndexed { index, prompt ->
            add(SimilarPromptFeedItem.PromptItem(prompt))
            val renderedPromptCount = index + 1
            if (renderedPromptCount in anchorSet) {
                add(
                    SimilarPromptFeedItem.NativeAdItem(
                        slotKey = "similar_${stableSeed}_${renderedPromptCount}",
                    ),
                )
            }
        }
    }
}

private fun computeSimilarInlineAdAnchors(
    itemCount: Int,
    seed: Int,
): List<Int> {
    if (itemCount < SIMILAR_INLINE_AD_MIN_ITEMS) return emptyList()

    val maxAds = (itemCount / SIMILAR_ITEMS_PER_AD_TARGET)
        .coerceAtLeast(1)
        .coerceAtMost(SIMILAR_INLINE_AD_MAX_COUNT)
    if (maxAds <= 0) return emptyList()

    val random = Random(seed)
    var nextAnchor = random.nextInt(
        from = SIMILAR_INLINE_AD_FIRST_MIN_GAP,
        until = SIMILAR_INLINE_AD_FIRST_MAX_GAP + 1,
    )

    val anchors = mutableListOf<Int>()
    while (
        anchors.size < maxAds &&
        nextAnchor <= itemCount - SIMILAR_INLINE_AD_MIN_TRAILING_ITEMS
    ) {
        anchors += nextAnchor
        nextAnchor += random.nextInt(
            from = SIMILAR_INLINE_AD_NEXT_MIN_GAP,
            until = SIMILAR_INLINE_AD_NEXT_MAX_GAP + 1,
        )
    }
    return anchors
}

@Composable
private fun SimilarPromptCardVerticalV2(
    prompt: V2PromptDto,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(),
        ) {
            Box {
                SubcomposeAsyncImage(
                    model = prompt.imageUrl ?: prompt.imageUrl2,
                    contentDescription = prompt.title,
                    modifier = Modifier
                        .width(132.dp)
                        .height(132.dp),
                    contentScale = ContentScale.Crop,
                ) {
                    when (painter.state) {
                        is AsyncImagePainter.State.Loading -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(22.dp))
                            }
                        }

                        is AsyncImagePainter.State.Error -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.BrokenImage,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }

                        else -> SubcomposeAsyncImageContent()
                    }
                }

                if (prompt.isLocked) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp),
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    ) {
                        Text(
                            text = stringResource(R.string.premium),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .padding(12.dp)
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = prompt.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                if (!prompt.shortPrompt.isNullOrBlank()) {
                    Text(
                        text = if (prompt.isLocked) prompt.teaserText.orEmpty() else prompt.shortPrompt.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = formatCompactNumber(prompt.views),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Icon(
                        imageVector = Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = formatCompactNumber(prompt.likes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

private fun formatCompactNumber(number: Int): String {
    return when {
        number >= 1_000_000 -> {
            val millions = number / 1_000_000f
            val floored = kotlin.math.floor(millions * 10) / 10
            if (floored % 1 == 0f) "${floored.toInt()}M" else "${floored}M"
        }

        number >= 10_000 -> {
            val thousands = number / 1_000f
            val floored = kotlin.math.floor(thousands * 10) / 10
            if (floored % 1 == 0f) "${floored.toInt()}K" else "${floored}K"
        }

        else -> number.toString()
    }
}
