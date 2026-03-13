/**
 * ---
 * File: EngagementEntity.kt
 * Layer: Data (Room)
 * Project: PicPose
 *
 * Purpose:
 * Defines a Room entity or local persistence model stored inside the PicPose database.
 *
 * Interactions:
 * Used by repositories for offline state, engagement persistence, and cached values that survive process death.
 *
 * Data Flow:
 * Repository -> DAO -> Room table -> Flow back to ViewModel/UI
 *
 * Maintainer Notes:
 * - Update migrations carefully when changing schema or table names.
 * - TODO: Replace destructive migration paths before shipping production schema changes.
 * ---
 */

package com.picpose.bestphotographyapp.data.local.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "engagement_state")
data class EngagementEntity(
    @PrimaryKey val promptId: String,

    val isLiked: Boolean = false,
    val isFavorited: Boolean = false,

    val localViewCount: Int = 0,
    val pendingViewSync: Int = 0,

    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    // Helper function for logging
    override fun toString(): String {
        return "EngagementEntity(promptId='$promptId', " +
                "isLiked=$isLiked, isFavorited=$isFavorited, " +
                "localViewCount=$localViewCount, pendingViewSync=$pendingViewSync)"
    }
}