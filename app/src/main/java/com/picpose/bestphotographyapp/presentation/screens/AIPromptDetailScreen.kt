package com.picpose.bestphotographyapp.presentation.screens

import android.app.Activity
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.picpose.bestphotographyapp.data.models.AIPrompt
import com.picpose.bestphotographyapp.presentation.viewmodels.AIPromptViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIPromptDetailScreen(
    promptId: String,
    viewModel: AIPromptViewModel = viewModel(),
    onBack: () -> Unit,
    onPromptClick: (String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var interstitialAd by remember { mutableStateOf<InterstitialAd?>(null) }
    val adClickCount by viewModel.similarPromptsClickCount.collectAsState()

    // Load ad when the screen is created
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

    // Dialog state: show full image
    var showImageDialog by remember { mutableStateOf(false) }

    // ✅ Find prompt in current cache first
    val prompt = remember(promptId, uiState.allPrompts) {
        uiState.allPrompts.find { it.id == promptId }
    }

    // ✅ Only load if not found in cache AND not currently loading
    LaunchedEffect(promptId) {
        if (prompt == null && !uiState.isLoading) {
            Log.d("PromptDetail", "Prompt $promptId not in cache, loading...")
            viewModel.loadPromptById(promptId)
        } else if (prompt != null) {
            Log.d("PromptDetail", "Prompt $promptId found in cache, no API call needed")
            // When a prompt is loaded, load similar prompts
            prompt.category?.let { category ->
                viewModel.loadSimilarPrompts(category, promptId)
            }
        }
    }

    // Show error Snackbar when uiState.error changes
    LaunchedEffect(uiState.error) {
        uiState.error?.let { msg ->
            coroutineScope.launch {
                snackbarHostState.showSnackbar(msg)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->

        // Show loading indicator first, then check prompt missing state.
        when {
            // 1) Loading state -> show centered loader
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            // 2) If not loading and still no prompt -> show "not found" view
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
                        Button(onClick = onBack) {
                            Text("Go Back")
                        }
                    }
                }
            }

            // 3) Prompt present -> show detail UI
            else -> {
                val promptData = prompt

                // Full-image dialog (shows entire image without crop)
                if (showImageDialog) {
                    Dialog(onDismissRequest = { showImageDialog = false }) {
                        // Fullscreen-like container
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black)
                                .clickable { /* consume clicks outside controls */ },
                            contentAlignment = Alignment.Center
                        ) {
                            // Image with fit scale so it is not cropped
                            SubcomposeAsyncImage(
                                model = promptData.imageUrl,
                                contentDescription = promptData.title,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                                    .verticalScroll(rememberScrollState()),
                                contentScale = ContentScale.Fit
                            ) {
                                when (painter.state) {
                                    is AsyncImagePainter.State.Loading -> {
                                        Box(
                                            Modifier
                                                .fillMaxWidth()
                                                .height(240.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(color = MaterialTheme.colorScheme.onBackground)
                                        }
                                    }

                                    is AsyncImagePainter.State.Error -> {
                                        Icon(
                                            Icons.Default.BrokenImage,
                                            contentDescription = "Image load error",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(96.dp)
                                        )
                                    }

                                    else -> SubcomposeAsyncImageContent()
                                }
                            }

                            // Close button top-right
                            IconButton(
                                onClick = { showImageDialog = false },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(12.dp)
                                    .size(44.dp)
                                    .background(
                                        color = Color.Black.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    // Top App Bar
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
                            // Share button
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(promptData.fullPrompt ?: ""))
                                    Toast.makeText(
                                        context,
                                        "Prompt copied for sharing!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Share")
                            }

                            // Favorite button
                            IconButton(
                                onClick = { viewModel.toggleFavorite(promptData) }
                            ) {
                                Icon(
                                    if (promptData.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Favorite",
                                    tint = if (promptData.isFavorite)
                                        MaterialTheme.colorScheme.error
                                    else
                                        LocalContentColor.current
                                )
                            }
                        }
                    )

                    // Scrollable content
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentPadding = PaddingValues(bottom = 32.dp)
                    ) {
                        // Hero Image (clickable -> open dialog)
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(300.dp)
                                    .clickable { showImageDialog = true } // open full image dialog
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

                                // Gradient overlay (remembered)
                                val gradient = remember {
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.4f)
                                        ),
                                        startY = 100f
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(gradient)
                                )
                            }
                        }

                        // Title + Stats Card
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

                        // Full Prompt Section
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

                                    Button(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(promptData.fullPrompt ?: ""))
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("✨ Prompt copied to clipboard!")
                                            }
                                        },
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
                                            text = "Copy Full Prompt",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }

                        // Tags Section
                        promptData.tags?.takeIf { it.isNotEmpty() }?.let { tags ->
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
                                            text = "🏷️ Tags",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )

                                        Spacer(modifier = Modifier.height(12.dp))

                                        FlowRow(
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
                                                    )
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
                                    }
                                }
                            }
                        }

                        // Similar Prompts Section
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
                                                    viewModel.onSimilarPromptClicked()
                                                    if (adClickCount >= 1) { // Show ad every 2 clicks
                                                        interstitialAd?.let {
                                                            it.fullScreenContentCallback = object : FullScreenContentCallback() {
                                                                override fun onAdDismissedFullScreenContent() {
                                                                    super.onAdDismissedFullScreenContent()
                                                                    onPromptClick(similarPrompt.id!!)
                                                                    viewModel.resetSimilarPromptClickCount()
                                                                }

                                                                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                                                                    super.onAdFailedToShowFullScreenContent(adError)
                                                                    onPromptClick(similarPrompt.id!!)
                                                                }
                                                            }
                                                            it.show(context as Activity)
                                                        }
                                                    } else {
                                                        onPromptClick(similarPrompt.id!!)
                                                    }
                                                }
                                            )
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

// Simple FlowRow fallback (replace with Accompanist FlowRow if available)
@Composable
private fun FlowRow(
    horizontalArrangement: Arrangement.Horizontal,
    verticalArrangement: Arrangement.Vertical,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = verticalArrangement) {
        content()
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
                    .height(120.dp)
                    .fillMaxWidth(),
                contentScale = ContentScale.Crop
            ) {
                if (painter.state is AsyncImagePainter.State.Loading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
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
