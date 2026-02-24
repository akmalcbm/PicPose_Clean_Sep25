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
