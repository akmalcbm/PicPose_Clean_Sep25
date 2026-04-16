/**
 * ---
 * File: PicPoseAppBar.kt
 * Layer: Presentation (UI)
 * Project: PicPose
 *
 * Purpose:
 * Shared app bar design system matching Explore screen typography, spacing, and surface styling.
 *
 * ---
 */

package com.picpose.bestphotographyapp.components.common

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.picpose.bestphotographyapp.R

object PicPoseAppBarDefaults {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun colors(): TopAppBarColors = PicPoseTopBarDefaults.colors(
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        titleContentColor = MaterialTheme.colorScheme.onSurface,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PicPoseAppBar(
    title: String,
    onBack: (() -> Unit)? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    colors: TopAppBarColors = PicPoseAppBarDefaults.colors(),
    titleStyle: TextStyle = MaterialTheme.typography.headlineSmall,
    titleFontWeight: FontWeight = FontWeight.Bold,
    windowInsets: WindowInsets? = null,
    titleContent: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    PicPoseTopBarFrame(
        title = {
            if (titleContent != null) {
                titleContent()
            } else {
                Text(
                    text = title,
                    style = titleStyle,
                    fontWeight = titleFontWeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        navigationIcon = if (onBack != null) {
            {
                PicPoseTopBarActionButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    onClick = onBack,
                )
            }
        } else {
            null
        },
        actions = actions,
        colors = colors,
        windowInsets = windowInsets,
    )
}
