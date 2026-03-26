/**
 * ---
 * File: AppCardStyles.kt
 * Layer: Shared UI
 * Project: PicPose
 *
 * Purpose:
 * Centralized card tokens used across multiple screens to keep surface language consistent.
 * ---
 */

package com.picpose.bestphotographyapp.components.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val appSectionCardShape: Shape = RoundedCornerShape(20.dp)

@Composable
fun appSectionCardColors(): CardColors = CardDefaults.cardColors(
    containerColor = MaterialTheme.colorScheme.surface
)

@Composable
fun appSectionCardBorder(alpha: Float = 0.12f): BorderStroke = BorderStroke(
    width = 1.dp,
    color = MaterialTheme.colorScheme.outline.copy(alpha = alpha)
)

@Composable
fun appSectionCardElevation(
    defaultElevation: Dp = 2.dp,
    pressedElevation: Dp = 4.dp
): CardElevation = CardDefaults.cardElevation(
    defaultElevation = defaultElevation,
    pressedElevation = pressedElevation
)

@Composable
fun appInnerSurfaceColor(alpha: Float = 0.42f): Color =
    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)
