/**
 * ---
 * File: ClipboardExtensions.kt
 * Layer: Core
 * Project: PicPose
 *
 * Purpose:
 * Provides app-wide helpers, constants, analytics, locale, formatting, or cross-cutting abstractions.
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

package com.picpose.bestphotographyapp.utils

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
