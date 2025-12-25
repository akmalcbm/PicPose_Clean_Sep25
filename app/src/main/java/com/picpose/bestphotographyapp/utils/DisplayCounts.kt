package com.picpose.bestphotographyapp.utils

import com.picpose.bestphotographyapp.data.database.entities.EngagementEntity
import com.picpose.bestphotographyapp.data.models.AIPrompt

fun AIPrompt.displayViews(local: EngagementEntity?): Int {
    val serverViews = views.coerceAtLeast(0)
    val localViews = local?.localViewCount?.coerceAtLeast(0) ?: 0

    return when {
        // 🔥 Server is source of truth (most common case)
        serverViews > 0 -> serverViews

        // 🔥 Optimistic / offline fallback
        localViews > 0 -> localViews

        // 🔥 Absolute fallback
        else -> 0
    }
}

fun AIPrompt.displayLikes(local: EngagementEntity?): Int {
    val serverLikes = likes.coerceAtLeast(0)

    return when {
        local?.isLiked == true && !isLiked -> serverLikes + 1
        local?.isLiked == false && isLiked -> (serverLikes - 1).coerceAtLeast(0)
        else -> serverLikes
    }
}

