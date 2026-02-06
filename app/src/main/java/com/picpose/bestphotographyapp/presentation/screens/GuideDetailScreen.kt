package com.picpose.bestphotographyapp.presentation.screens

import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
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
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.picpose.bestphotographyapp.presentation.viewmodels.GuidePostViewModel
import kotlinx.coroutines.launch
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.picpose.bestphotographyapp.core.utils.setText

@Suppress("UNUSED_VALUE")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideDetailScreen(
    guidePostId: String,
    viewModel: GuidePostViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Dialog state: show full image
    var showImageDialog by remember { mutableStateOf(false) }

    // Find guide post in current cache first
    val guidePostData = remember(guidePostId, uiState.guidePosts) {
        viewModel.findGuidePostById(guidePostId)
    }

    // Load guide posts if not already loaded
    LaunchedEffect(Unit) {
        if (uiState.guidePosts.isEmpty()) {
            viewModel.loadGuidePosts()
        }
    }

    // Handle error state
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(
                message = error,
                duration = SnackbarDuration.Short
            )
        }
    }

    if (guidePostData == null && !uiState.isLoading) {
        // Guide post not found and not loading
        LaunchedEffect(Unit) {
            snackbarHostState.showSnackbar(
                message = "Guide post not found",
                duration = SnackbarDuration.Short
            )
            onBack()
        }
    }

    // Full image dialog
    if (showImageDialog && guidePostData != null) {
        Dialog(
            onDismissRequest = { showImageDialog = false }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.9f))
                    .clickable { showImageDialog = false }
            ) {
                SubcomposeAsyncImage(
                    model = guidePostData.imageUrl,
                    contentDescription = guidePostData.title,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentScale = ContentScale.Fit
                ) {
                    when (painter.state) {
                        is AsyncImagePainter.State.Loading -> {
                            Box(
                                Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = Color.White)
                            }
                        }

                        is AsyncImagePainter.State.Error -> {
                            Icon(
                                Icons.Default.BrokenImage,
                                contentDescription = "Image load error",
                                tint = Color.White,
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
            title = { Text("Photography Guide") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                titleContentColor = MaterialTheme.colorScheme.onSurface
            )
        )


        Box(modifier = Modifier.fillMaxSize()) {
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                guidePostData != null -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 32.dp)
                    ) {
                        // Hero Image (clickable -> open dialog)
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(300.dp)
                                    .clickable { showImageDialog = true }
                            ) {
                                SubcomposeAsyncImage(
                                    model = guidePostData.imageUrl,
                                    contentDescription = guidePostData.title,
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
                                                modifier = Modifier.size(96.dp)
                                            )
                                        }

                                        else -> SubcomposeAsyncImageContent()
                                    }
                                }

                                // Gradient overlay for better text readability
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.Transparent,
                                                    Color.Black.copy(alpha = 0.3f)
                                                )
                                            )
                                        )
                                )

                                // Action buttons on image
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Favorite button
                                    IconButton(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            viewModel.toggleFavorite(guidePostData)
                                        },
                                        modifier = Modifier
                                            .background(
                                                Color.Black.copy(alpha = 0.5f),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                    ) {
                                        Icon(
                                            imageVector = if (guidePostData.isFavorited) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                            contentDescription = "Favorite",
                                            tint = if (guidePostData.isFavorited) Color.Red else Color.White
                                        )
                                    }

                                    // Share button
                                    IconButton(
                                        onClick = {
                                            val shareText = buildString {
                                                append("Check out this photography guide: ${guidePostData.title}\n\n")
                                                append(guidePostData.excerpt.ifEmpty { guidePostData.content.take(150) })
                                                if (guidePostData.difficultyLevel.isNotEmpty()) {
                                                    append("\n\nDifficulty: ${guidePostData.difficultyLevel}")
                                                }
                                                if (guidePostData.estimatedReadTime > 0) {
                                                    append("\nRead time: ${guidePostData.estimatedReadTime} min")
                                                }
                                                append("\n\n#PicPose #PhotographyGuide")
                                            }
                                        coroutineScope.launch {
                                            clipboard.setText(shareText, label = "guide")
                                        }
                                            Toast.makeText(context, "Guide details copied to clipboard!", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier
                                            .background(
                                                Color.Black.copy(alpha = 0.5f),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                    ) {
                                        Icon(
                                            Icons.Default.Share,
                                            contentDescription = "Share",
                                            tint = Color.White
                                        )
                                    }
                                }
                            }
                        }

                        // Title and Meta Info
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(20.dp)) {
                                    // Title
                                    Text(
                                        text = guidePostData.title,
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Meta info
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            if (guidePostData.difficultyLevel.isNotEmpty()) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        Icons.Default.Star,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp),
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = guidePostData.difficultyLevel,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }

                                            if (guidePostData.estimatedReadTime > 0) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        Icons.Default.Schedule,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp),
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = "${guidePostData.estimatedReadTime} min read",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }

                                        // Engagement stats
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Default.ThumbUp,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "${guidePostData.likes}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Default.Visibility,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "${guidePostData.views}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Content Card
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
                                            Icons.AutoMirrored.Filled.Article,
                                            contentDescription = null,
                                            modifier = Modifier.size(24.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Guide Content",
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
                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text(
                                                text = guidePostData.content,
                                                style = MaterialTheme.typography.bodyLarge,
                                                lineHeight = 24.sp,
                                                color = MaterialTheme.colorScheme.onBackground
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Tags (if available)
                        if (guidePostData.tags.isNotEmpty()) {
                            item {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                ) {
                                    Column(modifier = Modifier.padding(20.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.Tag,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Tags",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))

                                        // Tag chips
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            guidePostData.tags.take(3).forEach { tag ->
                                                SuggestionChip(
                                                    onClick = { },
                                                    label = { Text(tag) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Action buttons
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Like button
                                OutlinedButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.incrementLikes(guidePostData.id)
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(
                                                message = "Guide liked!",
                                                duration = SnackbarDuration.Short
                                            )
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = if (guidePostData.isLiked) Icons.Default.ThumbUp else Icons.Default.ThumbUpOffAlt,
                                        contentDescription = "Like",
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Like (${guidePostData.likes})")
                                }

                                // Copy content button
                                FilledTonalButton(
                                    onClick = {
                                        val fullContent = buildString {
                                            append("${guidePostData.title}\n\n")
                                            append(guidePostData.content)
                                            append("\n\n#PicPose #PhotographyGuide")
                                        }
                                    coroutineScope.launch {
                                        clipboard.setText(fullContent, label = "guide")
                                    }
                                        Toast.makeText(context, "Guide content copied!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = "Copy",
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Copy")
                                }
                            }
                        }
                    }
                }

                else -> {
                    // No guide post data
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = "Error",
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Guide not found",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "The requested guide could not be loaded.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Snackbar
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}
