package com.picpose.bestphotographyapp.presentation.components.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.picpose.bestphotographyapp.data.models.Post

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun TrendingFeaturedRow(
    trendingPosts: List<Post>,
    featuredPosts: List<Post>,
    onPostClick: (Post) -> Unit,
    onLikeClick: (Post) -> Unit,
    onShareClick: (Post) -> Unit
) {
    var selectedTab by remember { mutableStateOf("Trending") }
    val tabs = listOf("Trending", "Featured")

    Column {
        // 🌈 Animated Tab Row
        TabRow(
            selectedTabIndex = tabs.indexOf(selectedTab),
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            indicator = { tabPositions ->
                val transition = updateTransition(selectedTab, label = "TabIndicator")
                val indicatorLeft by transition.animateDp(
                    transitionSpec = { spring() },
                    label = "indicatorLeft"
                ) { tab ->
                    tabPositions[tabs.indexOf(tab)].left
                }
                val indicatorRight by transition.animateDp(
                    transitionSpec = { spring() },
                    label = "indicatorRight"
                ) { tab ->
                    tabPositions[tabs.indexOf(tab)].right
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .wrapContentSize(Alignment.BottomStart)
                        .offset(x = indicatorLeft)
                        .width(indicatorRight - indicatorLeft)
                        .padding(horizontal = 16.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        ) {
            tabs.forEach { tab ->
                val isSelected = selectedTab == tab
                Tab(
                    selected = isSelected,
                    onClick = { selectedTab = tab },
                    text = {
                        Text(
                            text = tab,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                )
            }
        }

        // 🔄 Animated Post Content Switch
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                (fadeIn(tween(300)) + slideInHorizontally(initialOffsetX = { it / 2 })) togetherWith
                        (fadeOut(tween(300)) + slideOutHorizontally(targetOffsetX = { -it / 2 }))
            },
            label = "TrendingFeaturedTransition"
        ) { tab ->
            val posts = if (tab == "Trending") trendingPosts else featuredPosts

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                items(posts) { post ->
                    TrendingFeaturedCard(
                        post = post,
                        categoryLabel = tab,
                        onClick = { onPostClick(post) },
                        onLikeClick = { onLikeClick(post) },
                        onShareClick = { onShareClick(post) }
                    )
                }
            }
        }
    }
}

@Composable
fun TrendingFeaturedCard(
    post: Post,
    categoryLabel: String,
    onClick: () -> Unit,
    onLikeClick: () -> Unit,
    onShareClick: () -> Unit
) {
    var isLiked by remember { mutableStateOf(false) }
    val likeScale by animateFloatAsState(if (isLiked) 1.1f else 1f, label = "likeScale")

    Card(
        modifier = Modifier
            .width(220.dp)
            .graphicsLayer {
                scaleX = likeScale
                scaleY = likeScale
            }
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box {
            AnimatedPanImage(post.image, post.title)

            // 🌈 Gradient overlay for better text visibility
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                        )
                    )
            )
        }

        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = post.title,
                style = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = post.description.take(60) + "...",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                // 🏷️ Category Pill with icon
                CategoryChip(label = categoryLabel)

                Row {
                    IconButton(onClick = {
                        isLiked = !isLiked
                        onLikeClick()
                    }) {
                        Icon(
                            imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "Like",
                            tint = if (isLiked) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onShareClick) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = "Share",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryChip(label: String) {
    val (bgColor, icon) = when (label) {
        "Trending" -> Color(0xFF00ACC1) to Icons.AutoMirrored.Filled.TrendingUp
        else -> Color(0xFFFFC107) to Icons.Filled.Star
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bgColor.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = bgColor, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = bgColor,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun AnimatedPanImage(imageUrl: String?, title: String?) {
    val infiniteTransition = rememberInfiniteTransition(label = "panAnim")
    val offsetY by infiniteTransition.animateFloat(
        initialValue = -25f,
        targetValue = 25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offsetAnim"
    )

    Box(
        modifier = Modifier
            .height(160.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
        contentAlignment = Alignment.Center
    ) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(imageUrl)
                .crossfade(true)
                .build(),
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    translationY = offsetY
                    scaleX = 1.1f
                    scaleY = 1.1f
                },
            loading = {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                }
            }
        )
    }
}
