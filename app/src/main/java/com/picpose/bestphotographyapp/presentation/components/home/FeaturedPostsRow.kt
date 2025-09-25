package com.picpose.bestphotographyapp.presentation.components.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.picpose.bestphotographyapp.data.models.Post

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
                    Text("Like (${post.likes})")
                }
                TextButton(onClick = onShareClick) {
                    Text("Share")
                }
            }
        }
    }
}