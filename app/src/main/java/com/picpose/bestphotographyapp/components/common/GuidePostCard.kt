/**
 * ---
 * File: GuidePostCard.kt
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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.picpose.bestphotographyapp.R
import com.picpose.bestphotographyapp.data.remote.dto.GuidePost
import com.picpose.bestphotographyapp.presentation.home.components.premiumListCardBorder
import com.picpose.bestphotographyapp.presentation.home.components.premiumListCardColors

@Composable
fun GuidePostCard(
    guidePost: GuidePost,
    onClick: () -> Unit,
    onFavoriteClick: ((GuidePost) -> Unit)? = null,
    isCompact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isPressed by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isPressed) 12.dp else 6.dp),
        colors = premiumListCardColors(),
        border = premiumListCardBorder()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {

            // Image Section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isCompact) 140.dp else 200.dp)
                    .height(if (isCompact) 160.dp else 240.dp) // Increased sizes for better visibility
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(guidePost.imageUrl ?: guidePost.thumbnailUrl)
                        .crossfade(true)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .build(),
                    contentDescription = guidePost.title,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                    contentScale = ContentScale.Crop
                )

                // Gradient overlay
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

                // Top badges
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    // Category badge
                    if (guidePost.category.isNotBlank()) {
                        Surface(
                            color = Color(0xFF8B5CF6).copy(alpha = 0.9f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = guidePost.category,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Favorite button
                    onFavoriteClick?.let {
                        Surface(
                            modifier = Modifier.size(36.dp),
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.5f)
                        ) {
                            IconButton(
                                onClick = { onFavoriteClick.invoke(guidePost) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (guidePost.isFavorited) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = if (guidePost.isFavorited) stringResource(R.string.remove_from_favorites) else stringResource(R.string.add_to_favorites),
                                    tint = if (guidePost.isFavorited) Color.Red else Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // View count badge (bottom right)
                if (guidePost.viewCount > 0) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd) // valid in BoxScope
                            .padding(12.dp),
                        color = Color.Black.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = when {
                                    guidePost.viewCount >= 1_000_000 -> "${guidePost.viewCount / 1_000_000}M"
                                    guidePost.viewCount >= 1_000 -> "${guidePost.viewCount / 1_000}K"
                                    else -> guidePost.viewCount.toString()
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                // Guide Badge (bottom left)
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart) // valid in BoxScope
                        .padding(12.dp),
                    color = Color(0xFF059669).copy(alpha = 0.9f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.guide_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }
                }
            } // end Box

            // Content Section
            Column(modifier = Modifier.padding(16.dp)) {

                // Title
                Text(
                    text = guidePost.title.ifBlank { stringResource(R.string.untitled) },
                    style = if (isCompact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = if (isCompact) 1 else 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Description/Content preview
                if (!isCompact && guidePost.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = guidePost.description,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Meta information
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Author and date
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (guidePost.authorName.isNotBlank()) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = Color(0xFF64748B)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = guidePost.authorName,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFF64748B),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (guidePost.createdAt.isNotBlank()) {
                            if (guidePost.authorName.isNotBlank()) {
                                Text(
                                    text = stringResource(R.string.created_at_with_bullet, guidePost.createdAt),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFF64748B)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = Color(0xFF64748B)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = guidePost.createdAt,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }
                    }

                    // Reading time or difficulty
                    if (guidePost.readingTime > 0) {
                        Surface(
                            color = Color(0xFFF1F5F9),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.read_time_with_minutes, guidePost.readingTime),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF475569),
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // Tags (if compact mode)
                if (isCompact && guidePost.tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(guidePost.tags.take(3)) { tag ->
                            Surface(
                                color = Color(0xFFEEF2FF),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "#$tag",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF4F46E5),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            } // end Column (content)
        } // end Column (root)
    } // end Card
}
