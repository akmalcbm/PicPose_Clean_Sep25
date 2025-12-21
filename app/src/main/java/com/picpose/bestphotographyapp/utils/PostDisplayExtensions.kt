package com.picpose.bestphotographyapp.utils

import com.picpose.bestphotographyapp.data.database.entities.EngagementEntity
import com.picpose.bestphotographyapp.data.models.Post

fun Post.displayViews(local: EngagementEntity?): Int {
    return views + (local?.localViewCount ?: 0)
}

fun Post.displayFavorites(local: EngagementEntity?): Int {
    return favorites + if (local?.isFavorited == true) 1 else 0
}
