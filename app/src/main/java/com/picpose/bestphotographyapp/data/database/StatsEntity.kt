package com.picpose.bestphotographyapp.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "quick_stats")
data class StatsEntity(
    @PrimaryKey val id: Int = 1,
    val total_prompts: Int,
    val total_likes: Int,
    val total_favorites: Int,
    val total_copies: Int,
    val total_views: Int,
    val last_updated: Long
)
