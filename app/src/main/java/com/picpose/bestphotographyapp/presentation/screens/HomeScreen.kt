package com.picpose.bestphotographyapp.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import android.util.Log
import com.picpose.bestphotographyapp.data.models.AIPrompt
import com.picpose.bestphotographyapp.data.models.Category
import com.picpose.bestphotographyapp.data.models.GuidePost
import com.picpose.bestphotographyapp.data.models.Post
import com.picpose.bestphotographyapp.presentation.components.ads.AdmobBannerAd
import com.picpose.bestphotographyapp.presentation.components.ads.AdmobInterstitialTrigger
import com.picpose.bestphotographyapp.presentation.components.home.AIPromptSectionHeader
import com.picpose.bestphotographyapp.presentation.components.home.AIPromptsRow
import com.picpose.bestphotographyapp.presentation.components.home.AnimatedDailyTipCard
import com.picpose.bestphotographyapp.presentation.components.home.AnimatedWelcomeHeader
import com.picpose.bestphotographyapp.presentation.components.home.CategoriesRow
import com.picpose.bestphotographyapp.presentation.components.home.ErrorScreen
import com.picpose.bestphotographyapp.presentation.components.home.FeaturedPostsRow
import com.picpose.bestphotographyapp.presentation.components.home.GuidePostsRow
import com.picpose.bestphotographyapp.presentation.components.home.HomeTopBar
import com.picpose.bestphotographyapp.presentation.components.home.LoadingScreen
import com.picpose.bestphotographyapp.presentation.components.home.QuickActionsCard
import com.picpose.bestphotographyapp.presentation.components.home.QuickStatsCard
import com.picpose.bestphotographyapp.presentation.components.home.RecentPostsColumn
import com.picpose.bestphotographyapp.presentation.components.home.SectionHeader
import com.picpose.bestphotographyapp.presentation.viewmodels.HomeViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(),
    onNavigateToAllPrompts: () -> Unit,
    onNavigateToFavorites: () -> Unit,
    onNavigateToCategory: (Category) -> Unit,
    onNavigateToPostDetail: (Post) -> Unit,
    onNavigateToPromptDetail: (AIPrompt) -> Unit,
    onNavigateToGuidePostDetail: (GuidePost) -> Unit,
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var currentTipIndex by remember { mutableIntStateOf(0) }

    val isRefreshing = uiState.isRefreshing

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
        topBar = {
            HomeTopBar(
                titleText = "PicPose",
                initialSearch = "",
                onQueryChanged = { query ->
                    viewModel.onSearchChanged(query)
                },
                onSearchClick = { query ->
                    viewModel.onSearchChanged(query)
                },
                onProfileClick = {
                    // navigate to profile or open menu
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        when {
            uiState.isLoading && !isRefreshing -> {
                LoadingScreen()
            }
            uiState.error != null && !isRefreshing -> {
                ErrorScreen(
                    message = uiState.error!!,
                    onRetry = { viewModel.refresh() }
                )
            }
            else -> {
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = { viewModel.refresh() }
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(vertical = 16.dp) // Simplified Padding
                    ) {
                        // Welcome Header
                        item {
                            AnimatedWelcomeHeader()
                        }

                        // Daily Tip
                        item {
                            val dailyTips = uiState.dailyTips.mapNotNull { it.tip }
                            if (dailyTips.isNotEmpty()) {
                                val tipToShow = dailyTips.getOrNull(currentTipIndex) ?: dailyTips.firstOrNull() ?: ""
                                AnimatedDailyTipCard(
                                    tip = tipToShow,
                                    onNextTip = {
                                        if (dailyTips.isNotEmpty()) {
                                            currentTipIndex = (currentTipIndex + 1) % dailyTips.size
                                        }
                                    },
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }

                        // Quick Actions
                        item {
                            QuickActionsCard(
                                onNavigateToAllPrompts = onNavigateToAllPrompts,
                                onNavigateToFavorites = onNavigateToFavorites,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }

                        // Quick Stats
                        item {
                            QuickStatsCard(modifier = Modifier.padding(horizontal = 16.dp))
                        }

                        // AI Prompts Section
                        if (uiState.aiPrompts.isNotEmpty()) {
                            item {
                                AIPromptSectionHeader(
                                    promptCount = uiState.aiPrompts.size,
                                    favoriteCount = uiState.favoritePromptsCount,
                                    onNavigateToAllPrompts = onNavigateToAllPrompts,
                                    onNavigateToFavorites = onNavigateToFavorites,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }

                            item {
                                AIPromptsRow(
                                    prompts = uiState.aiPrompts,
                                    onPromptClick = { aiPrompt ->
                                        viewModel.logPromptView(aiPrompt.id)
                                        onNavigateToPromptDetail(aiPrompt)
                                    },
                                    onCopyPrompt = { aiPrompt ->
                                        viewModel.copyPromptToClipboard(context, aiPrompt.fullPrompt ?: "")
                                        viewModel.logPromptCopy(aiPrompt.id)
                                    },
                                    onFavoriteClick = { aiPrompt ->
                                        viewModel.togglePromptFavorite(aiPrompt)
                                    }
                                )
                            }
                        }

                        // Admob Banner Ad
                        item {
                            AdmobBannerAd(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp, horizontal = 16.dp)
                            )
                        }

                        // Guide Posts Section
                        if (uiState.guidePosts.isNotEmpty()) {
                            item {
                                SectionHeader(
                                    title = "Photography Guides",
                                    subtitle = "Learn with expert tutorials",
                                    icon = Icons.Default.Book,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }

                            item {
                                GuidePostsRow(
                                    guidePosts = uiState.guidePosts,
                                    onGuidePostClick = onNavigateToGuidePostDetail,
                                    onLikeClick = { guidePost ->
                                        viewModel.toggleGuidePostLike(guidePost.id)
                                    },
                                    onShareClick = { guidePost ->
                                        viewModel.shareGuidePost(context, guidePost)
                                    },
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }

                        // Categories Section
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
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }

                        // Featured Posts Section
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
                                    onLikeClick = { post ->
                                        viewModel.togglePostLike(post.id)
                                    },
                                    onShareClick = { post ->
                                        viewModel.sharePost(context, post)
                                    },
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }

                        // Admob Interstitial Ad
                        item {
                            AdmobInterstitialTrigger()
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

                            item {
                                RecentPostsColumn(
                                    posts = uiState.recentPosts.take(3),
                                    onPostClick = onNavigateToPostDetail,
                                    onLikeClick = { post ->
                                        viewModel.togglePostLike(post.id)
                                    },
                                    onShareClick = { post ->
                                        viewModel.sharePost(context, post)
                                    },
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
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
