/**
 * ---
 * File: PicPoseMetadataUi.kt
 * Layer: Shared UI
 * Project: PicPose
 *
 * Purpose:
 * Reusable metadata chips and action surfaces shared across content cards.
 * ---
 */

package com.picpose.bestphotographyapp.components.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun PicPoseMetadataContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f)),
    ) {
        content()
    }
}

@Composable
fun PicPoseMetaChip(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.46f),
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.26f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun PicPoseStatChip(
    icon: ImageVector,
    value: Int,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(if (compact) 9.dp else 11.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = if (compact) 0.42f else 0.52f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (compact) 6.dp else 8.dp, vertical = if (compact) 3.dp else 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(if (compact) 12.dp else 13.dp),
            )
            Spacer(modifier = Modifier.size(4.dp))
            Text(
                text = formatCompactCount(value),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun PicPoseActionIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    active: Boolean = false,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val buttonSize = if (compact) 32.dp else 38.dp
    val iconSize = if (compact) 15.dp else 17.dp
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(if (compact) 10.dp else 12.dp),
        color = if (active) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceColorAtElevation(if (compact) 2.dp else 3.dp)
        },
        border = BorderStroke(
            1.dp,
            if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f),
        ),
        tonalElevation = if (active) 2.dp else 0.dp,
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(buttonSize),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(iconSize),
                tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatCompactCount(value: Int): String {
    if (value < 1000) return value.toString()
    if (value < 1_000_000) {
        val decimal = value / 100 % 10
        return if (decimal == 0) "${value / 1000}K" else "${value / 1000}.${decimal}K"
    }
    val decimal = value / 100_000 % 10
    return if (decimal == 0) "${value / 1_000_000}M" else "${value / 1_000_000}.${decimal}M"
}
