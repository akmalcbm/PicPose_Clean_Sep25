/**
 * ---
 * File: AIPromptCardWithEffects.kt
 * Layer: Presentation (UI)
 * Project: PicPose
 *
 * Purpose:
 * Contains reusable Compose UI building blocks shared across screens.
 *
 * Interactions:
 * Reads immutable state from ViewModels or callbacks and emits user events back up to navigation or state owners.
 *
 * Data Flow:
 * Feature-specific flow; see adjacent ViewModels and repositories for the full path.
 *
 * Maintainer Notes:
 * - Prefer stateless composables and keep side effects inside well-scoped effect APIs.
 * - Document new navigation arguments and remember that recomposition can re-run this code frequently.
 * ---
 */

package com.picpose.bestphotographyapp.components.common

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.picpose.bestphotographyapp.R
import com.picpose.bestphotographyapp.utils.displayFavorites
import com.picpose.bestphotographyapp.utils.displayLikes
import com.picpose.bestphotographyapp.utils.displayViews
import com.picpose.bestphotographyapp.utils.getBookmarkIconState
import com.picpose.bestphotographyapp.utils.getLikeIconState
import com.picpose.bestphotographyapp.data.local.database.entity.EngagementEntity
import com.picpose.bestphotographyapp.data.remote.dto.AIPrompt
import com.picpose.bestphotographyapp.presentation.home.components.premiumListCardBorder
import com.picpose.bestphotographyapp.presentation.home.components.premiumListCardColors
import com.picpose.bestphotographyapp.presentation.home.components.premiumListCardElevation
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
        elevation = premiumListCardElevation(),
        colors = premiumListCardColors(),
        border = premiumListCardBorder()
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

                Spacer(Modifier.height(8.dp))

                PicPoseMetadataContainer(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val primaryTag = prompt.category?.takeIf { it.isNotBlank() }
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (primaryTag != null) {
                                PicPoseMetaChip(
                                    text = primaryTag,
                                    modifier = Modifier.widthIn(max = 92.dp),
                                )
                            } else if (prompt.isPopular) {
                                PicPoseMetaChip(
                                    text = stringResource(R.string.popular),
                                    modifier = Modifier.widthIn(max = 86.dp),
                                    containerColor = colors.tertiaryContainer.copy(alpha = 0.46f),
                                    contentColor = colors.onTertiaryContainer,
                                )
                            }

                            PicPoseStatChip(
                                icon = Icons.Default.Visibility,
                                value = displayViews,
                                compact = true,
                            )
                            PicPoseStatChip(
                                icon = Icons.Default.ThumbUp,
                                value = displayLikes,
                                compact = true,
                            )
                            if (showFavoriteIcon) {
                                PicPoseStatChip(
                                    icon = Icons.Default.BookmarkAdded,
                                    value = displayFavorites,
                                    compact = true,
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PicPoseActionIconButton(
                                icon = if (isLiked) Icons.Default.ThumbUp else Icons.Default.ThumbUpOffAlt,
                                contentDescription = if (isLiked) stringResource(R.string.unlike) else stringResource(R.string.like),
                                onClick = { handleLike() },
                                active = isLiked,
                                compact = true,
                                modifier = Modifier.scale(likeScale.value),
                            )
                            if (showFavoriteIcon) {
                                PicPoseActionIconButton(
                                    icon = if (isBookmarked) Icons.Default.BookmarkAdded else Icons.Default.BookmarkBorder,
                                    contentDescription = if (isBookmarked) {
                                        stringResource(R.string.remove_from_favorites)
                                    } else {
                                        stringResource(R.string.add_to_favorites)
                                    },
                                    onClick = { handleBookmark() },
                                    active = isBookmarked,
                                    compact = true,
                                    modifier = Modifier.scale(bookmarkScale.value),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
