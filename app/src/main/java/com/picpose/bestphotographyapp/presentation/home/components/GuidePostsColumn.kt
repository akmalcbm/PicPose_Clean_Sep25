/**
 * ---
 * File: GuidePostsColumn.kt
 * Layer: Presentation (UI)
 * Project: PicPose
 *
 * Purpose:
 * Contains reusable Compose UI building blocks shared across screens.
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

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.picpose.bestphotographyapp.data.remote.dto.GuidePost

@Composable
fun GuidePostsColumn(
    guidePosts: List<GuidePost>,
    onGuidePostClick: (GuidePost) -> Unit,
    onLikeClick: (GuidePost) -> Unit,
    onShareClick: (GuidePost) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        itemsIndexed(guidePosts, key = { index, item -> "${item.id}_$index" }) { _, guidePost ->
            CompactGuidePostCard(
                guidePost = guidePost,
                onGuidePostClick = { onGuidePostClick(guidePost) },
                onLikeClick = { onLikeClick(guidePost) },
                onShareClick = { onShareClick(guidePost) }
            )
        }
    }
}
