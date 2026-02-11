package com.picpose.bestphotographyapp.presentation.screens

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.core.net.toUri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.ThumbUp
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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.google.android.gms.ads.nativead.NativeAd
import com.picpose.bestphotographyapp.R
import com.picpose.bestphotographyapp.presentation.ads.AdsLog
import com.picpose.bestphotographyapp.presentation.ads.AdsConfigState
import com.picpose.bestphotographyapp.data.models.AIPrompt
import com.picpose.bestphotographyapp.presentation.ads.AdsManager
import com.picpose.bestphotographyapp.presentation.ads.InterstitialAdController
import com.picpose.bestphotographyapp.presentation.ads.LargeNativeAdCard
import com.picpose.bestphotographyapp.presentation.ads.NativeAdController
import com.picpose.bestphotographyapp.presentation.ads.NativeAdUiState
import com.picpose.bestphotographyapp.presentation.components.EdgeToEdgeScaffold
import com.picpose.bestphotographyapp.presentation.viewmodels.AIPromptViewModel
import com.picpose.bestphotographyapp.core.utils.ShareUtils
import com.picpose.bestphotographyapp.core.utils.setText
import com.picpose.bestphotographyapp.core.utils.displayLikes
import com.picpose.bestphotographyapp.core.utils.displayViews
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG_DETAIL = "PromptDetail"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun AIPromptDetailScreen(
    promptId: String,
    aiPromptViewModel: AIPromptViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onPromptClick: (String) -> Unit,
    onTagClick: (String) -> Unit
) {
    val uiState by aiPromptViewModel.uiState.collectAsState()

    val localEngagementStates by aiPromptViewModel.localEngagementStates.collectAsState()

    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Local resolved prompt reference
    var localPrompt by remember { mutableStateOf<AIPrompt?>(null) }
    var hasRequestedFromApi by remember { mutableStateOf(false) }

    // list scroll state
    val listState: LazyListState = rememberLazyListState()

    // Transition overlay
    var isTransitionLoading by rememberSaveable { mutableStateOf(false) }

    // Interstitial controller
    val interstitialController = remember {
        InterstitialAdController(placementKey = AdsManager.KEY_DETAIL_INTERSTITIAL)
    }
    val similarClickCount by aiPromptViewModel.similarPromptsClickCount.collectAsState()
    val adsConfigState by AdsManager.configState.collectAsState()

    val skipGeminiDialog by aiPromptViewModel.skipGeminiDialog.collectAsState()

    // Preload interstitial once
    LaunchedEffect(adsConfigState) {
        if (adsConfigState is AdsConfigState.Loading) {
            AdsLog.d(
                AdsLog.TAG_UI,
                "[AdsUI] screen=AIPromptDetailScreen placement=${AdsManager.KEY_DETAIL_INTERSTITIAL} action=wait reason=CONFIG_LOADING"
            )
            return@LaunchedEffect
        }
        val canShowAds = AdsManager.canShowAds()
        AdsLog.i(
            AdsLog.TAG_UI,
            "[AdsUI] screen=AIPromptDetailScreen placement=${AdsManager.KEY_DETAIL_INTERSTITIAL} action=preload_request canShowAds=$canShowAds"
        )
        if (!canShowAds) {
            AdsLog.i(
                AdsLog.TAG_UI,
                "[AdsUI] screen=AIPromptDetailScreen placement=${AdsManager.KEY_DETAIL_INTERSTITIAL} action=skip reason=global_gate"
            )
            return@LaunchedEffect
        }
        interstitialController.preload(context)
    }

    // Native Ad (loaded-only rendering; no reserved space on fail/disabled)
    var nativeAdState by remember { mutableStateOf<NativeAdUiState>(NativeAdUiState.Disabled) }
    val nativePlacementKey = remember(adsConfigState) {
        if (adsConfigState is AdsConfigState.Ready &&
            AdsManager.getPlacement(AdsManager.KEY_NATIVE_2) != null
        ) {
            AdsManager.KEY_NATIVE_2
        } else {
            AdsManager.KEY_NATIVE_AD
        }
    }
    val nativeAdController = remember(nativePlacementKey) {
        NativeAdController(placementKey = nativePlacementKey)
    }
    LaunchedEffect(adsConfigState, promptId, nativePlacementKey) {
        when (adsConfigState) {
            is AdsConfigState.Loading -> {
                AdsLog.d(
                    AdsLog.TAG_UI,
                    "[AdsUI] screen=AIPromptDetailScreen placement=$nativePlacementKey action=wait reason=CONFIG_LOADING"
                )
            }

            is AdsConfigState.Error -> {
                nativeAdState = NativeAdUiState.Failed
                AdsLog.w(
                    AdsLog.TAG_UI,
                    "[AdsUI] screen=AIPromptDetailScreen placement=$nativePlacementKey action=skip reason=CONFIG_ERROR"
                )
            }

            is AdsConfigState.Ready -> {
                val canShowAds = AdsManager.canShowAds()
                AdsLog.i(
                    AdsLog.TAG_UI,
                    "[AdsUI] screen=AIPromptDetailScreen placement=$nativePlacementKey action=request canShowAds=$canShowAds promptId=$promptId"
                )
                if (!canShowAds) {
                    nativeAdState = NativeAdUiState.Disabled
                    AdsLog.i(
                        AdsLog.TAG_UI,
                        "[AdsUI] screen=AIPromptDetailScreen placement=$nativePlacementKey action=skip reason=global_gate"
                    )
                    return@LaunchedEffect
                }

                nativeAdState = NativeAdUiState.Loading
                nativeAdController.load(
                    context = context,
                    callbacks = object : NativeAdController.Callbacks {
                        override fun onLoaded(ad: NativeAd) {
                            AdsLog.i(
                                AdsLog.TAG_UI,
                                "[AdsUI] screen=AIPromptDetailScreen placement=$nativePlacementKey action=loaded promptId=$promptId"
                            )
                            nativeAdState = NativeAdUiState.Loaded(ad)
                        }

                        override fun onFailed(error: com.google.android.gms.ads.LoadAdError) {
                            AdsLog.w(
                                AdsLog.TAG_UI,
                                "[AdsUI] screen=AIPromptDetailScreen placement=$nativePlacementKey action=failed domain=${error.domain} code=${error.code} message=${error.message}"
                            )
                            nativeAdState = NativeAdUiState.Failed
                        }

                        override fun onUnavailable(reason: String) {
                            AdsLog.i(
                                AdsLog.TAG_UI,
                                "[AdsUI] screen=AIPromptDetailScreen placement=$nativePlacementKey action=unavailable reason=$reason"
                            )
                            nativeAdState = if (reason == "ADS_DISABLED" || reason == "FREQUENCY_BLOCK" || reason == "NO_UNIT") {
                                NativeAdUiState.Disabled
                            } else {
                                NativeAdUiState.Failed
                            }
                        }
                    }
                )
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            AdsLog.d(
                AdsLog.TAG_UI,
                "[AdsUI] screen=AIPromptDetailScreen action=dispose_ads"
            )
            interstitialController.clear()
            nativeAdState = NativeAdUiState.Disabled
        }
    }
    DisposableEffect(nativeAdController) {
        onDispose {
            nativeAdController.clear()
        }
    }


    var viewTracked by rememberSaveable(promptId) { mutableStateOf(false) }

    if (!viewTracked) {
        LaunchedEffect(promptId) {
            delay(4000)
            aiPromptViewModel.registerView(promptId)
            viewTracked = true
            Log.d(TAG_DETAIL, "✅ View registered after 4 seconds for prompt: $promptId")
        }
    }


    var showImageDialog by remember { mutableStateOf(false) }

    // Resolve prompt from VM state
    val effectivePrompt: AIPrompt? = remember(promptId, uiState.allPrompts, uiState.selectedPrompt, localPrompt) {
        val idTrim = promptId.trim()
        localPrompt
            ?: uiState.selectedPrompt?.takeIf { it.id.trim() == idTrim }
            ?: uiState.allPrompts.find { it.id.trim() == idTrim }
    }

    // Smoothly return to top when a new prompt is loaded in-place.
    LaunchedEffect(effectivePrompt?.id) {
        if (effectivePrompt != null &&
            (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0)
        ) {
            listState.animateScrollToItem(0)
        }
    }

    // Keep localPrompt in sync / trigger fetch when not found
    LaunchedEffect(promptId, uiState.allPrompts, uiState.selectedPrompt) {
        val idTrim = promptId.trim()
        val merged = listOfNotNull(uiState.selectedPrompt) + uiState.allPrompts
        val match = merged.find { it.id.trim() == idTrim }

        Log.d(TAG_DETAIL, "Received promptId = $promptId")
        Log.d(TAG_DETAIL, "Total prompts available = ${uiState.allPrompts.size}")
        Log.d(TAG_DETAIL, "Matched prompt? = ${match != null}")

        if (match != null) {
            localPrompt = match
            aiPromptViewModel.loadPromptById(promptId)
        } else if (!hasRequestedFromApi) {
            hasRequestedFromApi = true
            Log.w(TAG_DETAIL, "⚠️ Not found in memory → Fetching from API")
            aiPromptViewModel.loadPromptById(promptId)
        }
    }

    // Stop transition overlay once loading settles
    LaunchedEffect(promptId, uiState.isLoading, effectivePrompt) {
        if (!uiState.isLoading && effectivePrompt != null) {
            delay(250)
            isTransitionLoading = false
        } else if (uiState.isLoading && effectivePrompt == null) {
            isTransitionLoading = true
        }
    }

    // Similar prompts auto-load when we have category & id
    LaunchedEffect(effectivePrompt?.id, effectivePrompt?.category) {
        val cat = effectivePrompt?.category
        val id = effectivePrompt?.id
        if (!cat.isNullOrBlank() && !id.isNullOrBlank()) {
            aiPromptViewModel.loadSimilarPrompts(cat, id)
        }
    }

    // Error → snackbar
    LaunchedEffect(uiState.error) {
        uiState.error?.let { msg ->
            coroutineScope.launch {
                snackbarHostState.showSnackbar(msg)
                aiPromptViewModel.clearError()
            }
        }
    }

    if (showImageDialog) {
        (effectivePrompt?.imageUrl ?: effectivePrompt?.imageUrl2)?.let {
            FullScreenImageDialog(imageUrl = it, onDismiss = { showImageDialog = false })
        }
    }

    EdgeToEdgeScaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ai_prompt_details_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {

                    val seaGreen = Color(0xFF009688)

                    TopBarActionCircleButton(
                        icon = Icons.Default.Share,
                        tint = seaGreen,
                        onClick = {
                            effectivePrompt?.let { prompt ->
                                coroutineScope.launch {
                                    ShareUtils.sharePrompt(
                                        context = context,
                                        promptText = prompt.fullPrompt ?: prompt.shortPrompt.orEmpty(),
                                        imageUrl = prompt.imageUrl ?: prompt.imageUrl2
                                    )
                                }
                            }
                        }
                    )


                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                )
            )
        },
        snackbarHostState = snackbarHostState
    ) { innerPadding ->

        when {
            // Initial / hard loading with no prompt yet → shimmer screen
            uiState.isLoading && effectivePrompt == null -> {
                DetailLoadingPlaceholder(innerPadding = innerPadding)
            }

            // explicit not found (after we tried API and not loading)
            !uiState.isLoading && effectivePrompt == null && hasRequestedFromApi -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.prompt_not_found),
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onBack) { Text(stringResource(R.string.go_back)) }
                    }
                }
            }

            // Normal content
            effectivePrompt != null -> {
                val promptData = effectivePrompt

                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentPadding = PaddingValues(
                            top = 0.dp,
                            bottom = 24.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 🖼 Image Header
                        item {
                            val headerImageUrl = promptData.imageUrl ?: promptData.imageUrl2
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                            ) {
                                AdaptivePromptHeaderImage(
                                    imageUrl = headerImageUrl,
                                    contentDescription = promptData.title,
                                    onClick = { showImageDialog = true },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(
                                            RoundedCornerShape(
                                                bottomStart = 16.dp,
                                                bottomEnd = 16.dp
                                            )
                                        )
                                )

                                // subtle gradient bottom overlay
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.Transparent,
                                                    Color.Black.copy(alpha = 0.35f)
                                                ),
                                                startY = 160f
                                            )
                                        )
                                )
                            }

                            val localEngagement = localEngagementStates[promptData.id]

                            // 🧮 Stats Row directly under image
                            StatsRow(
                                promptId = promptData.id,
                                category = promptData.category,
                                likes = promptData.displayLikes(localEngagement),
                                views = promptData.displayViews(localEngagement),
                                favorites = promptData.favorites,
                                isLiked = promptData.isLiked,
                                isBookmarked = promptData.isFavouriteBookmarked,
                                onLikeClick = { _ ->
                                    // Haptic feedback
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    // Call ViewModel
                                    aiPromptViewModel.onLikeClicked(promptData)
                                },
                                onBookmarkClick = { _ ->
                                    // Haptic feedback
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    // Call ViewModel
                                    aiPromptViewModel.onFavoriteClicked(promptData)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
                                    )
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                            )
                        }


                        // 🧾 Title + short description card
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp), //from outer most left and right padding
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = promptData.title,
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))

                                    if (!promptData.shortPrompt.isNullOrBlank()) {
                                        Text(
                                            text = promptData.shortPrompt,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            lineHeight = 22.sp
                                        )
                                    }
                                }
                            }
                        }

                        // ✨ Full Prompt section
                        item {
                            val ctx = LocalContext.current
                            val localHaptic = LocalHapticFeedback.current
                            var showGeminiDialog by remember { mutableStateOf(false) }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp), //from outer most left and right padding
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
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
                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                                        )
                                    ) {
                                        Text(
                                            text = promptData.fullPrompt ?: "",
                                            modifier = Modifier.padding(16.dp),
                                            style = MaterialTheme.typography.bodyMedium,
                                            lineHeight = 22.sp
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {

                                        // 🔥 PRIMARY — Open in Gemini
                                        Button(
                                            onClick = {
                                                // ✨ HAPTIC (Primary CTA)
                                                localHaptic.performHapticFeedback(
                                                    HapticFeedbackType.TextHandleMove
                                                )

                                                if (skipGeminiDialog) {
                                                    // 🚀 Direct open
                                                    openGemini(
                                                        context = ctx,
                                                        promptText = promptData.fullPrompt ?: "",
                                                    )

                                                    Toast.makeText(
                                                        ctx,
                                                        ctx.getString(R.string.prompt_copied_opening_gemini),
                                                        Toast.LENGTH_SHORT
                                                    ).show()

                                                    promptData.id.toIntOrNull()?.let {
                                                        aiPromptViewModel.incrementCopyCount(it)
                                                    }
                                                } else {
                                                    showGeminiDialog = true
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(14.dp),
                                            contentPadding = PaddingValues(vertical = 16.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AutoAwesome,
                                                contentDescription = stringResource(R.string.copy_prompts_open_gemini),
                                                modifier = Modifier.size(22.dp),
                                                tint = MaterialTheme.colorScheme.onPrimary
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = stringResource(R.string.copy_prompts_open_gemini),
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimary
                                                )
                                                Text(
                                                    text = stringResource(R.string.best_for_generating_results),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                                                )
                                            }
                                        }


                                        // Native Ad renders only when loaded; no placeholder/space on failure.
                                        if (nativeAdState is NativeAdUiState.Loaded) {
                                            val loaded = nativeAdState as NativeAdUiState.Loaded
                                            LargeNativeAdCard(
                                                nativeAd = loaded.ad,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }


                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 4.dp),
                                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                        )


                                        // ⚪ SECONDARY — Copy Prompt (Improved Contrast)
                                        Button(
                                            onClick = {
                                                val textToCopy = promptData.fullPrompt ?: ""
                                                if (textToCopy.isNotBlank()) {
                                                    coroutineScope.launch {
                                                        clipboard.setText(textToCopy, label = "prompt")
                                                    }
                                                    localHaptic.performHapticFeedback(HapticFeedbackType.LongPress)

                                                    Toast.makeText(
                                                        ctx,
                                                        ctx.getString(R.string.prompt_copied_to_clipboard),
                                                        Toast.LENGTH_SHORT
                                                    ).show()

                                                    promptData.id.toIntOrNull()?.let {
                                                        aiPromptViewModel.incrementCopyCount(it)
                                                    }
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(14.dp),
                                            contentPadding = PaddingValues(vertical = 14.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.surface, // 👈 soft filled
                                                contentColor = MaterialTheme.colorScheme.onSurface
                                            ),
                                            elevation = ButtonDefaults.buttonElevation(
                                                defaultElevation = 1.dp,
                                                pressedElevation = 0.dp
                                            )
                                        ) {
                                            Icon(
                                                Icons.Default.ContentCopy,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = stringResource(R.string.copy_prompt),
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }

                                    }

                                }
                            }

                            var dontAskAgain by rememberSaveable { mutableStateOf(false) }

                            if (showGeminiDialog) {
                                AlertDialog(
                                    onDismissRequest = {
                                        showGeminiDialog = false
                                        dontAskAgain = false
                                    },
                                    title = { Text(stringResource(R.string.continue_in_gemini)) },
                                    text = {
                                        Column {
                                            Text(
                                                stringResource(R.string.gemini_continue_message)
                                            )

                                            Spacer(modifier = Modifier.height(12.dp))

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { dontAskAgain = !dontAskAgain }
                                            ) {
                                                Checkbox(
                                                    checked = dontAskAgain,
                                                    onCheckedChange = { dontAskAgain = it }
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(stringResource(R.string.dont_ask_again))
                                            }
                                        }
                                    },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            showGeminiDialog = false

                                            if (dontAskAgain) {
                                                aiPromptViewModel.setSkipGeminiDialog(true) // 💾 DataStore
                                            }

                                            openGemini(
                                                context = ctx,
                                                promptText = promptData.fullPrompt ?: ""
                                            )

                                            Toast.makeText(
                                                ctx,
                                                ctx.getString(R.string.prompt_copied_opening_gemini),
                                                Toast.LENGTH_SHORT
                                            ).show()

                                            promptData.id.toIntOrNull()?.let {
                                                aiPromptViewModel.incrementCopyCount(it)
                                            }

                                            dontAskAgain = false
                                        }) {
                                            Text(stringResource(R.string.continue_label))
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = {
                                            showGeminiDialog = false
                                            dontAskAgain = false
                                        }) {
                                            Text(stringResource(R.string.cancel))
                                        }
                                    }
                                )
                            }

                        }



                        // 🏷 Tags (expandable)
                        promptData.tags.takeIf { it.isNotEmpty() }?.let { tags ->
                            item {
                                var expanded by remember { mutableStateOf(false) }

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = stringResource(R.string.tags_with_emoji),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )

                                        Spacer(modifier = Modifier.height(12.dp))

                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .animateContentSize()
                                        ) {
                                            FlowRow(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .then(
                                                        if (!expanded) Modifier.heightIn(max = 120.dp)
                                                        else Modifier
                                                    ),
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
                                                        modifier = Modifier.clickable {
                                                            onTagClick(tag)
                                                        }
                                                    ) {
                                                        Text(
                                                            text = "#$tag",
                                                            modifier = Modifier.padding(
                                                                horizontal = 12.dp,
                                                                vertical = 6.dp
                                                            ),
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontWeight = FontWeight.Medium
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
                                                                    MaterialTheme.colorScheme.surface.copy(
                                                                        alpha = 0.96f
                                                                    )
                                                                )
                                                            )
                                                        )
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
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = if (expanded) {
                                                        stringResource(R.string.show_less)
                                                    } else {
                                                        stringResource(R.string.show_more)
                                                    },
                                                    color = MaterialTheme.colorScheme.primary,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 🤝 Similar Prompts
                        if (uiState.similarPrompts.isNotEmpty()) {
                            item {
                                Column(modifier = Modifier.padding(top = 8.dp)) {
                                    Text(
                                        text = stringResource(R.string.similar_prompts),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(uiState.similarPrompts) { similarPrompt ->
                                            SimilarPromptCard(
                                                prompt = similarPrompt,
                                                onClick = {
                                                    val id = similarPrompt.id
                                                    isTransitionLoading = true

                                                    // Interstitial after 1 click (test condition)
                                                    if (similarClickCount >= 1) {
                                                        val activity = context as? Activity
                                                        if (activity != null) {
                                                            AdsLog.i(
                                                                AdsLog.TAG_UI,
                                                                "[AdsUI] screen=AIPromptDetailScreen placement=${AdsManager.KEY_DETAIL_INTERSTITIAL} action=show_request reason=similar_prompt_click clickCount=$similarClickCount"
                                                            )
                                                            interstitialController.show(
                                                                activity = activity,
                                                                onComplete = {
                                                                    onPromptClick(id)
                                                                }
                                                            )
                                                            interstitialController.preload(context)
                                                        } else {
                                                            AdsLog.w(
                                                                AdsLog.TAG_UI,
                                                                "[AdsUI] screen=AIPromptDetailScreen placement=${AdsManager.KEY_DETAIL_INTERSTITIAL} action=skip reason=activity_null"
                                                            )
                                                            onPromptClick(id)
                                                        }
                                                    } else {
                                                        AdsLog.d(
                                                            AdsLog.TAG_UI,
                                                            "[AdsUI] screen=AIPromptDetailScreen placement=${AdsManager.KEY_DETAIL_INTERSTITIAL} action=skip reason=click_threshold clickCount=$similarClickCount"
                                                        )
                                                        onPromptClick(id)
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 🌀 Transition overlay
                    AnimatedVisibility(
                        visible = isTransitionLoading,
                        enter = fadeIn(animationSpec = tween(200)),
                        exit = fadeOut(animationSpec = tween(200)),
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = stringResource(R.string.loading_prompt),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun LargeAdShimmerPlaceholder() {
    val shimmerTransition = rememberInfiniteTransition()

    val shimmerX by shimmerTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f, // keep large for full sweep
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1300,
                easing = LinearEasing
            )
        )
    )

    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(shimmerX - 300f, 0f),
        end = Offset(shimmerX, 300f)
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .height(220.dp),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(0.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(brush)
        )
    }
}

@Composable
private fun CategoryStat(
    category: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Label,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = category,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}



@Composable
private fun StatsRow(
    promptId: String,
    category: String?,
    likes: Int,
    views: Int,
    favorites: Int,
    isLiked: Boolean,
    isBookmarked: Boolean,
    onLikeClick: (String) -> Unit,
    onBookmarkClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {

        /* -------------------- LEFT : CATEGORY + VIEWS (INFO ONLY) -------------------- */
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (!category.isNullOrBlank()) {
                CategoryStat(category = category)
            }

            ViewsStat(views = views)
        }

        Spacer(modifier = Modifier.weight(1f))

        /* -------------------- RIGHT : ACTIONS -------------------- */
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            StatPill(
                icon = if (isLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                value = formatCompactNumber(likes),
                tint = if (isLiked)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = { onLikeClick(promptId) }
            )

            StatPill(
                icon = if (isBookmarked)
                    Icons.Default.BookmarkAdded
                else
                    Icons.Default.BookmarkBorder,
                value = formatCompactNumber(favorites),
                tint = if (isBookmarked)
                    MaterialTheme.colorScheme.tertiary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = { onBookmarkClick(promptId) }
            )
        }
    }
}

@Composable
private fun StatPill(
    icon: ImageVector,
    value: String,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Surface(
        color = tint.copy(alpha = 0.08f),
        shape = RoundedCornerShape(50),
        modifier = modifier.then(
            if (onClick != null)
                Modifier.clickable { onClick() }
            else
                Modifier
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = tint
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                color = tint,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ViewsStat(
    views: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Visibility,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = formatCompactNumber(views),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
    }
}

// Advanced latest-style number formatting
private fun formatCompactNumber(number: Int): String {
    return when {
        number >= 1_000_000 -> {
            val millions = number / 1_000_000f
            val floored = kotlin.math.floor(millions * 10) / 10
            if (floored % 1 == 0f)
                "${floored.toInt()}M"
            else
                "${floored}M"
        }

        number >= 10_000 -> {
            val thousands = number / 1_000f
            val floored = kotlin.math.floor(thousands * 10) / 10
            if (floored % 1 == 0f)
                "${floored.toInt()}K"
            else
                "${floored}K"
        }

        else -> number.toString()
    }
}


@Composable
fun TopBarActionCircleButton(
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,      // 👈 Perfect for TopAppBar
    iconSize: Dp = 24.dp
) {
    Surface(
        shape = CircleShape,
        color = tint.copy(alpha = 0.02f),
        modifier = modifier
            .size(size)
            .padding(end = 6.dp),
        onClick = onClick
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}


@Composable
private fun SimilarPromptCard(
    prompt: AIPrompt,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(220.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            SubcomposeAsyncImage(
                model = prompt.imageUrl,
                contentDescription = prompt.title,
                modifier = Modifier
                    .height(140.dp)
                    .fillMaxWidth(),
                contentScale = ContentScale.Crop
            ) {
                if (painter.state is AsyncImagePainter.State.Loading) {
                    Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    SubcomposeAsyncImageContent()
                }
            }
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = prompt.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = prompt.shortPrompt ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3
                )
            }
        }
    }
}


@Composable
private fun AdaptivePromptHeaderImage(
    imageUrl: String?,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fallbackRatio = 16f / 9f
    var imageRatio by remember(imageUrl) { mutableStateOf(fallbackRatio) }
    val configuration = LocalConfiguration.current
    val minHeight = 180.dp
    val maxHeight = (configuration.screenHeightDp.dp * 0.5f).coerceAtLeast(300.dp)

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val containerHeight = (maxWidth / imageRatio).coerceIn(minHeight, maxHeight)
        val headerModifier = Modifier
            .fillMaxWidth()
            .height(containerHeight)
            .let { base ->
                if (!imageUrl.isNullOrBlank()) base.clickable { onClick() } else base
            }

        if (imageUrl.isNullOrBlank()) {
            Box(
                modifier = headerModifier.background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.BrokenImage,
                    contentDescription = stringResource(R.string.image_error),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(72.dp)
                )
            }
        } else {
            SubcomposeAsyncImage(
                model = imageUrl,
                contentDescription = contentDescription,
                modifier = headerModifier,
                contentScale = ContentScale.Crop
            ) {
                when (val state = painter.state) {
                    is AsyncImagePainter.State.Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    is AsyncImagePainter.State.Error -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.BrokenImage,
                                contentDescription = stringResource(R.string.image_load_error),
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(64.dp)
                            )
                        }
                    }

                    is AsyncImagePainter.State.Success -> {
                        val drawable = state.result.drawable
                        val w = drawable.intrinsicWidth
                        val h = drawable.intrinsicHeight
                        if (w > 0 && h > 0) {
                            val nextRatio = (w.toFloat() / h.toFloat()).coerceIn(0.45f, 2.5f)
                            if (kotlin.math.abs(nextRatio - imageRatio) > 0.01f) {
                                imageRatio = nextRatio
                            }
                        }
                        SubcomposeAsyncImageContent()
                    }

                    else -> SubcomposeAsyncImageContent()
                }
            }
        }
    }
}

