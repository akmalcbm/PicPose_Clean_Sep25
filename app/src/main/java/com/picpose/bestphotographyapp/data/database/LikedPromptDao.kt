/**
 * ---
 * File: LikedPromptDao.kt
 * Layer: Data (Room)
 * Project: PicPose
 *
 * Purpose:
 * Declares Room database operations used by repositories to read and persist local app state.
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

import androidx.room.*

@Dao
interface LikedPromptDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addLiked(item: LikedPrompt)

    @Query("DELETE FROM liked_prompts WHERE promptId = :id")
    suspend fun removeLiked(id: String)

    @Query("SELECT COUNT(*) FROM liked_prompts WHERE promptId = :id")
    suspend fun likedCount(id: String): Int

    suspend fun isLiked(id: String): Boolean {
        return likedCount(id) > 0
    }

    @Query("SELECT * FROM liked_prompts")
    suspend fun getAllLiked(): List<LikedPrompt>
}
