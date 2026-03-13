/**
 * ---
 * File: FeaturedPostsRow.kt
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.picpose.bestphotographyapp.data.remote.dto.Post
import com.picpose.bestphotographyapp.R

@Composable
fun FeaturedPostsRow(
    posts: List<Post>,
    onPostClick: (Post) -> Unit,
    onLikeClick: (Post) -> Unit,
    onShareClick: (Post) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(posts, key = { it.id }) { post ->
            PostCard(
                post = post,
                onClick = { onPostClick(post) },
                onLikeClick = { onLikeClick(post) },
                onShareClick = { onShareClick(post) }
            )
        }
    }
}

@Composable
fun PostCard(
    post: Post,
    onClick: () -> Unit,
    onLikeClick: () -> Unit,
    onShareClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.width(200.dp),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = post.title,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            post.description?.let { desc ->
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                TextButton(onClick = onLikeClick) {
                    Text(stringResource(R.string.like_count, post.likes))
                }
                TextButton(onClick = onShareClick) {
                    Text(stringResource(R.string.share))
                }
            }
        }
    }
}