@Composable
fun FullScreenImageDialog(imageUrl: String, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        var scale by remember { mutableStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }

        val state = rememberTransformableState { zoomChange, offsetChange, _ ->
            scale *= zoomChange
            offset += offsetChange
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .clickable { onDismiss() }
        ) {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(animationSpec = tween(300)),
                exit = fadeOut(animationSpec = tween(300)),
                modifier = Modifier.fillMaxSize()
            ) {
                SubcomposeAsyncImage(
                    model = imageUrl,
                    contentDescription = stringResource(R.string.full_screen_image),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                        .graphicsLayer(
                            scaleX = maxOf(1f, scale),
                            scaleY = maxOf(1f, scale),
                            translationX = offset.x,
                            translationY = offset.y
                        )
                        .transformable(state = state),
                    loading = {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color.White)
                        }
                    },
                    error = {
                        Icon(
                            Icons.Default.BrokenImage,
                            contentDescription = stringResource(R.string.image_load_error),
                            tint = Color.White,
                            modifier = Modifier.size(96.dp)
                        )
                    }
                )
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.5f), shape = CircleShape)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.close),
                    tint = Color.White
                )
            }
        }
    }
}

/**
 * Style-2: full-width grey shimmer bars for loading state
 */
@Composable
private fun DetailLoadingPlaceholder(innerPadding: PaddingValues) {
    val shimmerAlpha by rememberInfiniteTransition().animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse
        )
    )

    @Composable
    fun Modifier.shimmerBar(): Modifier = this
        .background(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = shimmerAlpha),
            shape = RoundedCornerShape(8.dp)
        )

    val configuration = LocalConfiguration.current
    val minHeaderHeight = 180.dp
    val maxHeaderHeight = (configuration.screenHeightDp.dp * 0.6f).coerceAtLeast(300.dp)
    val fallbackRatio = 16f / 9f

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Image area shimmer
        item {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val headerHeight = (maxWidth / fallbackRatio).coerceIn(minHeaderHeight, maxHeaderHeight)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(headerHeight)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = shimmerAlpha)
                        )
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = shimmerAlpha)
                    )
            )
        }

        // Title card shimmer
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        RoundedCornerShape(16.dp)
                    )
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(20.dp)
                        .shimmerBar()
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.4f)
                        .height(16.dp)
                        .shimmerBar()
                )
                Spacer(modifier = Modifier.height(8.dp))
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                            .shimmerBar()
                    )
                }
            }
        }

        // Full prompt card shimmer
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                        RoundedCornerShape(16.dp)
                    )
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.4f)
                        .height(18.dp)
                        .shimmerBar()
                )
                Spacer(modifier = Modifier.height(8.dp))
                repeat(5) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .shimmerBar()
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .shimmerBar()
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .shimmerBar()
                )
            }
        }
    }
}


