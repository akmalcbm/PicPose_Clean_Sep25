package com.picpose.bestphotographyapp.presentation.screens

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.picpose.bestphotographyapp.data.models.*
import com.picpose.bestphotographyapp.presentation.components.ads.*
import com.picpose.bestphotographyapp.presentation.components.home.*
import com.picpose.bestphotographyapp.presentation.viewmodels.HomeViewModel
import com.picpose.bestphotographyapp.presentation.viewmodels.StatsViewModel
import kotlinx.coroutines.launch
import androidx.hilt.navigation.compose.hiltViewModel


@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateToAllPrompts: () -> Unit,
    onNavigateToFavorites: () -> Unit,
    onNavigateToCategory: (Category) -> Unit,
    onNavigateToPostDetail: (Post) -> Unit,
    onNavigateToPromptDetail: (AIPrompt) -> Unit,
    onNavigateToGuidePostDetail: (GuidePost) -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val uiState by viewModel.uiState.collectAsState()
    var currentTipIndex by remember { mutableIntStateOf(0) }
    val statsViewModel: StatsViewModel = hiltViewModel()

    // System edge-to-edge setup
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity?.window?.let { WindowCompat.setDecorFitsSystemWindows(it, false) }
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(uiState.error) {
        uiState.error?.let { message ->
            coroutineScope.launch {
                snackbarHostState.showSnackbar(message)
                viewModel.clearError()
            }
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        topBar = {
            HomeTopBar(
                titleText = "PicPose",
                initialSearch = "",
                onQueryChanged = { query -> viewModel.onSearchChanged(query) },
                onSearchClick = { query -> viewModel.onSearchChanged(query) },
                onProfileClick = { /* handle profile click */ }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        contentWindowInsets = WindowInsets(0)
    ) { innerPadding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                uiState.isLoading && !uiState.isRefreshing -> {
                    LoadingScreen()
                }
                uiState.error != null && !uiState.isRefreshing -> {
                    ErrorScreen(
                        message = uiState.error!!,
                        onRetry = { viewModel.refresh() }
                    )
                }
                else -> {
                    PullToRefreshBox(
                        isRefreshing = uiState.isRefreshing,
                        onRefresh = { viewModel.refresh() }
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(top = 12.dp, bottom = 100.dp)
                        ) {
                            item { AnimatedWelcomeHeader() }

                            // 🔹 Daily Tip
                            item {
                                val dailyTips = uiState.dailyTips.mapNotNull { it.tip }
                                if (dailyTips.isNotEmpty()) {
                                    val tipToShow = dailyTips.getOrNull(currentTipIndex)
                                        ?: dailyTips.first()
                                    AnimatedDailyTipCard(
                                        tip = tipToShow,
                                        onNextTip = {
                                            currentTipIndex = (currentTipIndex + 1) % dailyTips.size
                                        },
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )
                                }
                            }

                            // 🔹 AI Prompts
                            if (uiState.aiPrompts.isNotEmpty()) {
                                item {
                                    AIPromptSectionHeader(
                                        viewModel = statsViewModel,
                                        onNavigateToAllPrompts = onNavigateToAllPrompts,
                                        onNavigateToFavorites = onNavigateToFavorites,
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )
                                }

                                // 🔹 Trending & Featured Tab Section (uses same horizontal AIPromptCard layout)
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
                                        TrendingFeaturedRow(
                                            trendingPosts = uiState.trendingPosts,
                                            featuredPosts = uiState.featuredPosts,
                                            onPostClick = onNavigateToPostDetail,
                                            onLikeClick = { viewModel.togglePostLike(it.id) },
                                            onShareClick = { viewModel.sharePost(context, it) }
                                        )
                                    }
                                }

                            }

                            // 🔹 Ad
                            item {
                                AdmobBannerAd(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                )
                            }

                            // ✅ FIXED: Categories Section (Now properly visible)
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
                                        onCategoryClick = { category -> onNavigateToCategory(category) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }

                            // Recent Posts Section
                            if (uiState.recentPosts.isNotEmpty()) {
                                item {
                                    SectionHeader(
                                        title = "Recent Posts",
                                        subtitle = "Latest from community",
                                        icon = Icons.Default.Schedule,
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )
                                }
                                // ⚠️ Wrap inside Box + Column instead of LazyColumn
                                item {
                                    Column(
                                        modifier = Modifier
                                            .padding(horizontal = 16.dp)
                                            .fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        uiState.recentPosts.take(5).forEach { post ->
                                            RecentPostItem(
                                                post = post,
                                                onClick = {
                                                    // ✅ Match AI Prompt by exact post ID (reliable)
                                                    val matchingPrompt = uiState.aiPrompts.find { prompt ->
                                                        prompt.id?.trim() == post.id.trim()
                                                    }

                                                    if (matchingPrompt != null) {
                                                        // ✅ Open the corresponding AI Prompt Detail
                                                        onNavigateToPromptDetail(matchingPrompt)
                                                    } else {
                                                        // ⚠️ Fallback: open post detail if prompt not in memory
                                                        onNavigateToPostDetail(post)
                                                    }
                                                },
                                                onLikeClick = { viewModel.togglePostLike(post.id) },
                                                onShareClick = { viewModel.sharePost(context, post) }
                                            )
                                        }

                                    }
                                }
                            }

                            // 🔹 Guides Section
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

                            // 🔹 Featured posts
                            if (uiState.featuredPosts.isNotEmpty()) {
                                item {
                                    SectionHeader(
                                        title = "Featured Posts",
                                        subtitle = "Community highlights",
                                        icon = Icons.Default.Star,
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )
                                }
                                item {
                                    FeaturedPostsRow(
                                        posts = uiState.featuredPosts,
                                        onPostClick = onNavigateToPostDetail,
                                        onLikeClick = { viewModel.togglePostLike(it.id) },
                                        onShareClick = { viewModel.sharePost(context, it) },
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
    }
}



@Composable
fun PullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) { // Ensure the box fills the screen
        content()
        // No need for a separate refreshing indicator here as PullToRefresh handles it
    }
}