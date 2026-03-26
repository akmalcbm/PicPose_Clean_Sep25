/**
 * ---
 * File: PostDisplayExtensions.kt
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

import com.picpose.bestphotographyapp.data.local.database.entity.EngagementEntity
import com.picpose.bestphotographyapp.data.remote.dto.Post

@Suppress("UNUSED_PARAMETER")
fun Post.displayViews(local: EngagementEntity?): Int {
    return views.coerceAtLeast(0)
}

fun Post.displayFavorites(local: EngagementEntity?): Int {
    return favorites + if (local?.isFavorited == true) 1 else 0
}
