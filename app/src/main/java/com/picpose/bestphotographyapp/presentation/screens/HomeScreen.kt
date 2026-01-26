package com.picpose.bestphotographyapp.presentation.screens

import android.app.Activity
import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.picpose.bestphotographyapp.data.models.*
import com.picpose.bestphotographyapp.presentation.components.ads.*
import com.picpose.bestphotographyapp.presentation.components.home.*
import com.picpose.bestphotographyapp.presentation.viewmodels.AuthViewModel
import com.picpose.bestphotographyapp.presentation.viewmodels.HomeViewModel
import com.picpose.bestphotographyapp.presentation.viewmodels.StatsViewModel
import com.picpose.bestphotographyapp.core.utils.ShareUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateToAllPrompts: () -> Unit,
    onNavigateToFavorites: () -> Unit,
    onNavigateToCategory: (Category) -> Unit,
    onNavigateToPostDetail: (Post) -> Unit,
    onNavigateToPromptDetail: (AIPrompt) -> Unit,
    onNavigateToGuidePostDetail: (GuidePost) -> Unit,
    onNavigateToViewAll: (String) -> Unit,
    onNavigateToEditProfile: () -> Unit,
    onRequestLogin: () -> Unit        // ✅ NEW — navigation callback
) {
    val context = LocalContext.current
    val activity = context as? Activity

    val uiState by viewModel.uiState.collectAsState()
    val localEngagementStates by viewModel.localEngagementStates.collectAsState()
    val statsViewModel: StatsViewModel = hiltViewModel()
    val coroutineScope = rememberCoroutineScope()

    val authViewModel: AuthViewModel = hiltViewModel()
    val currentUser by authViewModel.currentUser.collectAsState()
    val isLoggedIn by authViewModel.isLoggedIn.collectAsState()

    // Enable edge-to-edge layout
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            activity?.window?.let { WindowCompat.setDecorFitsSystemWindows(it, false) }
    }

    var isRefreshing by remember { mutableStateOf(false) }
    var currentTipIndex by remember { mutableIntStateOf(0) }

    // Initial shimmer loading logic
    val isCompletelyEmpty =
        uiState.aiPrompts.isEmpty() &&
                uiState.trendingPosts.isEmpty() &&
                uiState.recentPosts.isEmpty() &&
                uiState.categories.isEmpty() &&
                uiState.guidePosts.isEmpty()

    val showShimmer = uiState.isLoading && isCompletelyEmpty

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            HomeTopBar(
                titleText = "PicPose",
                initialSearch = "",
                userImage = currentUser?.displayProfilePicture,
                onQueryChanged = { query -> viewModel.onSearchChanged(query) },
                onSearchClick = { query -> viewModel.onSearchChanged(query) },

                // ⭐ Updated correct logic here
                onProfileClick = {
                    if (isLoggedIn) onNavigateToEditProfile()
                    else onRequestLogin()
                }
            )
        },
        contentWindowInsets = WindowInsets(0)
    ) { innerPadding ->

        PullToRefreshBox(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            isRefreshing = isRefreshing,
            onRefresh = {
                coroutineScope.launch {
                    isRefreshing = true
                    viewModel.refresh()
                    isRefreshing = false
                }
            }
        ) {

            if (showShimmer) {
                ShimmerLoadingHomeScreen()
            } else {

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(
                        top = 12.dp,
                        bottom = WindowInsets.navigationBars
                            .asPaddingValues()
                            .calculateBottomPadding() + 24.dp
                    )
                ) {

                    // Welcome Header
                    item { AnimatedWelcomeHeader() }

                    // Daily Tip
                    if (uiState.dailyTips.isNotEmpty()) {
                        item {
                            val tip = uiState.dailyTips[currentTipIndex % uiState.dailyTips.size]
                            AnimatedDailyTipCard(
                                tip = tip.tip,
                                onNextTip = {
                                    currentTipIndex = (currentTipIndex + 1) % uiState.dailyTips.size
                                },
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }

                    // AI Prompts Section
                    if (uiState.aiPrompts.isNotEmpty()) {
                        item {
                            AIPromptSectionHeader(
                                viewModel = statsViewModel,
                                onNavigateToAllPrompts = onNavigateToAllPrompts,
                                onNavigateToFavorites = onNavigateToFavorites,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }

                    // Trending & Featured & Popular
                    if (uiState.trendingPosts.isNotEmpty() || uiState.featuredPosts.isNotEmpty()) {
                        item {
                            SectionHeader(
                                title = "Community Highlights",
                                subtitle = "Explore trending & featured posts",
                                icon = Icons.Default.Star,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }

                        item {
                            TrendingFeaturedAndPopularRow(
                                trendingPosts = uiState.trendingPosts,
                                featuredPosts = uiState.featuredPosts,
                                popularPosts = uiState.popularPosts,
                                onPostClick = onNavigateToPostDetail,
                                //onLikeClick = { viewModel.onPostLikeClicked(it.id) },
                                onShareClick = { post ->
                                    coroutineScope.launch {

                                        // 🔍 Try to resolve full AI Prompt first
                                        val promptMatch = uiState.aiPrompts
                                            .firstOrNull { it.id.trim() == post.id.trim() }

                                        if (promptMatch != null) {
                                            // ✅ FULL PROMPT SHARE (same as detail screen)
                                            ShareUtils.sharePrompt(
                                                context = context,
                                                promptText =
                                                    promptMatch.fullPrompt
                                                        ?: promptMatch.shortPrompt
                                                        ?: promptMatch.title,
                                                imageUrl = promptMatch.imageUrl ?: promptMatch.imageUrl2
                                            )
                                        } else {
                                            // 🔁 Fallback → normal post share
                                            ShareUtils.sharePrompt(
                                                context = context,
                                                promptText =
                                                    post.description
                                                        .takeIf { it.isNotBlank() }
                                                        ?: post.title,
                                                imageUrl = post.image
                                            )
                                        }
                                    }
                                },
                                onViewAllClick = { category ->
                                    onNavigateToViewAll(category)
                                }
                            )
                        }
                    }

                    // Banner Ad
                    item {
                        AdmobBannerAd(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        )
                    }

                    // Categories
                    if (uiState.categories.isNotEmpty()) {
                        item {
                            SectionHeader(
                                title = "Categories",
                                subtitle = "Explore different styles",
                                icon = Icons.Default.Category,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                        item {
                            CategoriesRow(
                                categories = uiState.categories,
                                onCategoryClick = onNavigateToCategory,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // Recent Posts
                    if (uiState.recentPosts.isNotEmpty()) {
                        item {
                            SectionHeader(
                                title = "Recent Posts",
                                subtitle = "Latest from the community",
                                icon = Icons.Default.Schedule,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }

                        items(uiState.recentPosts.take(5)) { post ->
                            val local = localEngagementStates[post.id]

                            RecentPostItem(
                                post = post,
                                localEngagement = local,
                                onClick = {
                                    val match = uiState.aiPrompts.find { it.id.trim() == post.id.trim() }
                                    if (match != null)
                                        onNavigateToPromptDetail(match)
                                    else
                                        onNavigateToPostDetail(post)
                                },
                                onLikeClick = {
                                    viewModel.onPostLikeClicked(post.id)
                                },
                                onShareClick = {
                                    coroutineScope.launch {

                                        // 1️⃣ Try to resolve full AI Prompt first
                                        val promptMatch = uiState.aiPrompts
                                            .firstOrNull { it.id.trim() == post.id.trim() }

                                        if (promptMatch != null) {
                                            // ✅ SHARE FULL AI PROMPT (same as detail screen)
                                            ShareUtils.sharePrompt(
                                                context = context,
                                                promptText =
                                                    promptMatch.fullPrompt
                                                        ?: promptMatch.shortPrompt
                                                        ?: promptMatch.title,
                                                imageUrl = promptMatch.imageUrl ?: promptMatch.imageUrl2
                                            )
                                        } else {
                                            // 2️⃣ Fallback → normal Post sharing
                                            ShareUtils.sharePrompt(
                                                context = context,
                                                promptText =
                                                    post.description
                                                        .takeIf { it.isNotBlank() }
                                                        ?: post.title,
                                                imageUrl = post.image
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }

                    }

                    // Guides
                    if (uiState.guidePosts.isNotEmpty()) {
                        item {
                            SectionHeader(
                                title = "Photography Guides",
                                subtitle = "Learn from experts",
                                icon = Icons.Default.Book,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }

                        item {
                            GuidePostsRow(
                                guidePosts = uiState.guidePosts,
                                onGuidePostClick = onNavigateToGuidePostDetail,
                                onLikeClick = { viewModel.toggleGuidePostLike(it.id) },
                                onShareClick = { viewModel.shareGuidePost(context, it) },
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }

                    item { AdmobInterstitialTrigger() }
                }
            }
        }
    }
}



/* ───────────────────────────────
 *  Simple shimmer placeholder
 *  (replace with Modifier.placeholder if desired)
 *  ─────────────────────────────── */
@Composable
fun ShimmerLoadingHomeScreen() {
    val transition = rememberInfiniteTransition(label = "home_shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alphaAnim"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
        userScrollEnabled = false
    ) {
        // Simulate 3 main sections
        items(5) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.LightGray.copy(alpha = alpha))
                    .height(180.dp)
            ) {}
        }
    }
}