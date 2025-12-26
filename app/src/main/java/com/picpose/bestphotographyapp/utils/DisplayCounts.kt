package com.picpose.bestphotographyapp.utils

import com.picpose.bestphotographyapp.data.database.entities.EngagementEntity
import com.picpose.bestphotographyapp.data.models.AIPrompt
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