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

    // Pull refresh state directly from uiState
    val isRefreshing = uiState.isRefreshing

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Show error messages via Snackbar
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
                    onRetry = {
                        viewModel.refresh()
                    }
                )
            }
            else -> {
                // Fixed PullToRefreshBox implementation
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = { viewModel.refresh() }
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 16.dp,
                            bottom = 100.dp
                        )
                    ) {
                        // Welcome Header
                        item {
                            AnimatedWelcomeHeader()
                        }

                        // Daily Tip (cached)
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
                                    }
                                )
                            }
                        }

                        // Quick Actions
                        item {
                            QuickActionsCard(
                                onNavigateToAllPrompts = onNavigateToAllPrompts,
                                onNavigateToFavorites = onNavigateToFavorites
                            )
                        }

                        // Quick Stats
                        item {
                            QuickStatsCard()
                        }

                        // AI Prompts Section (cached)
                        if (uiState.aiPrompts.isNotEmpty()) {
                            item {
                                AIPromptSectionHeader(
                                    promptCount = uiState.aiPrompts.size,
                                    favoriteCount = uiState.favoritePromptsCount,
                                    onNavigateToAllPrompts = onNavigateToAllPrompts,
                                    onNavigateToFavorites = onNavigateToFavorites
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

                        // ADMOB AD PLACEMENT
                        item {
                            AdmobBannerAd(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            )
                        }

                        // Guide Posts Section
                        if (uiState.guidePosts.isNotEmpty()) {
                            Log.d("HomeScreen", "Rendering guide posts section with ${uiState.guidePosts.size} items")
                            item {
                                SectionHeader(
                                    title = "Photography Guides",
                                    subtitle = "Learn with expert tutorials",
                                    icon = Icons.Default.Book
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
                                    }
                                )
                            }
                        } else {
                            Log.d("HomeScreen", "Guide posts section NOT rendered - empty list: ${uiState.guidePosts.size} items")
                        }

                        // Categories Section
                        if (uiState.categories.isNotEmpty()) {
                            item {
                                SectionHeader(
                                    title = "Categories",
                                    subtitle = "Explore different styles",
                                    icon = Icons.Default.Category
                                )
                            }

                            item {
                                CategoriesRow(
                                    categories = uiState.categories,
                                    onCategoryClick = onNavigateToCategory
                                )
                            }
                        }

                        // Featured Posts Section
                        if (uiState.featuredPosts.isNotEmpty()) {
                            item {
                                SectionHeader(
                                    title = "Featured Posts",
                                    subtitle = "Community highlights",
                                    icon = Icons.Default.Star
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
                                    }
                                )
                            }
                        }

                        // ADMOB INTERSTITIAL AD PLACEMENT
                        item {
                            AdmobInterstitialTrigger()
                        }

                        // Recent Posts Section
                        if (uiState.recentPosts.isNotEmpty()) {
                            item {
                                SectionHeader(
                                    title = "Recent Posts",
                                    subtitle = "Latest from community",
                                    icon = Icons.Default.Schedule
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
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Fixed PullToRefreshBox composable
@Composable
fun PullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    content: @Composable () -> Unit
) {
    Box {
        content()
        if (isRefreshing) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            }
        }
    }
}