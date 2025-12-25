package com.picpose.bestphotographyapp.utils

import com.picpose.bestphotographyapp.data.database.entities.EngagementEntity
import com.picpose.bestphotographyapp.data.models.Post

fun Post.displayViews(local: EngagementEntity?): Int {
    val serverViews = views.coerceAtLeast(0)
    val localViews = local?.localViewCount?.coerceAtLeast(0) ?: 0

    return when {
        serverViews > 0 -> serverViews
        localViews > 0 -> localViews
        else -> 0
    }
}

fun Post.displayFavorites(local: EngagementEntity?): Int {
    return favorites + if (local?.isFavorited == true) 1 else 0
}
