package com.picpose.bestphotographyapp.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.picpose.bestphotographyapp.core.utils.displayFavorites
import com.picpose.bestphotographyapp.core.utils.displayLikes
import com.picpose.bestphotographyapp.core.utils.displayViews
import com.picpose.bestphotographyapp.core.utils.getBookmarkIconState
import com.picpose.bestphotographyapp.core.utils.getLikeIconState
import com.picpose.bestphotographyapp.data.database.entities.EngagementEntity
import com.picpose.bestphotographyapp.data.models.AIPrompt
import kotlinx.coroutines.launch

@Composable
fun AIPromptCardWithEffects(
    prompt: AIPrompt,
    localEngagement: EngagementEntity?,
    onClick: () -> Unit,
    onCopy: () -> Unit,
    showFavoriteIcon: Boolean = true,

    // REQUIRED CALLBACKS
    onLikeClick: (AIPrompt) -> Unit,
    onBookmarkClick: (AIPrompt) -> Unit,

    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    // ======================================================
    // 🔥 FIXED: Use Utils functions for ALL display values
    // ======================================================
    val displayLikes = remember(prompt, localEngagement) {
        prompt.displayLikes(localEngagement)
    }

    val displayFavorites = remember(prompt, localEngagement) {
        prompt.displayFavorites(localEngagement)
    }

    val displayViews = remember(prompt, localEngagement) {
        prompt.displayViews(localEngagement)
    }

    // Get icon states from single source
    val isLiked = remember(prompt, localEngagement) {
        prompt.getLikeIconState(localEngagement)
    }
    val isBookmarked = remember(prompt, localEngagement) {
        prompt.getBookmarkIconState(localEngagement)
    }

    // ======================================================
    // Animations
    // ======================================================
    val likeScale = remember { Animatable(1f) }
    val bookmarkScale = remember { Animatable(1f) }

    // ======================================================
    // ✅ FIXED: SIMPLIFIED HANDLERS - NO LOCAL CALCULATIONS
    // ======================================================
    fun handleLike() {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)

        // ✅ ONLY ANIMATION, NO CALCULATION
        coroutineScope.launch {
            likeScale.animateTo(1.3f, tween(130))
            likeScale.animateTo(1f, tween(160))
        }

        // ✅ Pass current prompt AS-IS
        // Let ViewModel handle everything through EngagementRepository
        onLikeClick(prompt)
    }

    fun handleBookmark() {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)

        // ✅ ONLY ANIMATION, NO CALCULATION
        coroutineScope.launch {
            bookmarkScale.animateTo(0.8f, tween(140))
            bookmarkScale.animateTo(1f, tween(180))
        }

        // ✅ Pass current prompt AS-IS
        onBookmarkClick(prompt)
    }

    // ======================================================
    // Flicker-proof Image Loader
    // ======================================================
    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(context)
            .data(prompt.imageUrl)
            .crossfade(true)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .build(),
        filterQuality = FilterQuality.None
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column {

            // IMAGE
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painter,
                    contentDescription = prompt.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                when (painter.state) {
                    is AsyncImagePainter.State.Loading -> CircularProgressIndicator()
                    is AsyncImagePainter.State.Error ->
                        Icon(Icons.Default.BrokenImage, null, tint = Color.Red, modifier = Modifier.size(40.dp))
                    else -> {}
                }
            }

            // CONTENT
            Column(
                Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Text(
                    prompt.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    prompt.shortPrompt ?: "",
                    style = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurfaceVariant),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(12.dp))

                // ACTION ROW
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {

                    // LEFT: Category + Popular
                    Row(verticalAlignment = Alignment.CenterVertically) {

                        prompt.category?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = colors.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }

                        Spacer(Modifier.width(12.dp))

                        if (prompt.isPopular) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Whatshot,
                                    null,
                                    tint = colors.error,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    "Popular",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.error
                                )
                            }
                        }
                    }

                    // RIGHT GROUP
                    Row(verticalAlignment = Alignment.CenterVertically) {

                        // ❤️ LIKE BUTTON
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable { handleLike() }
                                .padding(4.dp)
                        ) {
                            Icon(
                                imageVector = if (isLiked) Icons.Default.ThumbUp else Icons.Default.ThumbUpOffAlt,
                                contentDescription = if (isLiked) "Unlike" else "Like",
                                tint = if (isLiked) MaterialTheme.colorScheme.primary else colors.onSurfaceVariant,
                                modifier = Modifier
                                    .size(22.dp)
                                    .scale(likeScale.value)
                            )

                            Spacer(Modifier.width(4.dp))

                            Text("$displayLikes")
                        }

                        Spacer(Modifier.width(14.dp))

                        // 🔖 BOOKMARK BUTTON
                        Icon(
                            if (isBookmarked) Icons.Default.BookmarkAdded else Icons.Default.BookmarkBorder,
                            null,
                            tint = if (isBookmarked) colors.primary else colors.onSurfaceVariant,
                            modifier = Modifier
                                .size(22.dp)
                                .scale(bookmarkScale.value)
                                .clickable { handleBookmark() }
                        )

                        Spacer(Modifier.width(14.dp))

                        // 👁 VIEWS (Server only)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Visibility,
                                null,
                                tint = colors.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("$displayViews")
                        }
                    }
                }
            }
        }
    }
}