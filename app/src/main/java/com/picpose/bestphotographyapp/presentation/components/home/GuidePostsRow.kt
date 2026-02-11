package com.picpose.bestphotographyapp.presentation.components.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.picpose.bestphotographyapp.data.models.GuidePost

@Composable
fun GuidePostsRow(
    guidePosts: List<GuidePost>,
    onGuidePostClick: (GuidePost) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(guidePosts, key = { it.id }) { guidePost ->
            GuidePostCards(
                guidePost = guidePost,
                onGuidePostClick = { onGuidePostClick(guidePost) }
            )
        }
    }
}
