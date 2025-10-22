package com.picpose.bestphotographyapp.presentation.screens

import android.app.Activity
import android.os.Build
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
    val activity = context as? Activity
    val uiState by viewModel.uiState.collectAsState()
    var currentTipIndex by remember { mutableIntStateOf(0) }

    // Handle system window fitting for full edge-to-edge layout
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity?.window?.let {
                WindowCompat.setDecorFitsSystemWindows(it, false)
            }
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
            .imePadding(), // handles keyboard overlap
        topBar = {
            HomeTopBar(
                titleText = "PicPose",
                initialSearch = "",
                onQueryChanged = { query -> viewModel.onSearchChanged(query) },
                onSearchClick = { query -> viewModel.onSearchChanged(query) },
                onProfileClick = { /* handle profile */ }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        // ✅ Remove default insets to avoid double padding
        contentWindowInsets = WindowInsets(0)
    ) { innerPadding ->

        // ✅ Proper content layout without extra paddings
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
                            contentPadding = PaddingValues(
                                top = 16.dp,
                                bottom = 90.dp // avoid overlapping bottom bar
                            )
                        ) {
                            item { AnimatedWelcomeHeader() }

                            item {
                                val dailyTips = uiState.dailyTips.mapNotNull { it.tip }
                                if (dailyTips.isNotEmpty()) {
                                    val tipToShow = dailyTips.getOrNull(currentTipIndex)
                                        ?: dailyTips.firstOrNull().orEmpty()
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

                            item {
                                QuickActionsCard(
                                    onNavigateToAllPrompts = onNavigateToAllPrompts,
                                    onNavigateToFavorites = onNavigateToFavorites,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }

                            item {
                                QuickStatsCard(modifier = Modifier.padding(horizontal = 16.dp))
                            }

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
                                        onPromptClick = {
                                            viewModel.logPromptView(it.id)
                                            onNavigateToPromptDetail(it)
                                        },
                                        onCopyPrompt = {
                                            viewModel.copyPromptToClipboard(context, it.fullPrompt ?: "")
                                            viewModel.logPromptCopy(it.id)
                                        },
                                        onFavoriteClick = { viewModel.togglePromptFavorite(it) }
                                    )
                                }
                            }

                            item {
                                AdmobBannerAd(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp, horizontal = 16.dp)
                                )
                            }

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

                            if (uiState.recentPosts.isNotEmpty()) {
                                item {
                                    SectionHeader(
                                        title = "Recent Posts",
                                        subtitle = "Latest creations",
                                        icon = Icons.Default.Schedule,
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )
                                }
                                item {
                                    RecentPostsColumn(
                                        posts = uiState.recentPosts.take(3),
                                        onPostClick = onNavigateToPostDetail,
                                        onLikeClick = { viewModel.togglePostLike(it.id) },
                                        onShareClick = { viewModel.sharePost(context, it) },
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )
                                }
                            }

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
                                        onLikeClick = { viewModel.toggleGuidePostLike(it.id) },
                                        onShareClick = { viewModel.shareGuidePost(context, it) },
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )
                                }
                            }

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
