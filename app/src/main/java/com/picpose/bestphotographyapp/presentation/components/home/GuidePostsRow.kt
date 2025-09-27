package com.picpose.bestphotographyapp.presentation.components.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.picpose.bestphotographyapp.data.models.GuidePost

@Composable
fun GuidePostsRow(
    guidePosts: List<GuidePost>,
    onGuidePostClick: (GuidePost) -> Unit,
    onLikeClick: (GuidePost) -> Unit,
    onShareClick: (GuidePost) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(guidePosts, key = { it.id }) { guidePost ->
            FeaturedGuidePostCard(
                guidePost = guidePost,
                onGuidePostClick = { onGuidePostClick(guidePost) },
                onLikeClick = { onLikeClick(guidePost) },
                onShareClick = { onShareClick(guidePost) }
            )
        }
    }
}