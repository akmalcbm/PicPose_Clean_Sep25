package com.picpose.bestphotographyapp.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "engagement_state")
data class EngagementEntity(
    @PrimaryKey val promptId: String,

    val isLiked: Boolean = false,
    val isFavorited: Boolean = false,

    val localViewCount: Int = 0,
    val pendingViewSync: Int = 0,

    val updatedAt: Long = System.currentTimeMillis()
)