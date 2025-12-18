package com.picpose.bestphotographyapp.presentation.components.home

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.hilt.navigation.compose.hiltViewModel
import com.picpose.bestphotographyapp.data.models.AIPrompt
import com.picpose.bestphotographyapp.data.models.Post
import com.picpose.bestphotographyapp.presentation.components.AIPromptCard
import com.picpose.bestphotographyapp.presentation.viewmodels.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewAllPromptsScreen(
    categoryType: String,
    onBack: () -> Unit,
    onPromptClick: (String) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // 🚀 Load category-specific posts once
    LaunchedEffect(categoryType) {
        when (categoryType) {
            "Trending", "Featured", "Popular" -> viewModel.loadTrendingFeaturedAndPopularPosts(limit = 30)
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("View All $categoryType Posts") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0) // ✅ Prevent double inset padding
    ) { innerPadding ->
        val posts = when (categoryType) {
            "Trending" -> uiState.trendingPosts
            "Featured" -> uiState.featuredPosts
            "Popular"  -> uiState.popularPosts
            else       -> emptyList()
        }

        if (uiState.isLoading && posts.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = WindowInsets.navigationBars
                        .asPaddingValues()
                        .calculateBottomPadding() + 24.dp // ✅ fixes white gap at bottom
                )
            ) {
                itemsIndexed(posts) { index: Int, post: Post ->
                    if (index > 0 && index % 5 == 0) {
                        NativeAdPlaceholder()
                    }

                    AIPromptCard(
                        prompt = post.toAIPrompt(),
                        onClick = { onPromptClick(post.id) },
                        onCopy = {
                            Toast.makeText(context, "Prompt copied!", Toast.LENGTH_SHORT).show()
                        },

                        // 👍 REQUIRED — LIKE
                        onLikeClick = {
                            viewModel.toggleLike(post.toAIPrompt())
                        },

                        // ⭐ FAVORITE (future logic)
                        onFavoriteClick = {
                            // TODO: future favorite logic
                        },

                        showFavoriteIcon = true,
                        isCompact = false
                    )

                }
            }
        }
    }
}

/** 🔄 Convert Post → AIPrompt for reuse in AIPromptCard */
private fun Post.toAIPrompt(): AIPrompt = AIPrompt(
    id          = this.id,
    title       = this.title,
    shortPrompt = this.description,
    fullPrompt  = this.description,
    imageUrl    = this.image,
    category    = this.category,
    createdAt   = this.createdAt,
    likes       = this.likes,
    favorites   = this.favorites,
    views       = this.views,
    isPopular   = this.isPopular,
    isFeatured  = this.isFeatured
)

@Composable
fun NativeAdPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.LightGray.copy(alpha = 0.3f)),
        contentAlignment = Alignment.Center
    ) {
        Text("Ad Placeholder", color = Color.Gray)
    }
}
