/**
 * ---
 * File: DisplayCounts.kt
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

import android.util.Log
import com.picpose.bestphotographyapp.data.local.database.entity.EngagementEntity
import com.picpose.bestphotographyapp.data.remote.dto.AIPrompt
import kotlin.math.max

// DisplayCounts.kt में:
/**
 * 🔥 CENTRALIZED DISPLAY LOGIC
 * All screens should use these functions for consistency
 */
fun AIPrompt.displayViews(local: EngagementEntity?): Int {
    // Server views are source of truth
    // They already include all synced views from all users
    return this.views.coerceAtLeast(0)
}

fun AIPrompt.displayLikes(local: EngagementEntity?): Int {
    val serverLikes = this.likes.coerceAtLeast(0)

    // Adjust ONLY if local state differs from server state
    // This handles optimistic updates during network calls
    return when {
        local?.isLiked == true && !this.isLiked -> serverLikes + 1
        local?.isLiked == false && this.isLiked -> max(0, serverLikes - 1)
        else -> serverLikes
    }
}

fun AIPrompt.displayFavorites(local: EngagementEntity?): Int {
    val serverFavorites = this.favorites.coerceAtLeast(0)

    return when {
        local?.isFavorited == true && !this.isFavouriteBookmarked -> serverFavorites + 1
        local?.isFavorited == false && this.isFavouriteBookmarked -> max(0, serverFavorites - 1)
        else -> serverFavorites
    }
}

/**
 * Get icon states from single source of truth
 */
fun AIPrompt.getLikeIconState(local: EngagementEntity?): Boolean {
    return local?.isLiked ?: this.isLiked
}

fun AIPrompt.getBookmarkIconState(local: EngagementEntity?): Boolean {
    return local?.isFavorited ?: this.isFavouriteBookmarked
}

/**
 * For debug logging
 */
fun AIPrompt.logEngagementState(local: EngagementEntity?, tag: String) {
    Log.d(tag, "📊 Prompt: ${this.id}")
    Log.d(tag, "  Server - Liked: ${this.isLiked}, Likes: ${this.likes}")
    Log.d(tag, "  Server - Bookmarked: ${this.isFavouriteBookmarked}, Favorites: ${this.favorites}")
    Log.d(tag, "  Server - Views: ${this.views}")
    Log.d(tag, "  Local - Liked: ${local?.isLiked}, Favorited: ${local?.isFavorited}")
    Log.d(tag, "  Local - ViewCount: ${local?.localViewCount}")
    Log.d(tag, "  Display - Likes: ${displayLikes(local)}, Favorites: ${displayFavorites(local)}, Views: ${displayViews(local)}")
}