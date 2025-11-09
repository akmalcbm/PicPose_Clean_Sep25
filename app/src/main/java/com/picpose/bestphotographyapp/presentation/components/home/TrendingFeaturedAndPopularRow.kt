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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.picpose.bestphotographyapp.data.models.Post

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun TrendingFeaturedAndPopularRow(
    trendingPosts: List<Post>,
    featuredPosts: List<Post>,
    popularPosts: List<Post>,
    onPostClick: (Post) -> Unit,
    onLikeClick: (Post) -> Unit,
    onShareClick: (Post) -> Unit,
    onViewAllClick: (String) -> Unit
) {
    var selectedTab by remember { mutableStateOf("Trending") }

    // ✅ Now includes Popular
    val tabs = listOf("Trending", "Featured", "Popular")

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
                ) { tab -> tabPositions[tabs.indexOf(tab)].left }
                val indicatorRight by transition.animateDp(
                    transitionSpec = { spring() },
                    label = "indicatorRight"
                ) { tab -> tabPositions[tabs.indexOf(tab)].right }

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
                                color = if (isSelected)
                                    MaterialTheme.colorScheme.primary
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
            // ✅ Choose posts based on tab
            val posts = when (tab) {
                "Trending" -> trendingPosts
                "Featured" -> featuredPosts
                "Popular" -> popularPosts
                else -> emptyList()
            }

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                if (posts.isEmpty()) {
                    item {
                        Text(
                            text = "No posts available",
                            modifier = Modifier.padding(24.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(posts.take(10)) { post -> // show 10 max
                        TrendingFeaturedAndPopularCard(
                            post = post,
                            categoryLabel = tab,
                            onClick = { onPostClick(post) },
                            onLikeClick = { onLikeClick(post) },
                            onShareClick = { onShareClick(post) }
                        )
                    }

                    // ✅ Improved "View All" card with gradient and icon
                    item {
                        EnhancedViewAllCard(
                            title = tab, // Trending, Featured, or Popular
                            onClick = { onViewAllClick(tab) }
                        )
                    }
                }
            }

        }
    }
}


@Composable
fun TrendingFeaturedAndPopularCard(
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
fun EnhancedViewAllCard(
    title: String,
    onClick: () -> Unit
) {
    // 🌈 Dynamic gradient per category
    val (gradient, icon) = when (title) {
        "Trending" -> Brush.linearGradient(
            listOf(Color(0xFF43CEA2), Color(0xFF037C75))
        ) to Icons.AutoMirrored.Filled.TrendingUp

        "Featured" -> Brush.linearGradient(
            listOf(Color(0xFFFFD200), Color(0xFFFFA000))
        ) to Icons.Filled.Star

        "Popular" -> Brush.linearGradient(
            listOf(Color(0xFFFF5F6D), Color(0xFFE81E63))
        ) to Icons.Filled.Favorite

        else -> Brush.linearGradient(
            listOf(Color(0xFF8E2DE2), Color(0xFF4A00E0))
        ) to Icons.AutoMirrored.Filled.ArrowForward
    }

    // 💫 Hover animation effect (scale pulse)
    val scale = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 1.03f,
            animationSpec = infiniteRepeatable(
                animation = tween(2500, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            )
        )
    }

    Card(
        modifier = Modifier
            .width(180.dp)
            .height(240.dp)
            .padding(4.dp)
            .graphicsLayer(
                scaleX = scale.value,
                scaleY = scale.value,
                shadowElevation = 12f,
                shape = RoundedCornerShape(20.dp),
                clip = true
            )
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(brush = gradient)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = "View All Icon",
                    tint = Color.White,
                    modifier = Modifier.size(44.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "View All $title",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                Divider(
                    color = Color.White.copy(alpha = 0.5f),
                    thickness = 1.dp,
                    modifier = Modifier.width(80.dp)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Explore more in $title",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White.copy(alpha = 0.85f)
                    ),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}




@Composable
fun CategoryChip(label: String) {
    val (bgColor, icon) = when (label) {
        "Trending" -> Color(0xFF00ACC1) to Icons.AutoMirrored.Filled.TrendingUp
        "Featured" -> Color(0xFFFFC107) to Icons.Filled.Star
        "Popular" -> Color(0xFFE91E63) to Icons.Filled.Favorite
        else -> MaterialTheme.colorScheme.primary to Icons.Filled.Star
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