fun openGemini(context: Context, promptText: String) {
    try {
        // 1️⃣ Copy prompt
        val clipboard =
            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(
            ClipData.newPlainText(context.getString(R.string.ai_prompt_label), promptText)
        )

        // 2️⃣ Open Google App (Gemini lives here)
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setPackage("com.google.android.googlequicksearchbox")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(intent)

    } catch (e: Exception) {
        e.printStackTrace()

        // 3️⃣ LAST fallback → browser
        val browserIntent = Intent(
            Intent.ACTION_VIEW,
            "https://gemini.google.com".toUri()
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(browserIntent)
    }
}


/*

fun debugGeminiLaunch(context: Context) {
    val pm = context.packageManager
    val geminiPackage = "com.google.android.apps.bard"

    android.util.Log.e("GEMINI_DEBUG", "====== GEMINI DEBUG START ======")

    // 1️⃣ Check package installed
    try {
        val info = pm.getPackageInfo(geminiPackage, 0)
        android.util.Log.e("GEMINI_DEBUG", "✅ Package installed: ${info.packageName}")
    } catch (e: Exception) {
        android.util.Log.e("GEMINI_DEBUG", "❌ Package NOT installed")
    }

    // 2️⃣ getLaunchIntentForPackage
    val launchIntent = pm.getLaunchIntentForPackage(geminiPackage)
    android.util.Log.e(
        "GEMINI_DEBUG",
        "LaunchIntent = ${launchIntent?.component}"
    )

    // 3️⃣ ACTION_MAIN + CATEGORY_LAUNCHER
    val testIntent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
        setPackage(geminiPackage)
    }

    val resolve = pm.resolveActivity(testIntent, 0)
    android.util.Log.e(
        "GEMINI_DEBUG",
        "ResolveActivity = ${resolve?.activityInfo?.name}"
    )

    // 4️⃣ List ALL activities in Gemini package
    val activities = pm.getPackageInfo(
        geminiPackage,
        PackageManager.GET_ACTIVITIES
    ).activities

    if (activities.isNullOrEmpty()) {
        android.util.Log.e("GEMINI_DEBUG", "❌ No activities found")
    } else {
        activities.forEach {
            android.util.Log.e(
                "GEMINI_DEBUG",
                "Activity: ${it.name}, exported=${it.exported}"
            )
        }
    }

    android.util.Log.e("GEMINI_DEBUG", "====== GEMINI DEBUG END ======")
}

fun openGeminiOrPlayStore(
context: Context,
promptText: String
) {
val geminiPackage = "com.google.android.apps.bard"

// ✅ 1. Copy prompt FIRST
val clipboard =
    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
clipboard.setPrimaryClip(
    ClipData.newPlainText(context.getString(R.string.ai_prompt_label), promptText)
)

val pm = context.packageManager

// 🔥 2. TRY: Explicit launcher intent (Samsung-safe)
try {
    val launchIntent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
        setPackage(geminiPackage)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    if (launchIntent.resolveActivity(pm) != null) {
        context.startActivity(launchIntent)
        return // ✅ EXIT — APP OPENED
    }
} catch (_: Exception) { }

// 🔥 3. TRY: Standard launch intent
try {
    val fallbackLaunch = pm.getLaunchIntentForPackage(geminiPackage)
    if (fallbackLaunch != null) {
        fallbackLaunch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(fallbackLaunch)
        return // ✅ EXIT — APP OPENED
    }
} catch (_: Exception) { }

// ❌ 4. ONLY NOW → Play Store (FORCE GOOGLE PLAY)
try {
    val playStoreIntent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("market://details?id=$geminiPackage")
    ).apply {
        setPackage("com.android.vending") // ❗ no Galaxy Store
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(playStoreIntent)
} catch (_: Exception) {
    // 🌐 Final browser fallback
    context.startActivity(
        Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=$geminiPackage")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}
}*/
