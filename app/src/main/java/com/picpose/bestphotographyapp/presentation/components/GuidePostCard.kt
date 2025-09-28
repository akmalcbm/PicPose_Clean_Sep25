package com.picpose.bestphotographyapp.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.picpose.bestphotographyapp.data.models.GuidePost

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
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isPressed) 12.dp else 6.dp
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Image Section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isCompact) 140.dp else 200.dp)
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
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.3f)
                                ),
                                startY = 0f,
                                endY = Float.POSITIVE_INFINITY
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
                    guidePost.category?.let { category ->
                        Surface(
                            color = Color(0xFF8B5CF6).copy(alpha = 0.9f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = category,
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
                                onClick = { onFavoriteClick(guidePost) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (guidePost.isFavorited == true) {
                                        Icons.Default.Favorite
                                    } else {
                                        Icons.Default.FavoriteBorder
                                    },
                                    contentDescription = if (guidePost.isFavorited == true) "Remove from favorites" else "Add to favorites",
                                    tint = if (guidePost.isFavorited == true) Color.Red else Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // View count badge (bottom right)
                guidePost.viewCount?.let { count ->
                    if (count > 0) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
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
                                        count >= 1000000 -> "${count / 1000000}M"
                                        count >= 1000 -> "${count / 1000}K"
                                        else -> count.toString()
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }

                // Guide Badge
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp),
                    color = Color(0xFF059669).copy(alpha = 0.9f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "GUIDE",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            // Content Section
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Title
                Text(
                    text = guidePost.title ?: "Untitled",
                    style = if (isCompact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = if (isCompact) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                    color = Color(0xFF1E293B)
                )

                // Description/Content preview
                if (!isCompact && !guidePost.description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = guidePost.description!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF64748B),
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        guidePost.authorName?.let { author ->
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = Color(0xFF64748B)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = author,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFF64748B),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        guidePost.createdAt?.let { date ->
                            if (guidePost.authorName != null) {
                                Text(
                                    text = " • $date",
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
                                    text = date,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }
                    }

                    // Reading time or difficulty
                    guidePost.readingTime?.let { time ->
                        Surface(
                            color = Color(0xFFF1F5F9),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "${time} min read",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF475569),
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // Tags (if compact mode)
                if (isCompact && !guidePost.tags.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(guidePost.tags!!.take(3)) { tag ->
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
            }
        }
    }
}

// Extension for LazyRow with tags
@Composable
private fun LazyRow(
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    androidx.compose.foundation.lazy.LazyRow(
        horizontalArrangement = horizontalArrangement,
        content = content
    )
}