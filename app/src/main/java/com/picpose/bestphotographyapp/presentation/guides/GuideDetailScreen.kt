/**
 * ---
 * File: GuideDetailScreen.kt
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

package com.picpose.bestphotographyapp.presentation.guides

import android.content.Intent
import android.text.method.LinkMovementMethod
import android.widget.TextView
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.google.android.gms.ads.LoadAdError
import com.picpose.bestphotographyapp.R
import com.picpose.bestphotographyapp.components.common.PicPoseTopAppBar
import com.picpose.bestphotographyapp.data.remote.dto.GuideContentBlock
import com.picpose.bestphotographyapp.utils.MediaUrlResolver
import com.picpose.bestphotographyapp.components.ads.AdsConfigState
import com.picpose.bestphotographyapp.components.ads.AdsManager
import com.picpose.bestphotographyapp.components.ads.LargeNativeAdCard
import com.picpose.bestphotographyapp.components.ads.NativeAdController
import com.picpose.bestphotographyapp.components.ads.NativeAdUiState
import com.picpose.bestphotographyapp.presentation.guides.GuidePostViewModel
import kotlin.math.abs
import kotlinx.coroutines.launch
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private fun fullGuideImageUrl(path: String?): String? {
    return MediaUrlResolver.resolve(path)
}

private sealed interface GuideContentRow {
    data class BlockRow(
        val index: Int,
        val block: GuideContentBlock
    ) : GuideContentRow

    data class AdRow(
        val slotNumber: Int,
        val key: String
    ) : GuideContentRow
}

private data class GuideInlineAdSlot(
    val slotNumber: Int,
    val key: String,
    val anchorAfterIndex: Int
)

private fun contentBlockWords(block: GuideContentBlock): Int {
    val source = when (block) {
        is GuideContentBlock.Heading -> block.text
        is GuideContentBlock.Paragraph -> block.text
        is GuideContentBlock.Image -> listOfNotNull(block.caption, block.alt).joinToString(" ")
        is GuideContentBlock.Video -> block.caption.orEmpty()
        is GuideContentBlock.Callout -> "${block.title} ${block.text}"
        is GuideContentBlock.OrderedList -> block.items.joinToString(" ")
        is GuideContentBlock.UnorderedList -> block.items.joinToString(" ")
        GuideContentBlock.Divider -> ""
    }
    return source.trim().split(Regex("\\s+")).count { it.isNotBlank() }
}

private fun readableWordCount(
    post: com.picpose.bestphotographyapp.data.remote.dto.GuidePost,
    blocks: List<GuideContentBlock>
): Int {
    val plainLongHtml = HtmlCompat.fromHtml(
        post.longDescriptionHtml,
        HtmlCompat.FROM_HTML_MODE_LEGACY
    ).toString()
    val source = buildString {
        append(post.shortDescription)
        append('\n')
        append(plainLongHtml)
        append('\n')
        append(post.content)
        append('\n')
        append(blocks.joinToString("\n") {
            when (it) {
                is GuideContentBlock.Heading -> it.text
                is GuideContentBlock.Paragraph -> it.text
                is GuideContentBlock.Image -> listOfNotNull(it.caption, it.alt).joinToString(" ")
                is GuideContentBlock.Video -> it.caption.orEmpty()
                is GuideContentBlock.Callout -> "${it.title} ${it.text}"
                is GuideContentBlock.OrderedList -> it.items.joinToString(" ")
                is GuideContentBlock.UnorderedList -> it.items.joinToString(" ")
                GuideContentBlock.Divider -> ""
            }
        })
    }
    return source.split(Regex("\\s+")).count { it.isNotBlank() }
}

private fun isPreferredAdAnchor(block: GuideContentBlock): Boolean = when (block) {
    is GuideContentBlock.Paragraph,
    is GuideContentBlock.OrderedList,
    is GuideContentBlock.UnorderedList,
    is GuideContentBlock.Callout -> true
    else -> false
}

private fun computeGuideInlineAdSlots(
    guidePostId: String,
    blocks: List<GuideContentBlock>,
    totalWords: Int
): List<GuideInlineAdSlot> {
    if (guidePostId.isBlank()) return emptyList()
    val blockCount = blocks.size
    if (blockCount < 8 || totalWords < 600) return emptyList()
    val desiredCount = if (blockCount > 18 || totalWords > 1400) 2 else 1
    val targetPercents = if (desiredCount == 2) listOf(0.35f, 0.70f) else listOf(0.45f)
    val candidateIndices = (0 until blockCount).filter { idx ->
        idx >= 2 &&
            idx <= blockCount - 3 &&
            isPreferredAdAnchor(blocks[idx])
    }
    if (candidateIndices.isEmpty()) return emptyList()

    val blockWords = blocks.map(::contentBlockWords)
    val cumulativeWords = IntArray(blockWords.size)
    var running = 0
    blockWords.forEachIndexed { index, words ->
        running += words
        cumulativeWords[index] = running
    }

    val selectedAnchors = mutableListOf<Int>()
    targetPercents.forEachIndexed { slotIndex, pct ->
        val target = (blockCount * pct).toInt().coerceIn(2, blockCount - 3)
        val selected = candidateIndices
            .filter { candidate ->
                selectedAnchors.lastOrNull()?.let { prev ->
                    val blocksApart = candidate - prev
                    val wordsApart = cumulativeWords[candidate] - cumulativeWords[prev]
                    blocksApart >= 6 || wordsApart >= 400
                } ?: true
            }
            .minByOrNull { candidate -> abs(candidate - target) }
        if (selected != null) {
            selectedAnchors += selected
        } else if (slotIndex == 0) {
            return emptyList()
        }
    }

    return selectedAnchors.mapIndexed { idx, anchor ->
        GuideInlineAdSlot(
            slotNumber = idx + 1,
            key = "ad_${idx + 1}_$guidePostId",
            anchorAfterIndex = anchor
        )
    }
}

private fun buildGuideContentRows(
    blocks: List<GuideContentBlock>,
    inlineAdSlots: List<GuideInlineAdSlot>
): List<GuideContentRow> {
    if (blocks.isEmpty()) return emptyList()
    val slotsByAnchor = inlineAdSlots.associateBy { it.anchorAfterIndex }
    return buildList {
        blocks.forEachIndexed { index, block ->
            add(GuideContentRow.BlockRow(index = index, block = block))
            slotsByAnchor[index]?.let { slot ->
                add(GuideContentRow.AdRow(slotNumber = slot.slotNumber, key = slot.key))
            }
        }
    }
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
    val adsConfigState by AdsManager.configState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Dialog state: show full image
    var showImageDialog by remember { mutableStateOf(false) }
    var blockImageDialogUrl by remember { mutableStateOf<String?>(null) }

    val guidePostData = uiState.selectedGuidePost
        ?: remember(guidePostId, uiState.guidePosts) { viewModel.findGuidePostById(guidePostId) }
    val renderedBlocks = uiState.blocks
    val totalReadableWords = remember(guidePostData?.id, renderedBlocks) {
        guidePostData?.let { readableWordCount(it, renderedBlocks) } ?: 0
    }
    val inlineAdSlots = remember(guidePostData?.id, renderedBlocks, totalReadableWords) {
        computeGuideInlineAdSlots(
            guidePostId = guidePostData?.id.orEmpty(),
            blocks = renderedBlocks,
            totalWords = totalReadableWords
        )
    }
    val contentRows = remember(renderedBlocks, inlineAdSlots) {
        buildGuideContentRows(renderedBlocks, inlineAdSlots)
    }
    val adStates = remember { mutableStateMapOf<String, NativeAdUiState>() }
    val adControllers = remember { mutableStateMapOf<String, NativeAdController>() }

    LaunchedEffect(guidePostId) {
        viewModel.loadGuidePostById(guidePostId)
    }

    LaunchedEffect(guidePostData?.id, inlineAdSlots, adsConfigState) {
        if (adsConfigState !is AdsConfigState.Ready) return@LaunchedEffect

        val slotKeys = inlineAdSlots.map { it.key }.toSet()
        adControllers.keys.toList().forEach { key ->
            if (key !in slotKeys) {
                adControllers.remove(key)?.clear()
                adStates.remove(key)
            }
        }
        inlineAdSlots.forEach { slot ->
            val existingState = adStates[slot.key]
            if (existingState is NativeAdUiState.Loaded || existingState is NativeAdUiState.Loading) {
                return@forEach
            }
            adStates[slot.key] = NativeAdUiState.Loading
            val controller = adControllers.getOrPut(slot.key) {
                NativeAdController(placementKey = AdsManager.KEY_DETAIL_NATIVE)
            }
            controller.load(
                context = context,
                callbacks = object : NativeAdController.Callbacks {
                    override fun onLoaded(ad: com.google.android.gms.ads.nativead.NativeAd) {
                        adStates[slot.key] = NativeAdUiState.Loaded(ad)
                    }

                    override fun onFailed(error: LoadAdError) {
                        adStates[slot.key] = NativeAdUiState.Failed
                    }

                    override fun onUnavailable(reason: String) {
                        adStates[slot.key] = if (reason == "ADS_DISABLED") {
                            NativeAdUiState.Disabled
                        } else {
                            NativeAdUiState.Failed
                        }
                    }
                }
            )
        }
    }

    DisposableEffect(guidePostId) {
        onDispose {
            adControllers.values.forEach { it.clear() }
            adControllers.clear()
            adStates.clear()
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
        PicPoseTopAppBar(
            title = stringResource(R.string.photography_guide_title),
            onBack = onBack,
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
                                    .clip(
                                        RoundedCornerShape(
                                            bottomStart = 16.dp,
                                            bottomEnd = 16.dp
                                        )
                                    )
                                    .clickable { showImageDialog = true }
                            ) {
                                AdaptiveGuideImage(
                                    model = fullGuideImageUrl(guidePostData.imageUrl),
                                    contentDescription = guidePostData.title,
                                    modifier = Modifier.fillMaxWidth()
                                )

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

                                    if (guidePostData.shortDescription.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(
                                            text = guidePostData.shortDescription,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            lineHeight = 24.sp
                                        )
                                    }

                                    if (uiState.readMinutes > 0) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.Schedule,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "${uiState.readMinutes} min read",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    if (guidePostData.isFeatured) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        SuggestionChip(
                                            onClick = { },
                                            label = { Text(stringResource(R.string.featured)) }
                                        )
                                    }

                                    if (guidePostData.category.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(10.dp))
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
                                    }

                                    if (guidePostData.longDescriptionHtml.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(14.dp))
                                        Text(
                                            text = "Long Description",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        HtmlTextBlock(
                                            html = guidePostData.longDescriptionHtml,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }

                                    // Meta info
                                    Spacer(modifier = Modifier.height(14.dp))
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
                                        }

                                        // Engagement stats
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Default.Visibility,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "${uiState.displayViews}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Default.Favorite,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "${uiState.displayLikes}",
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
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    Icons.AutoMirrored.Filled.Article,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                /*Text(
                                    text = stringResource(R.string.guide_content),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )*/
                            }
                        }

                        itemsIndexed(
                            items = contentRows,
                            key = { _, row ->
                                when (row) {
                                    is GuideContentRow.BlockRow -> "block_${row.index}_${row.block::class.simpleName}"
                                    is GuideContentRow.AdRow -> row.key
                                }
                            }
                        ) { _, row ->
                            when (row) {
                                is GuideContentRow.BlockRow -> {
                                    GuideContentBlockItem(
                                        block = row.block,
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

                                is GuideContentRow.AdRow -> {
                                    when (val adState = adStates[row.key]) {
                                        is NativeAdUiState.Loaded -> {
                                            LargeNativeAdCard(
                                                nativeAd = adState.ad,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                            )
                                        }
                                        NativeAdUiState.Loading -> {
                                            LargeNativeAdCard(
                                                nativeAd = null,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                            )
                                        }
                                        NativeAdUiState.Disabled,
                                        NativeAdUiState.Failed,
                                        null -> Unit
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
                                        val wasLiked = uiState.isLiked
                                        viewModel.toggleGuidePostLike(guidePostData.id)
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(
                                                message = if (wasLiked) {
                                                    context.getString(R.string.guide_unliked)
                                                } else {
                                                    context.getString(R.string.guide_liked)
                                                },
                                                duration = SnackbarDuration.Short
                                            )
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = if (uiState.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = stringResource(R.string.like),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("${uiState.displayLikes}")
                                }

                                // Share button
                                FilledTonalButton(
                                    onClick = {
                                        viewModel.shareGuidePost(context, guidePostData)
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        Icons.Default.Share,
                                        contentDescription = stringResource(R.string.share_guide_button),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.share_guide_button))
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
                                text = uiState.error ?: stringResource(R.string.guide_could_not_be_loaded),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { viewModel.loadGuidePostById(guidePostId) }) {
                                Text(stringResource(R.string.retry))
                            }
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
private fun HtmlTextBlock(
    html: String,
    modifier: Modifier = Modifier
) {
    val color = MaterialTheme.colorScheme.onSurface.toArgb()
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()
    val isPreview = LocalInspectionMode.current
    AndroidView(
        factory = { context ->
            TextView(context).apply {
                textSize = 16f
                setLineSpacing(0f, 1.4f)
                movementMethod = LinkMovementMethod.getInstance()
                linksClickable = true
            }
        },
        update = { textView ->
            textView.setTextColor(color)
            textView.setLinkTextColor(linkColor)
            val text = if (isPreview) html else HtmlCompat.fromHtml(
                html,
                HtmlCompat.FROM_HTML_MODE_LEGACY
            )
            textView.text = text
        },
        modifier = modifier
    )
}

@Composable
private fun AdaptiveGuideImage(
    model: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    var aspectRatio by remember(model) { mutableFloatStateOf(4f / 3f) }

    SubcomposeAsyncImage(
        model = model,
        contentDescription = contentDescription,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio),
        contentScale = ContentScale.Fit
    ) {
        when (val state = painter.state) {
            is AsyncImagePainter.State.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is AsyncImagePainter.State.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
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
                val width = drawable.intrinsicWidth
                val height = drawable.intrinsicHeight
                if (width > 0 && height > 0) {
                    val nextRatio = width.toFloat() / height.toFloat()
                    if (kotlin.math.abs(nextRatio - aspectRatio) > 0.01f) {
                        aspectRatio = nextRatio
                    }
                }
                SubcomposeAsyncImageContent()
            }

            else -> SubcomposeAsyncImageContent()
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
                    AdaptiveGuideImage(
                        model = fullGuideImageUrl(block.url),
                        contentDescription = block.alt ?: block.caption ?: "Guide image",
                        modifier = Modifier.fillMaxWidth()
                    )
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
