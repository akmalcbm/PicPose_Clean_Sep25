/**
 * ---
 * File: MediaUrlResolver.kt
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

package com.picpose.bestphotographyapp.core.utils

import com.picpose.bestphotographyapp.BuildConfig

object MediaUrlResolver {

    fun resolve(path: String?): String? {
        val raw = path?.trim().orEmpty()
        if (raw.isBlank()) return null
        if (raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true)) {
            return raw
        }
        val base = BuildConfig.API_BASE_URL.trim().ifBlank { "https://picpose.iamakmal.in/" }
        return base.trimEnd('/') + "/" + raw.trimStart('/')
    }
}

