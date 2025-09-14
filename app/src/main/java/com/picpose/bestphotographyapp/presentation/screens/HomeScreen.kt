package com.picpose.bestphotographyapp.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.picpose.bestphotographyapp.data.models.AIPrompt
import com.picpose.bestphotographyapp.data.models.Category
import com.picpose.bestphotographyapp.data.models.Post
import com.picpose.bestphotographyapp.data.repository.HomeRepository
import com.picpose.bestphotographyapp.presentation.components.home.*
import com.picpose.bestphotographyapp.presentation.viewmodels.HomeViewModel
import com.picpose.bestphotographyapp.presentation.viewmodels.HomeViewModelFactory


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToAllPrompts: () -> Unit,
    onNavigateToFavorites: () -> Unit,
    onNavigateToCategory: (Category) -> Unit,
    onNavigateToPostDetail: (Post) -> Unit,
    onNavigateToPromptDetail: (AIPrompt) -> Unit,
    viewModel: HomeViewModel = viewModel(
        factory = HomeViewModelFactory(
            HomeRepository(LocalContext.current)
        )
    )
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var currentTipIndex by remember { mutableIntStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }

    // Daily tips that complement your ViewModel's photography tips
    val dailyTips = remember {
        listOf(
            "Use specific descriptive words in your prompts for better AI results! 🎯",
            "Try combining different art styles like 'watercolor meets cyberpunk' for unique effects! 🎨",
            "Add lighting conditions like 'golden hour' or 'dramatic shadows' to enhance your images! ✨",
            "Specify camera angles like 'bird's eye view' or 'close-up portrait' for better composition! 📸",
            "Use emotion words like 'serene', 'energetic', or 'mysterious' to set the mood! 😊"
        )
    }

    // Handle refresh state from ViewModel
    LaunchedEffect(uiState.isRefreshing) {
        isRefreshing = uiState.isRefreshing
    }

    // Clear error after some time
    LaunchedEffect(uiState.error) {
        if (uiState.error != null) {
            kotlinx.coroutines.delay(5000) // Auto-clear error after 5 seconds
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    HomeTopBar(
                        onSearchClick = {
                            // You can implement search later using viewModel.searchPrompts()
                        },
                        onProfileClick = { /* Handle profile */ }
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        // Show error snackbar if there's an error
        uiState.error?.let { error ->
            LaunchedEffect(error) {
                // You could show a Snackbar here if needed
            }
        }

        when {
            uiState.isLoading && !uiState.isRefreshing -> {
                LoadingScreen()
            }
            uiState.error != null && !uiState.isRefreshing -> {
                ErrorScreen(
                    message = uiState.error!!,
                    onRetry = {
                        viewModel.refresh() // Using your refresh method
                    }
                )
            }
            else -> {
                // Pull-to-refresh wrapper (optional)
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = { viewModel.refresh() }
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                        contentPadding = PaddingValues(vertical = 16.dp)
                    ) {
                        // Welcome Header
                        item {
                            AnimatedWelcomeHeader()
                        }

                        // Daily Tip with ViewModel integration
                        item {
                            AnimatedDailyTipCard(
                                tip = dailyTips[currentTipIndex],
                                onNextTip = {
                                    currentTipIndex = (currentTipIndex + 1) % dailyTips.size
                                }
                            )
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

                        // AI Prompts Section
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
                                        // Log analytics
                                        viewModel.logPromptView(aiPrompt.id)
                                        onNavigateToPromptDetail(aiPrompt)
                                    },
                                    onCopyPrompt = { aiPrompt ->
                                        // ✅ Fixed: Use fullPrompt
                                        viewModel.copyPromptToClipboard(context, aiPrompt.fullPrompt)
                                        // Log analytics
                                        viewModel.logPromptCopy(aiPrompt.id)
                                    },
                                    onFavoriteClick = { aiPrompt ->
                                        viewModel.togglePromptFavorite(aiPrompt)
                                    }
                                )
                            }
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
                                    subtitle = "Trending AI creations",
                                    icon = Icons.Default.Star
                                )
                            }

                            items(uiState.featuredPosts.take(3)) { post ->
                                if (uiState.featuredPosts.indexOf(post) == 0) {
                                    FeaturedPostCard(
                                        post = post,
                                        onPostClick = { onNavigateToPostDetail(post) },
                                        onLikeClick = {
                                            viewModel.togglePostLike(post.id)
                                        },
                                        onShareClick = {
                                            viewModel.sharePost(context, post)
                                        }
                                    )
                                } else {
                                    CompactPostCard(
                                        post = post,
                                        onPostClick = { onNavigateToPostDetail(post) }
                                    )
                                }
                            }
                        }

                        // Recent Posts Section
                        if (uiState.recentPosts.isNotEmpty()) {
                            item {
                                SectionHeader(
                                    title = "Recent Posts",
                                    subtitle = "Latest creations",
                                    icon = Icons.Default.Schedule
                                )
                            }

                            items(uiState.recentPosts.take(5)) { post ->
                                CompactPostCard(
                                    post = post,
                                    onPostClick = { onNavigateToPostDetail(post) }
                                )
                            }
                        }

                        // Empty state if no data
                        if (uiState.aiPrompts.isEmpty() &&
                            uiState.featuredPosts.isEmpty() &&
                            uiState.categories.isEmpty() &&
                            !uiState.isLoading) {
                            item {
                                EmptyStateCard(
                                    onRefresh = { viewModel.refresh() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Optional: Custom Pull-to-Refresh implementation (if you don't have it)
@Composable
fun PullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    content: @Composable () -> Unit
) {
    // Simple implementation - you can use Material3's PullToRefreshContainer
    // or implement your own pull-to-refresh logic here
    Box {
        content()

        // Show refresh indicator if refreshing
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

// Optional: Empty state component
@Composable
fun EmptyStateCard(
    onRefresh: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "📷",
                style = MaterialTheme.typography.displayMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No content available",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Pull down to refresh or try again",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Refresh")
            }
        }
    }
}

