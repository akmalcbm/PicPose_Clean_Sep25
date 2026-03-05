package com.picpose.bestphotographyapp.ui.prompts

import android.app.Activity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.picpose.bestphotographyapp.R
import com.picpose.bestphotographyapp.core.utils.setText
import com.picpose.bestphotographyapp.data.ads.RewardedAdManager
import com.picpose.bestphotographyapp.data.admob.AdManager
import com.picpose.bestphotographyapp.data.models.v2.V2PromptDto
import com.picpose.bestphotographyapp.presentation.ads.AdsManager
import com.picpose.bestphotographyapp.presentation.screens.openGemini
import com.picpose.bestphotographyapp.ui.ads.NativeAdSection
import com.picpose.bestphotographyapp.ui.details.FullScreenImageDialog
import kotlinx.coroutines.launch

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
            TopAppBar(
                title = { Text(stringResource(R.string.ai_prompt_details_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    uiState.prompt?.let { prompt ->
                        IconButton(
                            onClick = {
                                val fullPrompt = prompt.fullPrompt.orEmpty()
                                if (fullPrompt.isBlank()) return@IconButton
                                coroutineScope.launch {
                                    clipboard.setText(fullPrompt, label = "prompt")
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    snackbarHostState.showSnackbar(context.getString(R.string.prompt_copied_to_clipboard))
                                }
                            },
                        ) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.share))
                        }

                        IconButton(
                            onClick = { viewModel.toggleFavorite(prompt.id) },
                        ) {
                            Icon(
                                imageVector = if (uiState.isFavoriteLocal) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = stringResource(R.string.favorite),
                                tint = if (uiState.isFavoriteLocal) MaterialTheme.colorScheme.error else LocalContentColor.current,
                            )
                        }
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
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator()
            Text(text = stringResource(R.string.loading_prompt), style = MaterialTheme.typography.bodyMedium)
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
    listState: LazyListState,
    onImageClick: () -> Unit,
    onOpenGemini: () -> Unit,
    onCopyPrompt: () -> Unit,
    onRequireLogin: () -> Unit,
    onOpenSubscribe: () -> Unit,
    onUnlockWithAd: () -> Unit,
    onUnlockWithPoints: () -> Unit,
    onUnlockWithToken: () -> Unit,
    onTagClick: (String) -> Unit,
    onSimilarPromptClick: (V2PromptDto) -> Unit,
    onLoadMore: () -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "header_${prompt.id}") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clickable(onClick = onImageClick),
            ) {
                SubcomposeAsyncImage(
                    model = prompt.imageUrl ?: prompt.imageUrl2,
                    contentDescription = prompt.title,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)),
                    contentScale = ContentScale.Crop,
                ) {
                    when (painter.state) {
                        is AsyncImagePainter.State.Loading -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }

                        is AsyncImagePainter.State.Error -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.BrokenImage,
                                    contentDescription = stringResource(R.string.image_load_error),
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(58.dp),
                                )
                            }
                        }

                        else -> SubcomposeAsyncImageContent()
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f)),
                                startY = 120f,
                            ),
                        ),
                )
            }
        }

        item(key = "prompt_meta_${prompt.id}") {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = prompt.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StatChip(
                            icon = Icons.Default.Visibility,
                            text = "${formatCompactNumber(prompt.views)} ${stringResource(R.string.views)}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        StatChip(
                            icon = Icons.Default.FavoriteBorder,
                            text = "${formatCompactNumber(prompt.likes)} ${stringResource(R.string.likes)}",
                            color = MaterialTheme.colorScheme.error,
                        )
                        if (prompt.tags.isNotEmpty()) {
                            StatChip(
                                icon = Icons.AutoMirrored.Filled.Label,
                                text = "${prompt.tags.size} tags",
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    if (!prompt.shortPrompt.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = prompt.shortPrompt,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 24.sp,
                        )
                    }
                }
            }
        }

        item(key = "full_prompt_${prompt.id}") {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
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
                            text = if (prompt.isLocked) "Premium prompt" else stringResource(R.string.full_ai_prompt), // TODO string key: premium_prompt
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (prompt.isLocked) {
                        LockedPromptPreview(prompt.teaserText.orEmpty())
                        Spacer(modifier = Modifier.height(16.dp))

                        if (!isLoggedIn) {
                            Text(
                                text = "Login to unlock premium prompts with ads, credits, or tokens.", // TODO string key: prompt_unlock_login_desc
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(onClick = onRequireLogin, modifier = Modifier.fillMaxWidth()) {
                                Text("Login to Unlock") // TODO string key: prompt_login_unlock
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
                                            unlockState.isUnlockingWithAd -> "Unlocking..." // TODO string key: unlocking
                                            rewardedAdLoading && !rewardedAdReady -> stringResource(R.string.rewards_loading_reward_ad)
                                            else -> "Watch Ad" // TODO string key: watch_ad
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
                                            "Unlocking..." // TODO string key: unlocking
                                        } else {
                                            "Unlock with ${prompt.premiumUnlockCostPoints} credits" // TODO string key: unlock_with_points
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
                                        if (unlockState.isUnlockingWithToken) "Checking token..." else "Use Unlock Token", // TODO string keys
                                    )
                                }

                                OutlinedButton(
                                    onClick = onOpenSubscribe,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Subscribe / Go Pro") // TODO string key: subscribe_go_pro
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
                            onClick = onOpenGemini,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(vertical = 14.dp),
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.ai_prompt_action_open),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = onCopyPrompt,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.copy_full_prompt))
                        }
                    }
                }
            }
        }

        if (AdsManager.canShowAds()) {
            item(key = "native_ad_${prompt.id}") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                ) {
                    NativeAdSection(
                        placementKey = AdsManager.KEY_DETAIL_NATIVE,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        if (prompt.tags.isNotEmpty()) {
            item(key = "tags_${prompt.id}") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.tags_with_emoji),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            prompt.tags.forEach { tag ->
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
                    }
                }
            }
        }

        if (similarPrompts.isNotEmpty()) {
            item(key = "similar_title_${prompt.id}") {
                Text(
                    text = stringResource(R.string.similar_prompts),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }

            items(similarPrompts, key = { it.id }) { similarPrompt ->
                SimilarPromptCardVerticalV2(
                    prompt = similarPrompt,
                    onClick = { onSimilarPromptClick(similarPrompt) },
                )
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
        }
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
private fun StatChip(
    icon: ImageVector,
    text: String,
    color: Color,
) {
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = color,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = color,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun SimilarPromptCardVerticalV2(
    prompt: V2PromptDto,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            SubcomposeAsyncImage(
                model = prompt.imageUrl ?: prompt.imageUrl2,
                contentDescription = prompt.title,
                modifier = Modifier
                    .width(120.dp)
                    .height(120.dp),
                contentScale = ContentScale.Crop,
            ) {
                if (painter.state is AsyncImagePainter.State.Loading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                } else {
                    SubcomposeAsyncImageContent()
                }
            }

            Column(
                modifier = Modifier
                    .padding(14.dp)
                    .weight(1f),
            ) {
                Text(
                    text = prompt.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (prompt.isLocked) prompt.teaserText.orEmpty() else prompt.shortPrompt.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
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
                        modifier = Modifier.size(16.dp),
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
