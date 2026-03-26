/**
 * ---
 * File: RecentPostItem.kt
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

package com.picpose.bestphotographyapp.presentation.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.ThumbUpOffAlt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.picpose.bestphotographyapp.components.common.PicPoseActionIconButton
import com.picpose.bestphotographyapp.components.common.PicPoseMetadataContainer
import com.picpose.bestphotographyapp.components.common.PicPoseStatChip
import com.picpose.bestphotographyapp.data.local.database.entity.EngagementEntity
import com.picpose.bestphotographyapp.data.remote.dto.Post
import com.picpose.bestphotographyapp.R
import com.picpose.bestphotographyapp.utils.displayFavorites
import com.picpose.bestphotographyapp.utils.displayViews
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent

@Composable
fun RecentPostItem(
    post: Post,
    localEngagement: EngagementEntity?,
    onClick: () -> Unit,
    onLikeClick: () -> Unit,
    onShareClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    // 🔹 ONLY FOR ICON STATE
    val isLiked = localEngagement?.isLiked == true

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        elevation = premiumListCardElevation(),
        colors = premiumListCardColors(),
        border = premiumListCardBorder()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RecentPostThumbnail(
                imageUrl = post.image,
                title = post.title,
            )

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = post.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1
                )

                if (post.description.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = post.description,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        maxLines = 1
                    )
                }

                Spacer(Modifier.height(6.dp))

                PicPoseMetadataContainer(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PicPoseStatChip(
                                icon = Icons.Default.Visibility,
                                value = post.displayViews(localEngagement),
                                compact = true,
                            )
                            PicPoseStatChip(
                                icon = Icons.Default.Bookmarks,
                                value = post.displayFavorites(localEngagement),
                                compact = true,
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                PicPoseActionIconButton(
                                    icon = if (isLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUpOffAlt,
                                    contentDescription = stringResource(R.string.like),
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onLikeClick()
                                    },
                                    active = isLiked,
                                    compact = true,
                                )
                                Text(
                                    text = post.likes.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isLiked) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                            PicPoseActionIconButton(
                                icon = Icons.Default.Share,
                                contentDescription = stringResource(R.string.share),
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onShareClick()
                                },
                                compact = true,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentPostThumbnail(
    imageUrl: String,
    title: String,
) {
    val thumbnailShape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .size(width = 96.dp, height = 96.dp)
            .clip(thumbnailShape),
    ) {
        SubcomposeAsyncImage(
            model = imageUrl,
            contentDescription = title,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentScale = ContentScale.Crop,
        ) {
            when (painter.state) {
                is AsyncImagePainter.State.Loading,
                is AsyncImagePainter.State.Empty -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
                is AsyncImagePainter.State.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.BrokenImage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                else -> SubcomposeAsyncImageContent()
            }
        }
    }
}
