/**
 * ---
 * File: StatsEntity.kt
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

package com.picpose.bestphotographyapp.data.local.database

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
