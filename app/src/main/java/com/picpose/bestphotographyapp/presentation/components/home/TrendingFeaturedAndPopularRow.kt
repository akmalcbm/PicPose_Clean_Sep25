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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.picpose.bestphotographyapp.data.models.Post
import com.picpose.bestphotographyapp.R

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun TrendingFeaturedAndPopularRow(
    trendingPosts: List<Post>,
    featuredPosts: List<Post>,
    popularPosts: List<Post>,
    onPostClick: (Post) -> Unit,
    //onLikeClick: (Post) -> Unit,
    onShareClick: (Post) -> Unit,
    onViewAllClick: (String) -> Unit
) {
    val tabTrending = stringResource(R.string.trending)
    val tabFeatured = stringResource(R.string.featured)
    val tabPopular = stringResource(R.string.popular)

    var selectedTab by remember { mutableStateOf(tabTrending) }
    val tabs = listOf(tabTrending, tabFeatured, tabPopular)

    Column {

        // 🔹 Tabs
        PrimaryTabRow(
            selectedTabIndex = tabs.indexOf(selectedTab),
            containerColor = Color.Transparent,
            indicator = {
                TabRowDefaults.PrimaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(
                        selectedTabIndex = tabs.indexOf(selectedTab),
                        matchContentSize = true
                    ),
                    width = Dp.Unspecified,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        ) {
            tabs.forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    text = {
                        Text(
                            text = tab,
                            fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        // 🔹 Content
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                fadeIn(tween(300)) togetherWith fadeOut(tween(300))
            },
            label = "tabContent"
        ) { tab ->

            val posts = when (tab) {
                tabTrending -> trendingPosts
                tabFeatured -> featuredPosts
                tabPopular -> popularPosts
                else -> emptyList()
            }

            LazyRow(
                modifier = Modifier.padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {

                if (posts.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.no_posts_available),
                            modifier = Modifier.padding(24.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {

                    items(posts.take(10)) { post ->
                        TrendingFeaturedAndPopularCard(
                            post = post,
                            categoryLabel = tab,
                            onClick = { onPostClick(post) },
                            //onLikeClick = { onLikeClick(post) },
                            onShareClick = { onShareClick(post) }
                        )
                    }

                    item {
                        EnhancedViewAllCard(
                            title = tab,
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
    //onLikeClick: () -> Unit,
    onShareClick: () -> Unit
) {

    Card(
        modifier = Modifier
            .width(220.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {

        Column {

            AnimatedPanImage(post.image, post.title)

            Column(modifier = Modifier.padding(16.dp)) {

                Text(
                    text = post.title,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = post.description.take(60),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    CategoryChip(categoryLabel)

                    // 👁 Views
                    Icon(
                        imageVector = Icons.Filled.Visibility,
                        contentDescription = stringResource(R.string.views),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.width(4.dp))

                    Text(
                        text = post.views.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.width(12.dp))

                    // 🔗 Share
                    IconButton(onClick = onShareClick) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = stringResource(R.string.share),
                            tint = MaterialTheme.colorScheme.primary
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
    val (gradient, icon) = when (title) {
        stringResource(R.string.trending) -> Brush.linearGradient(listOf(Color(0xFF43CEA2), Color(0xFF037C75))) to Icons.AutoMirrored.Filled.TrendingUp
        stringResource(R.string.featured) -> Brush.linearGradient(listOf(Color(0xFFFFD200), Color(0xFFFFA000))) to Icons.Filled.Star
        stringResource(R.string.popular) -> Brush.linearGradient(listOf(Color(0xFFFF5F6D), Color(0xFFE81E63))) to Icons.Filled.Favorite
        else -> Brush.linearGradient(listOf(Color.Gray, Color.DarkGray)) to Icons.AutoMirrored.Filled.ArrowForward
    }

    Card(
        modifier = Modifier
            .width(180.dp)
            .height(280.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradient),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(44.dp))
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.view_all_title, title),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun CategoryChip(label: String) {
    val (bgColor, icon) = when (label) {
        stringResource(R.string.trending) -> Color(0xFF00ACC1) to Icons.AutoMirrored.Filled.TrendingUp
        stringResource(R.string.featured) -> Color(0xFFFFC107) to Icons.Filled.Star
        stringResource(R.string.popular) -> Color(0xFFE91E63) to Icons.Filled.Favorite
        else -> MaterialTheme.colorScheme.primary to Icons.Filled.Star
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bgColor.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = bgColor, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, color = bgColor, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun AnimatedPanImage(imageUrl: String?, title: String?) {
    val infiniteTransition = rememberInfiniteTransition(label = "pan")
    val offsetY by infiniteTransition.animateFloat(
        initialValue = -25f,
        targetValue = 25f,
        animationSpec = infiniteRepeatable(
            tween(6000, easing = LinearEasing),
            RepeatMode.Reverse
        ),
        label = "offset"
    )

    SubcomposeAsyncImage(
        model = ImageRequest.Builder(LocalContext.current).data(imageUrl).build(),
        contentDescription = title,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .height(160.dp)
            .fillMaxWidth()
            .graphicsLayer {
                translationY = offsetY
                scaleX = 1.1f
                scaleY = 1.1f
            }
    )
}
