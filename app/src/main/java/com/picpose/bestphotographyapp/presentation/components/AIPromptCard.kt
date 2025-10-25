package com.picpose.bestphotographyapp.presentation.components

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.picpose.bestphotographyapp.data.models.AIPrompt
import com.picpose.bestphotographyapp.presentation.viewmodels.AIPromptViewModel

@Composable
fun AIPromptCard(
    prompt: AIPrompt,
    onClick: () -> Unit,
    onCopy: () -> Unit,
    onFavoriteClick: ((AIPrompt) -> Unit)? = null,
    isCompact: Boolean = false,
    showFavoriteIcon: Boolean = true,
    modifier: Modifier = Modifier,
    viewModel: AIPromptViewModel? = null // ✅ Added for background updates
) {
    val colors = MaterialTheme.colorScheme
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                onClick()
                // ✅ Increment view count in background
                prompt.id.toIntOrNull()?.let { id ->
                    viewModel?.incrementViewCount(id)
                }
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface)
        ) {

            // 🖼️ Image section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isCompact) 140.dp else 240.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            ) {
                val painter = rememberAsyncImagePainter(
                    model = ImageRequest.Builder(context)
                        .data(prompt.imageUrl)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .crossfade(true)
                        .build()
                )

                Image(
                    painter = painter,
                    contentDescription = prompt.title ?: "Prompt Image",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                when (painter.state) {
                    is AsyncImagePainter.State.Loading -> {
                        Box(
                            Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = colors.primary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    is AsyncImagePainter.State.Error -> {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(colors.errorContainer.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.BrokenImage,
                                contentDescription = null,
                                tint = colors.error,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }

                    else -> Unit
                }
            }

            // 📝 Content Section (List mode only)
            if (!isCompact) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    // 🔹 Title
                    Text(
                        text = prompt.title ?: "",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // 🔹 Subtitle / shortPrompt
                    Text(
                        text = prompt.shortPrompt ?: "",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = colors.onSurfaceVariant
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // 🔹 Bottom Row (Category, Popular, Heart, View Button)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left side: Category + Popular + Hearts
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Category
                            if (!prompt.category.isNullOrBlank()) {
                                Text(
                                    text = prompt.category ?: "",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = colors.primary,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Popular
                            if (prompt.isPopular) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Whatshot,
                                        contentDescription = "Popular",
                                        tint = colors.error,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Popular",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = colors.error,
                                            fontWeight = FontWeight.Medium
                                        )
                                    )
                                }
                            }

                            // Likes (clickable to like)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable {
                                    onFavoriteClick?.invoke(prompt)
                                    // ✅ Increment like count only when liked
                                    prompt.id.toIntOrNull()?.let { id ->
                                        viewModel?.incrementLikeCount(id)
                                    }
                                }
                            ) {
                                Icon(
                                    if (prompt.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Likes",
                                    tint = if (prompt.isFavorite)
                                        colors.error
                                    else
                                        colors.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "${prompt.likes}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = colors.onSurfaceVariant
                                    )
                                )
                            }
                        }

                        // 👁️ Views counter (right of likes)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Visibility, // 👁️ Eye icon
                                contentDescription = "Views",
                                tint = colors.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "${prompt.views ?: 0}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = colors.onSurfaceVariant
                                )
                            )
                        }


                        // Right side: View button + Copy button inline
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(prompt.fullPrompt ?: ""))
                                    Toast.makeText(context, "Prompt copied!", Toast.LENGTH_SHORT).show()
                                    onCopy()
                                    // ✅ Increment copy count
                                    prompt.id.toIntOrNull()?.let { id ->
                                        viewModel?.incrementCopyCount(id)
                                    }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = "Copy",
                                    tint = colors.primary
                                )
                            }

                            Button(
                                onClick = {
                                    onClick()
                                    // ✅ Increment view count again for explicit button
                                    prompt.id.toIntOrNull()?.let { id ->
                                        viewModel?.incrementViewCount(id)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.primary,
                                    contentColor = colors.onPrimary
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text(
                                    text = "View",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
