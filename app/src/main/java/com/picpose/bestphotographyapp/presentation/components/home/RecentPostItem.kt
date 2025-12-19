package com.picpose.bestphotographyapp.presentation.components.home

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.picpose.bestphotographyapp.data.models.Post
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun RecentPostItem(
    post: Post,
    onClick: () -> Unit,
    onLikeClick: () -> Unit,
    onShareClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showHeartBurst by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val heartScale by animateFloatAsState(
        targetValue = if (showHeartBurst) 1.4f else 1f,
        animationSpec = spring(dampingRatio = 0.4f),
        label = "heartScale"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            // 🖼 Image
            AsyncImage(
                model = post.image,
                contentDescription = post.title,
                modifier = Modifier
                    .size(70.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {

                Text(
                    text = post.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1
                )

                if (post.description.isNotBlank()) {
                    Text(
                        text = post.description,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2
                    )
                }

                Spacer(Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {

                    // ❤️ LIKE
                    Box(contentAlignment = Alignment.Center) {
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showHeartBurst = true
                                onLikeClick()

                                scope.launch {
                                    delay(400)
                                    showHeartBurst = false
                                }
                            },
                            modifier = Modifier.size(30.dp)
                        ) {
                            AnimatedContent(
                                targetState = post.isLiked,
                                transitionSpec = {
                                    scaleIn(spring(dampingRatio = 0.5f)) togetherWith
                                            scaleOut(spring(dampingRatio = 0.7f))
                                },
                                label = "likeAnim"
                            ) { liked ->
                                if (liked) {
                                    Icon(
                                        Icons.Filled.Favorite,
                                        contentDescription = "Liked",
                                        tint = Color(0xFFE91E63),
                                        modifier = Modifier.scale(heartScale)
                                    )
                                } else {
                                    Icon(
                                        Icons.Outlined.FavoriteBorder,
                                        contentDescription = "Like",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // 💥 HEART BURST (🔥 FIXED)
                        androidx.compose.animation.AnimatedVisibility(
                            visible = showHeartBurst,
                            enter = scaleIn(
                                animationSpec = spring(dampingRatio = 0.3f)
                            ) + fadeIn(),
                            exit = fadeOut(animationSpec = tween(300))
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFFC1CC).copy(alpha = 0.4f))
                            )
                        }
                    }

                    // ⭐ Favorites
                    IconWithText(
                        icon = Icons.Default.Star,
                        text = post.favorites.toString(),
                        tint = Color(0xFFFFC107)
                    )

                    // 👁 Views
                    IconWithText(
                        icon = Icons.Default.Visibility,
                        text = post.views.toString(),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.weight(1f))

                    // 📤 Share
                    IconButton(onClick = onShareClick) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = "Share"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IconWithText(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    tint: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelSmall)
    }
}
