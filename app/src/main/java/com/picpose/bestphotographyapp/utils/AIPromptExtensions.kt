package com.picpose.bestphotographyapp.utils

import com.picpose.bestphotographyapp.data.database.entities.EngagementEntity
import com.picpose.bestphotographyapp.data.models.AIPrompt

fun AIPrompt.displayViews(local: EngagementEntity?): Int {
    val serverViews = views.coerceAtLeast(0)
    val localViews = local?.localViewCount?.coerceAtLeast(0) ?: 0
    return serverViews + localViews
}

