package com.picpose.bestphotographyapp.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.picpose.bestphotographyapp.data.database.entities.EngagementEntity
import com.picpose.bestphotographyapp.data.models.AIPrompt
import com.picpose.bestphotographyapp.core.utils.displayLikes
import com.picpose.bestphotographyapp.core.utils.displayViews

@Composable
fun AIPromptCard(
    prompt: AIPrompt,
    localEngagement: EngagementEntity?, // 🔥 NEW (STEP-4)

    onClick: () -> Unit,
    onCopy: () -> Unit,

    // 👍 LIKE — ID based
    onLikeClick: (String) -> Unit,

    // ⭐ FAVORITE — ID based
    onFavoriteClick: (String) -> Unit,

    isCompact: Boolean = false,
    showFavoriteIcon: Boolean = true,
    modifier: Modifier = Modifier
) {

    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme

    val isBookmarked =
        localEngagement?.isFavorited ?: prompt.isFavouriteBookmarked


    // ------------------------------------------------------------
    // Prevent image flicker on recomposition
    // ------------------------------------------------------------
    val fixedImageUrl = remember(prompt.imageUrl) { prompt.imageUrl }

    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(context)
            .data(fixedImageUrl)
            .crossfade(true)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .allowHardware(true)
            .build(),
        filterQuality = FilterQuality.None
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface)
    ) {

        Column {

            // ================= IMAGE =================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            ) {

                Image(
                    painter = painter,
                    contentDescription = prompt.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                when (painter.state) {
                    is AsyncImagePainter.State.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                strokeWidth = 3.dp
                            )
                        }
                    }

                    is AsyncImagePainter.State.Error -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.BrokenImage,
                                contentDescription = "Image error",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }

                    else -> Unit
                }
            }

            // ================= CONTENT =================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {

                Text(
                    text = prompt.title ?: "",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = prompt.shortPrompt ?: "",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = colors.onSurfaceVariant
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(12.dp))

                // ================= BOTTOM ROW =================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {

                    // ---------- LEFT ----------
                    Row(verticalAlignment = Alignment.CenterVertically) {

                        if (!prompt.category.isNullOrBlank()) {
                            Text(
                                text = prompt.category!!,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = colors.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        if (prompt.isPopular) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Whatshot,
                                    contentDescription = null,
                                    tint = colors.error,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Popular",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.error
                                )
                            }
                        }
                    }

                    // ---------- RIGHT ----------
                    Row(verticalAlignment = Alignment.CenterVertically) {

                        // 👍 LIKE
                        LikeButton(
                            isLiked = localEngagement?.isLiked ?: prompt.isLiked,
                            likeCount = prompt.displayLikes(localEngagement),
                            onLikeClick = {
                                prompt.id?.let { id ->
                                    onLikeClick(id)
                                }
                            }
                        )


                        Spacer(modifier = Modifier.width(16.dp))

                        // 👁 VIEWS (🔥 SINGLE SOURCE OF TRUTH)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = "Views",
                                tint = colors.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = prompt.displayViews(localEngagement).toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSurfaceVariant
                            )
                        }

                        if (showFavoriteIcon) {
                            Spacer(modifier = Modifier.width(12.dp))

                            // ⭐ FAVORITE
                            Icon(
                                imageVector =
                                    if (isBookmarked)
                                        Icons.Default.BookmarkAdded
                                    else
                                        Icons.Default.BookmarkBorder,
                                contentDescription = "Favorite",
                                tint =
                                    if (isBookmarked)
                                        Color(0xFFFFC107)
                                    else
                                        colors.onSurfaceVariant,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable {
                                        prompt.id?.let { id ->
                                            onFavoriteClick(id)
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
