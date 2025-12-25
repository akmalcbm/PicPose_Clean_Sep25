package com.picpose.bestphotographyapp.presentation.screens

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.picpose.bestphotographyapp.data.models.AIPrompt
import com.picpose.bestphotographyapp.presentation.ads.LargeNativeAdCard
import com.picpose.bestphotographyapp.presentation.components.EdgeToEdgeScaffold
import com.picpose.bestphotographyapp.presentation.viewmodels.AIPromptViewModel
import com.picpose.bestphotographyapp.utils.displayLikes
import com.picpose.bestphotographyapp.utils.displayViews
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
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
    val clipboardManager = LocalClipboardManager.current
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


    // To avoid multiple view-count increments
    val viewTracked = rememberSaveable(promptId) { mutableStateOf(false) }

    // ⚠️ IMPORTANT:
    // View count MUST be incremented ONLY here with Server Stored total Views +1 (Ai Prompts Detail Screen)
    // Do NOT call registerView() from any ViewModel load/fetch function
    LaunchedEffect(promptId) {
        if (!viewTracked.value) {
            aiPromptViewModel.registerView(promptId)
            viewTracked.value = true
        }
    }


    // Interstitial ad
    var interstitialAd by remember { mutableStateOf<InterstitialAd?>(null) }
    val similarClickCount by aiPromptViewModel.similarPromptsClickCount.collectAsState()

    val skipGeminiDialog by aiPromptViewModel.skipGeminiDialog.collectAsState()

    // Load interstitial once
    LaunchedEffect(Unit) {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            context,
            "ca-app-pub-3940256099942544/1033173712", // test id
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    interstitialAd = null
                }
            }
        )
    }

    // Native Ad (single instance)
    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }
    DisposableEffect(Unit) {
        val adLoader = AdLoader.Builder(
            context,
            "ca-app-pub-3940256099942544/2247696110" // test native id
        ).forNativeAd { ad ->
            nativeAd?.destroy()
            nativeAd = ad
        }.withNativeAdOptions(
            NativeAdOptions.Builder().build()
        ).build()

        adLoader.loadAd(AdRequest.Builder().build())

        onDispose {
            nativeAd?.destroy()
            nativeAd = null
        }
    }

    var showImageDialog by remember { mutableStateOf(false) }

    // Resolve prompt from VM state
    val effectivePrompt: AIPrompt? = remember(promptId, uiState.allPrompts, uiState.selectedPrompt, localPrompt) {
        val idTrim = promptId.trim()
        localPrompt
            ?: uiState.selectedPrompt?.takeIf { it.id?.trim() == idTrim }
            ?: uiState.allPrompts.find { it.id?.trim() == idTrim }
    }

    // Keep localPrompt in sync / trigger fetch when not found
    LaunchedEffect(promptId, uiState.allPrompts, uiState.selectedPrompt) {
        val idTrim = promptId.trim()
        val merged = listOfNotNull(uiState.selectedPrompt) + uiState.allPrompts
        val match = merged.find { it.id?.trim() == idTrim }

        android.util.Log.d(TAG_DETAIL, "Received promptId = $promptId")
        android.util.Log.d(TAG_DETAIL, "Total prompts available = ${uiState.allPrompts.size}")
        android.util.Log.d(TAG_DETAIL, "Matched prompt? = ${match != null}")

        if (match != null) {
            localPrompt = match
            aiPromptViewModel.loadPromptById(promptId)
        } else if (!hasRequestedFromApi) {
            hasRequestedFromApi = true
            android.util.Log.w(TAG_DETAIL, "⚠️ Not found in memory → Fetching from API")
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
        effectivePrompt?.imageUrl?.let {
            FullScreenImageDialog(imageUrl = it, onDismiss = { showImageDialog = false })
        }
    }

    EdgeToEdgeScaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Prompt Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(effectivePrompt?.fullPrompt ?: ""))
                            Toast.makeText(context, "Prompt copied for sharing!", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }

                    effectivePrompt?.let { p ->
                        IconButton(onClick = { aiPromptViewModel.onFavoriteClicked(p) }) {
                            Icon(
                                if (p.isFavouriteBookmarked) Icons.Default.BookmarkAdded else Icons.Default.BookmarkBorder,
                                contentDescription = "Favorite Bookmarked AI Prompts",
                                tint = if (p.isFavouriteBookmarked)
                                    Color(0xFFFFC107)
                                else
                                    LocalContentColor.current
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                ),
                modifier = Modifier.windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top)
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
                            text = "Prompt not found",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onBack) { Text("Go Back") }
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
                            bottom = 100.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 🖼 Image Header
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(380.dp)
                            ) {
                                SubcomposeAsyncImage(
                                    model = promptData.imageUrl,
                                    contentDescription = promptData.title,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(
                                            RoundedCornerShape(
                                                bottomStart = 16.dp,
                                                bottomEnd = 16.dp
                                            )
                                        )
                                        .clickable { showImageDialog = true },
                                    contentScale = ContentScale.Crop
                                ) {
                                    when (painter.state) {
                                        is AsyncImagePainter.State.Loading -> {
                                            Box(
                                                Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                CircularProgressIndicator()
                                            }
                                        }

                                        is AsyncImagePainter.State.Error -> {
                                            Icon(
                                                Icons.Default.BrokenImage,
                                                contentDescription = "Image load error",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(64.dp)
                                            )
                                        }

                                        else -> SubcomposeAsyncImageContent()
                                    }
                                }

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
                                likes = promptData.displayLikes(localEngagement),
                                views = promptData.displayViews(localEngagement),
                                favorites = promptData.favorites,
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
                                    .padding(horizontal = 16.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                            ) {
                                Column(modifier = Modifier.padding(20.dp)) {
                                    Text(
                                        text = promptData.title,
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold
                                    )

                                    if (!promptData.category.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(50),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                            border = BorderStroke(
                                                1.dp,
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                            )
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(
                                                    horizontal = 10.dp,
                                                    vertical = 4.dp
                                                ),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.Label,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = promptData.category ?: "",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    if (!promptData.shortPrompt.isNullOrBlank()) {
                                        Text(
                                            text = promptData.shortPrompt ?: "",
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
                            val clipMgr = LocalClipboardManager.current
                            val localHaptic = LocalHapticFeedback.current
                            var showGeminiDialog by remember { mutableStateOf(false) }

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
                                            text = "Full AI Prompt",
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
                                                        "Prompt copied. Opening Gemini…",
                                                        Toast.LENGTH_SHORT
                                                    ).show()

                                                    promptData.id?.toIntOrNull()?.let {
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
                                                contentDescription = "Copy Prompts & Open in Gemini",
                                                modifier = Modifier.size(22.dp),
                                                tint = MaterialTheme.colorScheme.onPrimary
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = "Copy Prompts & Open in Gemini",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimary
                                                )
                                                Text(
                                                    text = "Best for generating results",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                                                )
                                            }
                                        }


                                        // 📢 One-Time Large Native Ad with Shimmer Placeholder
                                        if (nativeAd == null) {
                                            // ⏳ Shimmer while ad loads
                                            LargeAdShimmerPlaceholder()
                                        } else {
                                            // 🎉 Real Large Ad
                                            LargeNativeAdCard(
                                                nativeAd = nativeAd,
                                                modifier = Modifier
                                                    .fillMaxWidth()
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
                                                    clipMgr.setText(AnnotatedString(textToCopy))
                                                    localHaptic.performHapticFeedback(HapticFeedbackType.LongPress)

                                                    Toast.makeText(
                                                        ctx,
                                                        "Prompt copied to clipboard",
                                                        Toast.LENGTH_SHORT
                                                    ).show()

                                                    promptData.id?.toIntOrNull()?.let {
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
                                                text = "Copy Prompt",
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
                                    title = { Text("Continue in Gemini") },
                                    text = {
                                        Column {
                                            Text(
                                                "Your prompt will be copied and opened in Gemini.\n\n" +
                                                        "If the app is installed, tap “Try in app”."
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
                                                Text("Don’t ask again")
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
                                                "Prompt copied. Opening Gemini…",
                                                Toast.LENGTH_SHORT
                                            ).show()

                                            promptData.id?.toIntOrNull()?.let {
                                                aiPromptViewModel.incrementCopyCount(it)
                                            }

                                            dontAskAgain = false
                                        }) {
                                            Text("Continue")
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = {
                                            showGeminiDialog = false
                                            dontAskAgain = false
                                        }) {
                                            Text("Cancel")
                                        }
                                    }
                                )
                            }

                        }



                        // 🏷 Tags (expandable)
                        promptData.tags?.takeIf { it.isNotEmpty() }?.let { tags ->
                            item {
                                var expanded by remember { mutableStateOf(false) }

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = "🏷️ Tags",
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
                                                    text = if (expanded) "Show Less" else "Show More",
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
                                        text = "Similar Prompts",
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
                                                    val id = similarPrompt.id ?: return@SimilarPromptCard
                                                    isTransitionLoading = true

                                                    // Interstitial after 1 click (test condition)
                                                    if (similarClickCount >= 1) {
                                                        interstitialAd?.let { ad ->
                                                            ad.fullScreenContentCallback =
                                                                object : FullScreenContentCallback() {
                                                                    override fun onAdDismissedFullScreenContent() {
                                                                        onPromptClick(id)
                                                                    }

                                                                    override fun onAdFailedToShowFullScreenContent(
                                                                        adError: AdError
                                                                    ) {
                                                                        onPromptClick(id)
                                                                    }
                                                                }
                                                            ad.show(context as Activity)
                                                        } ?: onPromptClick(id)
                                                    } else {
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
                                    text = "Loading prompt…",
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
private fun StatsRow(
    likes: Int,
    views: Int,
    favorites: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatPill(icon = Icons.Default.ThumbUp, value = likes, tint = MaterialTheme.colorScheme.error)
        StatPill(icon = Icons.Default.Visibility, value = views, tint = MaterialTheme.colorScheme.primary)
        StatPill(
            icon = Icons.Default.BookmarkBorder,
            value = favorites,
            tint = MaterialTheme.colorScheme.tertiary
        )
    }
}

// small helper — icon + number only (Style A)
@Composable
private fun StatPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: Int,
    tint: Color
) {
    Surface(
        color = tint.copy(alpha = 0.08f),
        shape = RoundedCornerShape(50)
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
                text = value.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = tint,
                fontWeight = FontWeight.Medium
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
                    contentDescription = "Full-screen image",
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
                            contentDescription = "Image load error",
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
                    contentDescription = "Close",
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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentPadding = PaddingValues(bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Image area shimmer
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = shimmerAlpha)
                    )
            )
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


fun openGemini(context: Context, promptText: String) {
    try {
        // 1️⃣ Copy prompt
        val clipboard =
            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(
            ClipData.newPlainText("AI Prompt", promptText)
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
            Uri.parse("https://gemini.google.com")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(browserIntent)
    }
}

/*fun openGeminiOrPlayStore(
context: Context,
promptText: String
) {
val geminiPackage = "com.google.android.apps.bard"

// ✅ 1. Copy prompt FIRST
val clipboard =
    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
clipboard.setPrimaryClip(
    ClipData.newPlainText("AI Prompt", promptText)
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
