/**
 * ---
 * File: LikedPrompt.kt
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

package com.picpose.bestphotographyapp.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "liked_prompts")
data class LikedPrompt(
    @PrimaryKey val promptId: String,     // unique prompt id from server
    val likedAt: Long = System.currentTimeMillis()
)
