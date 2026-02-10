package com.picpose.bestphotographyapp.presentation.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.picpose.bestphotographyapp.R
import com.picpose.bestphotographyapp.data.models.GuideContentBlock
import com.picpose.bestphotographyapp.core.utils.MediaUrlResolver
import com.picpose.bestphotographyapp.presentation.viewmodels.GuidePostViewModel
import kotlinx.coroutines.launch
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.picpose.bestphotographyapp.core.utils.setText

private fun fullGuideImageUrl(path: String?): String? {
    return MediaUrlResolver.resolve(path)
}

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
    var blockImageDialogUrl by remember { mutableStateOf<String?>(null) }

    val guidePostData = uiState.selectedGuidePost
        ?: remember(guidePostId, uiState.guidePosts) { viewModel.findGuidePostById(guidePostId) }
    val renderedBlocks = uiState.blocks

    LaunchedEffect(guidePostId) {
        viewModel.loadGuidePostById(guidePostId)
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
                message = context.getString(R.string.guide_post_not_found),
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
                    model = fullGuideImageUrl(guidePostData.imageUrl),
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
                                contentDescription = stringResource(R.string.image_load_error),
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
                        contentDescription = stringResource(R.string.close),
                        tint = Color.White
                    )
                }
            }
        }
    }

    if (!blockImageDialogUrl.isNullOrBlank()) {
        Dialog(onDismissRequest = { blockImageDialogUrl = null }) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.9f))
                    .clickable { blockImageDialogUrl = null }
            ) {
                SubcomposeAsyncImage(
                    model = fullGuideImageUrl(blockImageDialogUrl),
                    contentDescription = stringResource(R.string.full_screen_image),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentScale = ContentScale.Fit
                ) {
                    when (painter.state) {
                        is AsyncImagePainter.State.Loading -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Color.White)
                            }
                        }
                        is AsyncImagePainter.State.Error -> {
                            Icon(
                                Icons.Default.BrokenImage,
                                contentDescription = stringResource(R.string.image_load_error),
                                tint = Color.White,
                                modifier = Modifier.size(96.dp)
                            )
                        }
                        else -> SubcomposeAsyncImageContent()
                    }
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top App Bar
        TopAppBar(
            title = { Text(stringResource(R.string.photography_guide_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back)
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
                                    model = fullGuideImageUrl(guidePostData.imageUrl),
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
                                                contentDescription = stringResource(R.string.image_load_error),
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
                                            contentDescription = stringResource(R.string.favorite),
                                            tint = if (guidePostData.isFavorited) Color.Red else Color.White
                                        )
                                    }

                                    // Share button
                                    IconButton(
                                        onClick = {
                                            viewModel.shareGuidePost(context, guidePostData)
                                        },
                                        modifier = Modifier
                                            .background(
                                                Color.Black.copy(alpha = 0.5f),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                    ) {
                                        Icon(
                                            Icons.Default.Share,
                                            contentDescription = stringResource(R.string.share),
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

                                    if (guidePostData.isFeatured) {
                                        SuggestionChip(
                                            onClick = { },
                                            label = { Text(stringResource(R.string.featured)) }
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }

                                    if (guidePostData.category.isNotBlank()) {
                                        AssistChip(
                                            onClick = { },
                                            label = { Text(guidePostData.category) },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.Category,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }

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
                                                        text = stringResource(R.string.min_read, guidePostData.estimatedReadTime),
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

                        item {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Article,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.guide_content),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        itemsIndexed(
                            items = renderedBlocks,
                            key = { idx, block -> "${idx}_${block::class.simpleName}" }
                        ) { _, block ->
                            GuideContentBlockItem(
                                block = block,
                                onImageClick = { blockImageDialogUrl = it },
                                onVideoClick = { url ->
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(context.getString(R.string.error))
                                        }
                                    }
                                },
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
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
                                                text = stringResource(R.string.tags),
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
                                            guidePostData.tags.take(6).forEach { tag ->
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
                                                message = context.getString(R.string.guide_liked),
                                                duration = SnackbarDuration.Short
                                            )
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = if (guidePostData.isLiked) Icons.Default.ThumbUp else Icons.Default.ThumbUpOffAlt,
                                        contentDescription = stringResource(R.string.like),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.like_with_count, guidePostData.likes))
                                }

                                // Copy content button
                                FilledTonalButton(
                                    onClick = {
                                        val fullContent = buildString {
                                            append("${guidePostData.title}\n\n")
                                            if (renderedBlocks.isNotEmpty()) {
                                                append(renderedBlocks.joinToString("\n\n") { it.plainText() })
                                            } else {
                                                append(guidePostData.content)
                                            }
                                            append("\n\n")
                                            append(context.getString(R.string.share_hashtag_guide))
                                        }
                                    coroutineScope.launch {
                                        clipboard.setText(fullContent, label = "guide")
                                    }
                                        Toast.makeText(context, context.getString(R.string.guide_content_copied), Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = stringResource(R.string.copy),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.copy))
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
                                contentDescription = stringResource(R.string.error),
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.guide_not_found),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.guide_could_not_be_loaded),
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

@Composable
private fun GuideContentBlockItem(
    block: GuideContentBlock,
    onImageClick: (String) -> Unit,
    onVideoClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    when (block) {
        is GuideContentBlock.Heading -> {
            val textStyle = when (block.level) {
                1 -> MaterialTheme.typography.headlineSmall
                2 -> MaterialTheme.typography.titleLarge
                else -> MaterialTheme.typography.titleMedium
            }
            Text(
                text = block.text,
                style = textStyle,
                fontWeight = FontWeight.Bold,
                modifier = modifier.padding(top = 8.dp, bottom = 4.dp)
            )
        }

        is GuideContentBlock.Paragraph -> {
            Text(
                text = block.text,
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = 24.sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = modifier.padding(vertical = 6.dp)
            )
        }

        is GuideContentBlock.Image -> {
            Card(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clickable { onImageClick(block.url) },
                shape = RoundedCornerShape(12.dp)
            ) {
                Column {
                    SubcomposeAsyncImage(
                        model = fullGuideImageUrl(block.url),
                        contentDescription = block.alt ?: block.caption ?: "Guide image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        contentScale = ContentScale.Crop
                    ) {
                        when (painter.state) {
                            is AsyncImagePainter.State.Loading -> Box(
                                Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) { CircularProgressIndicator() }
                            is AsyncImagePainter.State.Error -> Box(
                                Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.BrokenImage,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                            else -> SubcomposeAsyncImageContent()
                        }
                    }
                    if (!block.caption.isNullOrBlank()) {
                        Text(
                            text = block.caption,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }

        is GuideContentBlock.Video -> {
            Card(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onVideoClick(block.url) }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.PlayCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = block.caption ?: "Open video",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Provider: ${block.provider}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        is GuideContentBlock.Callout -> {
            Card(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = block.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = block.text, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        is GuideContentBlock.OrderedList -> {
            Column(modifier = modifier.padding(vertical = 6.dp)) {
                block.items.forEachIndexed { index, item ->
                    Text(
                        text = "${index + 1}. $item",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }

        is GuideContentBlock.UnorderedList -> {
            Column(modifier = modifier.padding(vertical = 6.dp)) {
                block.items.forEach { item ->
                    Text(
                        text = "• $item",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }

        GuideContentBlock.Divider -> {
            HorizontalDivider(modifier = modifier.padding(vertical = 8.dp))
        }
    }
}

private fun GuideContentBlock.plainText(): String = when (this) {
    is GuideContentBlock.Heading -> text
    is GuideContentBlock.Paragraph -> text
    is GuideContentBlock.Image -> listOfNotNull(caption, url).joinToString(" ")
    is GuideContentBlock.Video -> listOfNotNull(caption, url).joinToString(" ")
    is GuideContentBlock.Callout -> "$title\n$text"
    is GuideContentBlock.OrderedList -> items.joinToString("\n") { "- $it" }
    is GuideContentBlock.UnorderedList -> items.joinToString("\n") { "- $it" }
    GuideContentBlock.Divider -> ""
}
