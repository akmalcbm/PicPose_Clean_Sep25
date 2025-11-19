package com.picpose.bestphotographyapp.presentation.screens

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
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
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import com.picpose.bestphotographyapp.data.models.AIPrompt
import com.picpose.bestphotographyapp.presentation.ads.LargeNativeAdCard
import com.picpose.bestphotographyapp.presentation.components.EdgeToEdgeScaffold
import com.picpose.bestphotographyapp.presentation.viewmodels.AIPromptViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG_DETAIL = "PromptDetail"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun AIPromptDetailScreen(
    promptId: String,
    viewModel: AIPromptViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onPromptClick: (String) -> Unit,
    onTagClick: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

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
    var lastCountedPromptId by rememberSaveable { mutableStateOf<String?>(null) }

    // Interstitial ad
    var interstitialAd by remember { mutableStateOf<InterstitialAd?>(null) }
    val similarClickCount by viewModel.similarPromptsClickCount.collectAsState()

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
            viewModel.selectPromptForDetail(match)
        } else if (!hasRequestedFromApi) {
            hasRequestedFromApi = true
            android.util.Log.w(TAG_DETAIL, "⚠️ Not found in memory → Fetching from API")
            viewModel.loadPromptById(promptId)
        }
    }

    // Increment view count once per promptId
    LaunchedEffect(promptId) {
        val numericId = promptId.toIntOrNull()
        if (numericId != null && lastCountedPromptId != promptId) {
            viewModel.incrementViewCount(numericId)
            lastCountedPromptId = promptId
        }
        // transition start
        isTransitionLoading = true
        listState.scrollToItem(0)
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
            viewModel.loadSimilarPrompts(cat, id)
        }
    }

    // Error → snackbar
    LaunchedEffect(uiState.error) {
        uiState.error?.let { msg ->
            coroutineScope.launch {
                snackbarHostState.showSnackbar(msg)
                viewModel.clearError()
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
                        IconButton(onClick = { viewModel.toggleFavorite(p) }) {
                            Icon(
                                if (p.isFavorite) Icons.Default.BookmarkAdded else Icons.Default.BookmarkBorder,
                                contentDescription = "Favorite Bookmarked AI Prompts",
                                tint = if (p.isFavorite)
                                    MaterialTheme.colorScheme.surfaceTint
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

                            // 🧮 Stats Row directly under image
                            StatsRow(
                                likes = promptData.likes,
                                views = promptData.views,
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
                                        // Copy button
                                        Button(
                                            onClick = {
                                                val textToCopy = promptData.fullPrompt ?: ""
                                                if (textToCopy.isNotBlank()) {
                                                    clipMgr.setText(AnnotatedString(textToCopy))
                                                    localHaptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    Toast
                                                        .makeText(
                                                            ctx,
                                                            "✨ Prompt copied to clipboard!",
                                                            Toast.LENGTH_SHORT
                                                        )
                                                        .show()
                                                    promptData.id?.toIntOrNull()?.let { id ->
                                                        viewModel.incrementCopyCount(id)
                                                    }
                                                } else {
                                                    Toast.makeText(
                                                        ctx,
                                                        "⚠️ Nothing to copy!",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary
                                            ),
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(vertical = 14.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.ContentCopy,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Copy Full Prompt",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }

                                        // Gemini button
                                        OutlinedButton(
                                            onClick = { showGeminiDialog = true },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(vertical = 14.dp)
                                        ) {
                                            Icon(
                                                painter = painterResource(
                                                    id = android.R.drawable.ic_menu_search
                                                ),
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Open in Gemini",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }

                            // Gemini Dialog
                            if (showGeminiDialog) {
                                AlertDialog(
                                    onDismissRequest = { showGeminiDialog = false },
                                    title = { Text("Open in Gemini?") },
                                    text = {
                                        Text(
                                            "This will open the prompt in the Gemini app (if installed). " +
                                                    "If not, you’ll be redirected to its Play Store page."
                                        )
                                    },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            showGeminiDialog = false
                                            openGeminiOrPlayStore(ctx, promptData.fullPrompt ?: "")

                                            promptData.id?.toIntOrNull()?.let { id ->
                                                coroutineScope.launch {
                                                    viewModel.incrementCopyCount(id)
                                                }
                                            }
                                        }) {
                                            Text("Continue")
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showGeminiDialog = false }) {
                                            Text("Cancel")
                                        }
                                    }
                                )
                            }
                        }

                        // 📢 One-Time Large Native Ad with Shimmer Placeholder
                        item {
                            if (nativeAd == null) {
                                // ⏳ Shimmer while ad loads
                                LargeAdShimmerPlaceholder()
                            } else {
                                // 🎉 Real Large Ad
                                LargeNativeAdCard(
                                    nativeAd = nativeAd,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
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
                                                    viewModel.onSimilarPromptClicked()

                                                    // Interstitial after 1 click (test condition)
                                                    if (similarClickCount >= 1) {
                                                        interstitialAd?.let { ad ->
                                                            ad.fullScreenContentCallback =
                                                                object : FullScreenContentCallback() {
                                                                    override fun onAdDismissedFullScreenContent() {
                                                                        onPromptClick(id)
                                                                        viewModel.resetSimilarPromptClickCount()
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
        StatPill(icon = Icons.Default.Favorite, value = likes, tint = MaterialTheme.colorScheme.error)
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

fun openGeminiOrPlayStore(context: Context, promptText: String) {
    val geminiPackage = "com.google.android.apps.bard"
    val pm = context.packageManager

    // Always copy text to clipboard before any action
    val clipboard = ContextCompat.getSystemService(context, ClipboardManager::class.java)
    clipboard?.setPrimaryClip(ClipData.newPlainText("Prompt", promptText))
    Toast.makeText(context, "✨ Prompt copied to clipboard", Toast.LENGTH_SHORT).show()

    try {
        pm.getPackageInfo(geminiPackage, 0)

        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://gemini.google.com/app")
            setPackage(geminiPackage)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        if (intent.resolveActivity(pm) != null) {
            context.startActivity(intent)
        } else {
            val launchIntent = pm.getLaunchIntentForPackage(geminiPackage)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
            } else {
                openGeminiPlayStore(context)
            }
        }
    } catch (e: PackageManager.NameNotFoundException) {
        openGeminiPlayStore(context)
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Unable to open Gemini", Toast.LENGTH_SHORT).show()
    }
}

private fun openGeminiPlayStore(context: Context) {
    val geminiPackage = "com.google.android.apps.bard"
    val playStoreIntent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("https://play.google.com/store/apps/details?id=$geminiPackage")
    ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(playStoreIntent)
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




/*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AIPromptDetailScreen(
    promptId: String,
    viewModel: AIPromptViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onPromptClick: (String) -> Unit,
    onTagClick: (String) -> Unit, // NEW: navigate to Tag screen
) {
    val uiState by viewModel.uiState.collectAsState()

    var localPrompt by remember { mutableStateOf<AIPrompt?>(null) }

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val viewModel: AIPromptViewModel = hiltViewModel()


    // NEW: list state for scroll-to-top behavior
    val listState: LazyListState = rememberLazyListState()

    // NEW: transition loading overlay flag (separate from global uiState.isLoading)
    var isTransitionLoading by rememberSaveable { mutableStateOf(false) }

    var interstitialAd by remember { mutableStateOf<InterstitialAd?>(null) }
    val adClickCount by viewModel.similarPromptsClickCount.collectAsState()

    // Load interstitial ad on screen create (kept as-is)
    LaunchedEffect(Unit) {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            context, "ca-app-pub-3940256099942544/1033173712", adRequest,
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

    // Native Ad state and loader (Native Advanced)
    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }
    DisposableEffect(Unit) {
        val adLoader = AdLoader.Builder(
            context,
            // Test Native Advanced unit id
            "ca-app-pub-3940256099942544/2247696110"
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

    */
/*val prompt = remember(promptId, uiState.allPrompts) {
        uiState.allPrompts.find { it.id.toString() == promptId }
            ?: uiState.allPrompts.find { it.id == promptId }
    }*//*


    val prompt = remember(promptId, uiState.allPrompts, uiState.selectedPrompt) {
        Log.d("PromptDetail", "Received promptId = $promptId")
        Log.d("PromptDetail", "Total prompts available = ${uiState.allPrompts.size}")

        val fromList = uiState.allPrompts.find { it.id?.trim() == promptId.trim() }
        val fromSelected = uiState.selectedPrompt?.takeIf { it.id?.trim() == promptId.trim() }

        val match = fromList ?: fromSelected

        Log.d("PromptDetail", "Matched prompt? = ${match != null}")
        match
    }


    if (showImageDialog) {
        prompt?.imageUrl?.let {
            FullScreenImageDialog(imageUrl = it, onDismiss = { showImageDialog = false })
        }
    }

    LaunchedEffect(promptId) {
        val numericId = promptId.toIntOrNull()
        if (numericId != null) {
            viewModel.incrementViewCount(numericId)
        }

        if (prompt == null) {
            Log.w("PromptDetail", "⚠️ Prompt $promptId not found in memory → Fetching from API")
            viewModel.loadPromptById(promptId)
        } else {
            // ensure VM knows which one is selected (in case we navigated from deep link)
            viewModel.selectPromptForDetail(prompt)
        }
    }


    LaunchedEffect(prompt?.id, prompt?.category) {
        val category = prompt?.category
        val id = prompt?.id
        if (!category.isNullOrBlank() && !id.isNullOrBlank()) {
            viewModel.loadSimilarPrompts(category, id)
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { msg ->
            coroutineScope.launch {
                snackbarHostState.showSnackbar(msg)
            }
        }
    }

    // NEW: When promptId changes, scroll to top immediately and show transition overlay
    LaunchedEffect(promptId) {
        isTransitionLoading = true
        // Make sure UI jumps to top so it feels like a fresh screen
        listState.scrollToItem(0)
    }

    // NEW: Hide transition overlay once loading (if any) completes; keep it for a minimum duration for UX clarity
    LaunchedEffect(promptId, uiState.isLoading) {
        if (!uiState.isLoading) {
            // small delay so user perceives the loading feedback
            delay(300)
            isTransitionLoading = false
        } else {
            isTransitionLoading = true
        }
    }

    LaunchedEffect(promptId, uiState.allPrompts, uiState.selectedPrompt) {
        Log.d("PromptDetail", "Received promptId = $promptId")
        Log.d("PromptDetail", "Total prompts available = ${uiState.allPrompts.size}")

        val idTrim = promptId.trim()
        val mergedSearch = listOfNotNull(
            uiState.selectedPrompt,
            *uiState.allPrompts.toTypedArray()
        ).find { it.id?.trim() == idTrim }

        Log.d("PromptDetail", "Matched prompt? = ${mergedSearch != null}")

        if (mergedSearch != null) {
            localPrompt = mergedSearch
            viewModel.selectPromptForDetail(mergedSearch)
        } else {
            Log.w("PromptDetail", "⚠️ Not found → Fetching from API")
            viewModel.loadPromptById(promptId)
        }
    }

    EdgeToEdgeScaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Prompt Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(prompt?.fullPrompt ?: ""))
                            Toast.makeText(
                                context,
                                "Prompt copied for sharing!",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }

                    prompt?.let {
                        IconButton(onClick = { viewModel.toggleFavorite(it) }) {
                            Icon(
                                if (it.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (it.isFavorite)
                                    MaterialTheme.colorScheme.error
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
                ) // ✅ perfect status bar handling
            )
        },
        snackbarHostState = snackbarHostState,

    ) { innerPadding ->

        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(
                            WindowInsets.safeDrawing
                                .only(WindowInsetsSides.Horizontal)
                                .asPaddingValues()
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            prompt == null -> {
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

            else -> {
                val promptData = prompt

                // ✅ Single container with scroll + overlay support
                Box(modifier = Modifier.fillMaxSize()) {

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentPadding = PaddingValues(
                            top = 8.dp,
                            bottom = 100.dp // ✅ consistent nav bar gap
                        ),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 🖼️ Image Header
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(380.dp)
                                    .clickable { showImageDialog = true }
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
                                        ),
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

                                // gradient overlay
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.Transparent,
                                                    Color.Black.copy(alpha = 0.4f)
                                                ),
                                                startY = 100f
                                            )
                                        )
                                )
                            }
                        }

                        // 🧾 Title + Stats + Short Description
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
                                            text = "${promptData.likes} likes",
                                            color = MaterialTheme.colorScheme.error
                                        )

                                        promptData.tags?.takeIf { it.isNotEmpty() }?.let { tags ->
                                            StatChip(
                                                icon = Icons.AutoMirrored.Filled.Label,
                                                text = "${tags.size} tags",
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Text(
                                        text = promptData.shortPrompt ?: "",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 24.sp
                                    )
                                }
                            }
                        }

                        // ✨ Full Prompt Section
                        item {
                            val context = LocalContext.current
                            val clipboardManager = LocalClipboardManager.current
                            val haptic = LocalHapticFeedback.current
                            val coroutineScope = rememberCoroutineScope()
                            val snackbarHostState = remember { SnackbarHostState() }

                            // ✅ Use your existing AIPromptViewModel (injected by Hilt)
                            val viewModel: AIPromptViewModel = hiltViewModel()

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
                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
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

                                    // 🔹 Buttons Row (Copy + Gemini)
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                                        // ✅ Copy Button
                                        Button(
                                            onClick = {
                                                val textToCopy = promptData.fullPrompt ?: ""
                                                if (textToCopy.isNotBlank()) {
                                                    // ✅ Copy text to clipboard instantly
                                                    clipboardManager.setText(AnnotatedString(textToCopy))
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                                                    // ✅ Show instant toast feedback instead of Snackbar
                                                    Toast.makeText(context, "✨ Prompt copied to clipboard!", Toast.LENGTH_SHORT).show()

                                                    // ✅ Increment copy count in backend via ViewModel
                                                    promptData.id.toIntOrNull()?.let { id ->
                                                        viewModel.incrementCopyCount(id)
                                                    }
                                                } else {
                                                    Toast.makeText(context, "⚠️ Nothing to copy!", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary
                                            ),
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(vertical = 14.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.ContentCopy,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Copy Full Prompt",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }


                                        // ✅ Gemini Button
                                        OutlinedButton(
                                            onClick = {
                                                showGeminiDialog = true
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(vertical = 14.dp)
                                        ) {
                                            Icon(
                                                painter = painterResource(id = android.R.drawable.ic_menu_search),
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Open in Gemini",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }

                            // 🔹 Gemini Confirmation Dialog
                            if (showGeminiDialog) {
                                AlertDialog(
                                    onDismissRequest = { showGeminiDialog = false },
                                    title = { Text("Open in Gemini?") },
                                    text = {
                                        Text(
                                            "This will open the prompt in the Gemini app (if installed). " +
                                                    "If not, you’ll be redirected to its Play Store page."
                                        )
                                    },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            showGeminiDialog = false
                                            openGeminiOrPlayStore(context, promptData.fullPrompt ?: "")

                                            // ✅ Increment copy count when opening in Gemini too
                                            promptData.id.toIntOrNull()?.let { id ->
                                                coroutineScope.launch {
                                                    viewModel.incrementCopyCount(id)
                                                }
                                            }
                                        }) {
                                            Text("Continue")
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showGeminiDialog = false }) {
                                            Text("Cancel")
                                        }
                                    }
                                )
                            }
                        }

                        // 📢 Native Ad
                        if (nativeAd != null) {
                            item {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                                ) {
                                    NativeAdCard(nativeAd = nativeAd!!)
                                }
                            }
                        }

                        // 🏷️ Expandable Tags
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
                                            // FlowRow container with height control
                                            FlowRow(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .then(
                                                        if (!expanded) Modifier.heightIn(max = 120.dp) // roughly 2-3 lines height
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

                                            // Gradient fade overlay when collapsed
                                            if (!expanded && tags.size > 8) {
                                                Box(
                                                    modifier = Modifier
                                                        .matchParentSize()
                                                        .background(
                                                            Brush.verticalGradient(
                                                                colors = listOf(
                                                                    Color.Transparent,
                                                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                                                                )
                                                            )
                                                        )
                                                )
                                            }
                                        }

                                        // Expand / Collapse button
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
                                                    contentDescription = if (expanded) "Collapse" else "Expand",
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
                                Column(modifier = Modifier.padding(top = 16.dp)) {
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
                                                    isTransitionLoading = true
                                                    viewModel.onSimilarPromptClicked()
                                                    if (adClickCount >= 1) {
                                                        interstitialAd?.let {
                                                            it.fullScreenContentCallback =
                                                                object :
                                                                    FullScreenContentCallback() {
                                                                    override fun onAdDismissedFullScreenContent() {
                                                                        super.onAdDismissedFullScreenContent()
                                                                        onPromptClick(similarPrompt.id)
                                                                        viewModel.resetSimilarPromptClickCount()
                                                                    }

                                                                    override fun onAdFailedToShowFullScreenContent(
                                                                        adError: AdError
                                                                    ) {
                                                                        super.onAdFailedToShowFullScreenContent(
                                                                            adError
                                                                        )
                                                                        onPromptClick(similarPrompt.id)
                                                                    }
                                                                }
                                                            it.show(context as Activity)
                                                        } ?: onPromptClick(similarPrompt.id)
                                                    } else {
                                                        onPromptClick(similarPrompt.id)
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 🌀 Loading overlay
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
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
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

fun openGeminiOrPlayStore(context: Context, promptText: String) {
    val geminiPackage = "com.google.android.apps.bard"
    val pm = context.packageManager

    // Always copy text to clipboard before any action
    val clipboard = ContextCompat.getSystemService(context, ClipboardManager::class.java)
    clipboard?.setPrimaryClip(ClipData.newPlainText("Prompt", promptText))
    Toast.makeText(context, "✨ Prompt copied to clipboard", Toast.LENGTH_SHORT).show()

    try {
        // Check if Gemini app is installed
        pm.getPackageInfo(geminiPackage, 0)

        // Try to open Gemini directly using deep link
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://gemini.google.com/app")
            setPackage(geminiPackage)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        // Verify there’s an activity to handle the intent
        if (intent.resolveActivity(pm) != null) {
            context.startActivity(intent)
        } else {
            // Fallback if deep link not handled, try to launch main activity
            val launchIntent = pm.getLaunchIntentForPackage(geminiPackage)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
            } else {
                // If still fails, open Play Store
                openGeminiPlayStore(context)
            }
        }

    } catch (e: PackageManager.NameNotFoundException) {
        // Not installed → Open Play Store
        openGeminiPlayStore(context)
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Unable to open Gemini", Toast.LENGTH_SHORT).show()
    }
}

private fun openGeminiPlayStore(context: Context) {
    val geminiPackage = "com.google.android.apps.bard"
    val playStoreIntent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("https://play.google.com/store/apps/details?id=$geminiPackage")
    ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(playStoreIntent)
}


@Composable
private fun StatChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
private fun NativeAdCard(nativeAd: NativeAd) {
    AndroidView(
        factory = { context ->
            val adView = NativeAdView(context)

            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(32, 32, 32, 32)
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
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTypeface(typeface, Typeface.BOLD)
            }
            val body = TextView(context).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
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
                text = nativeAd.callToAction ?: "Install"
                visibility = if (nativeAd.callToAction.isNullOrBlank()) View.GONE else View.VISIBLE
            }
            adView.mediaView?.mediaContent = nativeAd.mediaContent

            adView.setNativeAd(nativeAd)
        }
    )
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
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
}*/
