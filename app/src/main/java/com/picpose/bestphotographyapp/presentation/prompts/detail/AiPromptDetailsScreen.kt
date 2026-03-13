/**
 * ---
 * File: AiPromptDetailsScreen.kt
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

package com.picpose.bestphotographyapp.presentation.prompts.detail

import android.app.Activity
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.picpose.bestphotographyapp.data.service.ads.AdManager
import com.picpose.bestphotographyapp.data.remote.dto.AIPrompt
import com.picpose.bestphotographyapp.R
import com.picpose.bestphotographyapp.components.ads.AdsManager
import com.picpose.bestphotographyapp.utils.setText
import com.picpose.bestphotographyapp.components.ads.NativeAdSection
import kotlinx.coroutines.launch

/**
 * AiPromptDetailsScreen - Enhanced detail screen with ad integration
 * 
 * Features:
 * - Displays prompt details with image, title, and full prompt
 * - Shows native ad between content and similar prompts
 * - Vertical list of similar prompts with load more
 * - Interstitial ads on similar prompt click (every 2-3 clicks)
 * - Smooth animations on prompt reload
 * - Scroll reset to top on navigation
 * - Fallback to test ad IDs when server IDs missing
 */
@Suppress("unused", "UNUSED_VALUE")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AiPromptDetailsScreen( //Currently Not Used AnyWhere
    promptId: String,
    viewModel: AiPromptDetailsViewModel = viewModel(),
    onBack: () -> Unit,
    onPromptClick: (String) -> Unit,
    onTagClick: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity
    val clipboard = LocalClipboard.current
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    
    // Track current prompt ID for animations
    var currentPromptId by remember { mutableStateOf(promptId) }
    val adManager = remember { AdManager.getInstance() }
    
    // Dialog state
    var showImageDialog by remember { mutableStateOf(false) }
    
    // Load prompt on init or when promptId changes
    LaunchedEffect(promptId) {
        if (promptId != currentPromptId) {
            // Reset scroll to top immediately when prompt changes
            listState.scrollToItem(0)
            // Reset for new prompt
            viewModel.resetForNewPrompt()
            currentPromptId = promptId
        }
        viewModel.loadPromptById(promptId)
    }
    
    // Preload ads on init
    LaunchedEffect(Unit) {
        activity?.let { viewModel.preloadAds(it) }
    }
    
    // Show full screen image dialog
    if (showImageDialog && uiState.currentPrompt?.imageUrl != null) {
        FullScreenImageDialog(
            imageUrl = uiState.currentPrompt!!.imageUrl!!,
            onDismiss = { showImageDialog = false }
        )
    }
    
    // Show error as snackbar
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
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
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    uiState.currentPrompt?.let { prompt ->
                        IconButton(
                            onClick = {
                                    coroutineScope.launch {
                                        clipboard.setText(prompt.fullPrompt ?: "", label = "prompt")
                                    }
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(context.getString(R.string.prompt_copied_for_sharing))
                                }
                            }
                        ) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.share))
                        }
                        
                        IconButton(
                            onClick = {
                                uiState.currentPrompt?.let { prompt ->
                                    viewModel.toggleFavorite(prompt)
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(
                                            if (prompt.isFavouriteBookmarked) {
                                                context.getString(R.string.removed_from_favorites)
                                            } else {
                                                context.getString(R.string.added_to_favorites)
                                            }
                                        )
                                    }
                                }
                            }
                        ) {
                            Icon(
                                if (prompt.isFavouriteBookmarked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = stringResource(R.string.favorite),
                                tint = if (prompt.isFavouriteBookmarked)
                                    MaterialTheme.colorScheme.error
                                else
                                    LocalContentColor.current
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Animated content with crossfade
            AnimatedContent(
                targetState = currentPromptId,
                transitionSpec = {
                    fadeIn(animationSpec = tween(500)).togetherWith(fadeOut(animationSpec = tween(300)))
                },
                label = "prompt_content"
            ) { targetPromptId ->
                when {
                    uiState.isLoading -> {
                        LoadingState()
                    }
                    
                    uiState.currentPrompt == null -> {
                        ErrorState(onRetry = { viewModel.loadPromptById(targetPromptId) })
                    }
                    
                    else -> {
                        PromptContent(
                            prompt = uiState.currentPrompt!!,
                            similarPrompts = uiState.similarPrompts,
                            isLoadingMore = uiState.isLoadingMore,
                            nativePlacementKey = AdsManager.KEY_DETAIL_NATIVE,
                            listState = listState,
                            onImageClick = { showImageDialog = true },
                            onCopyPrompt = {
                                    coroutineScope.launch {
                                        clipboard.setText(uiState.currentPrompt?.fullPrompt ?: "", label = "prompt")
                                    }
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(context.getString(R.string.prompt_copied_to_clipboard))
                                }
                            },
                            onTagClick = onTagClick,
                            onSimilarPromptClick = { clickedPrompt ->
                                coroutineScope.launch {
                                    // Reset scroll to top immediately
                                    listState.scrollToItem(0)
                                    
                                    // Increment click counter
                                    viewModel.onSimilarPromptClicked()
                                    
                                    // Check if should show interstitial
                                    if (viewModel.shouldShowInterstitial() && activity != null) {
                                        // Show loading
                                        viewModel.setShowAdLoader(true)
                                        
                                        // Show ad and wait for dismissal
                                        adManager.showInterstitialAndWait(activity)
                                        
                                        // Hide loading
                                        viewModel.setShowAdLoader(false)
                                        
                                        // Navigate after ad (or immediately if ad failed)
                                        onPromptClick(clickedPrompt.id)
                                    } else {
                                        // No ad needed, navigate immediately
                                        onPromptClick(clickedPrompt.id)
                                    }
                                }
                            },
                            onLoadMore = {
                                viewModel.loadMoreSimilarPrompts()
                            }
                        )
                    }
                }
            }
            
            // Ad loading overlay
            if (uiState.showAdLoader) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(stringResource(R.string.loading_ad), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator()
            Text(stringResource(R.string.loading_prompt), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ErrorState(onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Default.Error,
                contentDescription = stringResource(R.string.error),
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                stringResource(R.string.prompt_not_found),
                style = MaterialTheme.typography.headlineSmall
            )
            Button(onClick = onRetry) {
                Text(stringResource(R.string.retry))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PromptContent(
    prompt: AIPrompt,
    similarPrompts: List<AIPrompt>,
    isLoadingMore: Boolean,
    nativePlacementKey: String,
    listState: LazyListState,
    onImageClick: () -> Unit,
    onCopyPrompt: () -> Unit,
    onTagClick: (String) -> Unit,
    onSimilarPromptClick: (AIPrompt) -> Unit,
    onLoadMore: () -> Unit
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // Header Image
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clickable(onClick = onImageClick)
            ) {
                SubcomposeAsyncImage(
                    model = prompt.imageUrl,
                    contentDescription = prompt.title,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)),
                    contentScale = ContentScale.Crop
                ) {
                    when (painter.state) {
                        is AsyncImagePainter.State.Loading -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                        is AsyncImagePainter.State.Error -> {
                            Icon(
                                Icons.Default.BrokenImage,
                                contentDescription = stringResource(R.string.image_load_error),
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(64.dp)
                            )
                        }
                        else -> SubcomposeAsyncImageContent()
                    }
                }
                
                // Gradient overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.4f)),
                                startY = 100f
                            )
                        )
                )
            }
        }
        
        // Prompt Details Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = prompt.title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        StatChip(
                            icon = Icons.Default.FavoriteBorder,
                            text = "${prompt.likes} likes",
                            color = MaterialTheme.colorScheme.error
                        )
                        
                        prompt.tags.takeIf { it.isNotEmpty() }?.let { tags ->
                            StatChip(
                                icon = Icons.AutoMirrored.Filled.Label,
                                text = "${tags.size} tags",
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = prompt.shortPrompt ?: "",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 24.sp
                    )
                }
            }
        }
        
        // Full Prompt Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.full_ai_prompt),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Surface(
                        color = MaterialTheme.colorScheme.background,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )
                    ) {
                        Text(
                            text = prompt.fullPrompt ?: "",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 22.sp
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = onCopyPrompt,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(vertical = 16.dp)
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.copy_full_prompt),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
        
        // Native Ad Section
        if (AdsManager.canShowAds()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    NativeAdSection(
                        placementKey = nativePlacementKey,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        
        // Tags Section
        prompt.tags.takeIf { it.isNotEmpty() }?.let { tags ->
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = stringResource(R.string.tags_with_emoji),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            tags.forEach { tag ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(20.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                    ),
                                    modifier = Modifier.clickable { onTagClick(tag) }
                                ) {
                                    Text(
                                        text = "#$tag",
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Similar Prompts Section (Vertical List)
        if (similarPrompts.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.similar_prompts),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                )
            }
            
            items(similarPrompts) { similarPrompt ->
                SimilarPromptCardVertical(
                    prompt = similarPrompt,
                    onClick = { onSimilarPromptClick(similarPrompt) }
                )
            }
            
            // Load More Button
            if (isLoadingMore) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (similarPrompts.size >= 5) {
                item {
                    OutlinedButton(
                        onClick = onLoadMore,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(stringResource(R.string.load_more))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatChip(
    icon: ImageVector,
    text: String,
    color: Color
) {
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = color
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = color,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun SimilarPromptCardVertical(
    prompt: AIPrompt,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Image
            SubcomposeAsyncImage(
                model = prompt.imageUrl,
                contentDescription = prompt.title,
                modifier = Modifier
                    .width(120.dp)
                    .height(120.dp),
                contentScale = ContentScale.Crop
            ) {
                if (painter.state is AsyncImagePainter.State.Loading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                } else {
                    SubcomposeAsyncImageContent()
                }
            }
            
            // Content
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .weight(1f)
            ) {
                Text(
                    text = prompt.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = prompt.shortPrompt ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${prompt.likes}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
