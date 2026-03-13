/**
 * ---
 * File: BackgroundRemovalModels.kt
 * Layer: Data
 * Project: PicPose
 *
 * Purpose:
 * Supports the PicPose app with feature-specific models, helpers, or UI building blocks.
 *
 * Interactions:
 * Works with nearby classes in the same layer to keep responsibilities separated and easier to maintain.
 *
 * Data Flow:
 * Feature-specific flow; see adjacent ViewModels and repositories for the full path.
 *
 * Maintainer Notes:
 * - Keep responsibilities narrow and update this header when the file grows into a larger abstraction.
 * ---
 */

package com.picpose.bestphotographyapp.data.rembg

import android.graphics.Color
import android.net.Uri

enum class BgRemovalQualityMode {
    HIGH_QUALITY_ONLINE,
    OFFLINE_BASIC
}

enum class BgBackgroundMode {
    TRANSPARENT,
    SOLID_COLOR,
    BLUR_ORIGINAL
}

data class BgBackgroundOption(
    val mode: BgBackgroundMode = BgBackgroundMode.TRANSPARENT,
    val solidColor: Int = Color.WHITE
)

data class BackgroundRemovalRequest(
    val sourceUri: Uri,
    val qualityMode: BgRemovalQualityMode,
    val backgroundOption: BgBackgroundOption,
    val outputTag: String = "create",
    val previewSize: Boolean = true
)

data class BackgroundRemovalResult(
    val cutoutUri: Uri,
    val previewUri: Uri
)
