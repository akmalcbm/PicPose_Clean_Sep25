package com.picpose.bestphotographyapp.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.ThumbUpOffAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun LikeButton(
    isLiked: Boolean,
    likeCount: Int,
    onLikeClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable(onClick = onLikeClick)
    ) {
        Icon(
            imageVector =
                if (isLiked) Icons.Default.ThumbUp
                else Icons.Default.ThumbUpOffAlt,
            contentDescription = "Like",
            tint =
                if (isLiked) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = likeCount.toString(),
            style = MaterialTheme.typography.labelMedium
        )
    }
}
