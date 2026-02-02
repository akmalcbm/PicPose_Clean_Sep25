package com.picpose.bestphotographyapp.core.utils

import android.content.ClipData
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.toClipEntry

/**
 * ✅ Helper for the new suspend Clipboard API (Compose 1.9+).
 * Uses platform ClipData and setClipEntry under the hood.
 */
suspend fun Clipboard.setText(text: String, label: String = "text") {
    val clipData = ClipData.newPlainText(label, text)
    setClipEntry(clipData.toClipEntry())
}
