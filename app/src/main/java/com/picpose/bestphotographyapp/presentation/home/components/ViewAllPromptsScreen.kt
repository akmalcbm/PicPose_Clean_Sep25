/**
 * ---
 * File: ViewAllPromptsScreen.kt
 * Layer: Presentation (UI)
 * Project: PicPose
 *
 * Purpose:
 * Lists the app navigation routes and helper builders used by Navigation Compose.
 *
 * Interactions:
 * Reads immutable state from ViewModels or callbacks and emits user events back up to navigation or state owners.
 *
 * Data Flow:
 * Feature-specific flow; see adjacent ViewModels and repositories for the full path.
 *
 * Maintainer Notes:
 * - Prefer stateless composables and keep side effects inside well-scoped effect APIs.
 * - Document new navigation arguments and remember that recomposition can re-run this code frequently.
 * ---
 */

package com.picpose.bestphotographyapp.presentation.home.components

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.hilt.navigation.compose.hiltViewModel
import com.picpose.bestphotographyapp.R
import com.picpose.bestphotographyapp.data.local.database.entity.EngagementEntity
import com.picpose.bestphotographyapp.data.remote.dto.AIPrompt
import com.picpose.bestphotographyapp.data.remote.dto.Post
import com.google.android.gms.ads.nativead.NativeAd
import com.picpose.bestphotographyapp.components.ads.AdsConfigState
import com.picpose.bestphotographyapp.components.ads.AdsLog
import com.picpose.bestphotographyapp.components.ads.AdsManager
import com.picpose.bestphotographyapp.components.ads.LargeNativeAdCard
import com.picpose.bestphotographyapp.components.ads.NativeAdController
import com.picpose.bestphotographyapp.components.ads.NativeAdUiState
import com.picpose.bestphotographyapp.components.common.AIPromptCard
import com.picpose.bestphotographyapp.components.common.PicPoseTopAppBar
import com.picpose.bestphotographyapp.presentation.prompts.AIPromptViewModel
import com.picpose.bestphotographyapp.presentation.home.HomeViewModel
import androidx.compose.runtime.collectAsState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewAllPromptsScreen(
    categoryType: String,
    onBack: () -> Unit,
    onPromptClick: (String) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
    aiPromptViewModel: AIPromptViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val localEngagementStates by aiPromptViewModel.localEngagementStates.collectAsState()
    val adsConfigState by AdsManager.configState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val categoryDisplayName = when (categoryType) {
        "Trending" -> stringResource(R.string.trending)
        "Featured" -> stringResource(R.string.featured)
        "Popular" -> stringResource(R.string.popular)
        else -> categoryType
    }

    // 🚀 Load category-specific posts once
    LaunchedEffect(categoryType) {
        when (categoryType) {
            "Trending", "Featured", "Popular" -> viewModel.loadTrendingFeaturedAndPopularPosts(limit = 30)
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            PicPoseTopAppBar(
                title = stringResource(R.string.view_all_posts_title, categoryDisplayName),
                onBack = onBack,
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
            val adFrequency = 5
            val adSlotIndices by remember(posts.size) {
                derivedStateOf {
                    buildList {
                        var i = adFrequency
                        while (i < posts.size) {
                            add(i)
                            i += adFrequency
                        }
                    }
                }
            }
            val adStates = remember { mutableStateMapOf<Int, NativeAdUiState>() }
            val adControllers = remember { mutableStateMapOf<Int, NativeAdController>() }
            val rows by remember(posts, adStates) {
                derivedStateOf {
                    buildList<ViewAllRow> {
                        posts.forEachIndexed { index, post ->
                            if (index > 0 && index % adFrequency == 0) {
                                val state = adStates[index]
                                if (state is NativeAdUiState.Loaded) {
                                    add(ViewAllRow.AdRow(slotIndex = index, ad = state.ad))
                                }
                            }
                            add(ViewAllRow.PostRow(post = post, index = index))
                        }
                    }
                }
            }

            LaunchedEffect(adsConfigState, adSlotIndices) {
                if (adsConfigState !is AdsConfigState.Ready) return@LaunchedEffect

                if (!AdsManager.canShowAds()) {
                    adSlotIndices.forEach { slot ->
                        adStates[slot] = NativeAdUiState.Disabled
                    }
                    AdsLog.i(
                        AdsLog.TAG_UI,
                        "[AdsUI] screen=ViewAllPrompts placement=${AdsManager.KEY_NATIVE_AD} action=skip reason=global_gate"
                    )
                    return@LaunchedEffect
                }

                adSlotIndices.forEach { slot ->
                    if (adStates[slot] is NativeAdUiState.Loaded || adStates[slot] is NativeAdUiState.Loading) {
                        return@forEach
                    }
                    adStates[slot] = NativeAdUiState.Loading
                    val controller = adControllers.getOrPut(slot) {
                        NativeAdController(placementKey = AdsManager.KEY_NATIVE_AD)
                    }
                    controller.load(
                        context = context,
                        callbacks = object : NativeAdController.Callbacks {
                            override fun onLoaded(ad: com.google.android.gms.ads.nativead.NativeAd) {
                                adStates[slot] = NativeAdUiState.Loaded(ad)
                            }

                            override fun onFailed(error: com.google.android.gms.ads.LoadAdError) {
                                adStates[slot] = NativeAdUiState.Failed
                                AdsLog.w(
                                    AdsLog.TAG_UI,
                                    "[AdsUI] screen=ViewAllPrompts placement=${AdsManager.KEY_NATIVE_AD} slot=$slot action=failed domain=${error.domain} code=${error.code} message=${error.message}"
                                )
                            }

                            override fun onUnavailable(reason: String) {
                                adStates[slot] = if (reason == "ADS_DISABLED") {
                                    NativeAdUiState.Disabled
                                } else {
                                    NativeAdUiState.Failed
                                }
                            }
                        }
                    )
                }
            }

            DisposableEffect(Unit) {
                onDispose {
                    adControllers.values.forEach { it.clear() }
                    adControllers.clear()
                    adStates.clear()
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                    ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = 24.dp
                )
            ) {
                items(
                    items = rows,
                    key = { row ->
                        when (row) {
                            is ViewAllRow.AdRow -> "inline_ad_${row.slotIndex}"
                            is ViewAllRow.PostRow -> "post_${row.post.id}_${row.index}"
                        }
                    }
                ) { row ->
                    when (row) {
                        is ViewAllRow.AdRow -> {
                            LargeNativeAdCard(
                                nativeAd = row.ad,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            )
                        }

                        is ViewAllRow.PostRow -> {
                            val post = row.post
                            val local: EngagementEntity? = localEngagementStates[post.id]

                            AIPromptCard(
                                prompt = post.toAIPrompt(),
                                localEngagement = local,
                                onClick = { onPromptClick(post.id) },
                                onCopy = {
                                    Toast.makeText(context, context.getString(R.string.prompt_copied_toast), Toast.LENGTH_SHORT).show()
                                },
                                onLikeClick = {
                                    aiPromptViewModel.onLikeClicked(post.toAIPrompt())
                                },
                                onFavoriteClick = {
                                    aiPromptViewModel.onFavoriteClicked(post.toAIPrompt())
                                },
                                showFavoriteIcon = true,
                                isCompact = false
                            )
                        }
                    }
                }
            }
        }
    }
}

private sealed interface ViewAllRow {
    data class PostRow(
        val post: Post,
        val index: Int
    ) : ViewAllRow

    data class AdRow(
        val slotIndex: Int,
        val ad: NativeAd
    ) : ViewAllRow
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
