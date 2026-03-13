/**
 * ---
 * File: FavoritePromptDao.kt
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

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoritePromptDao {

    /* --------------------------------------------------- */
    /* INSERT / DELETE */
    /* --------------------------------------------------- */

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addToFavorites(favorite: FavoritePrompt)

    @Query("DELETE FROM favorite_prompts WHERE promptId = :promptId")
    suspend fun removeFromFavorites(promptId: String)

    @Query("DELETE FROM favorite_prompts")
    suspend fun clearAllFavorites()

    /* --------------------------------------------------- */
    /* CHECK FAVORITE */
    /* --------------------------------------------------- */

    // Fast boolean check (BEST PRACTICE)
    @Query("SELECT EXISTS(SELECT 1 FROM favorite_prompts WHERE promptId = :promptId)")
    suspend fun isBookmarked(promptId: String): Boolean

    // Optional legacy-style count check (safe to keep)
    @Query("SELECT COUNT(*) FROM favorite_prompts WHERE promptId = :promptId")
    suspend fun getFavoriteCountByPrompt(promptId: String): Int

    /* --------------------------------------------------- */
    /* FETCH FAVORITES */
    /* --------------------------------------------------- */

    @Query("SELECT * FROM favorite_prompts ORDER BY favoritedAt DESC")
    suspend fun getAllFavorites(): List<FavoritePrompt>

    // Reactive (future-proof for Compose / badges / counters)
    @Query("SELECT * FROM favorite_prompts ORDER BY favoritedAt DESC")
    fun observeAllFavorites(): Flow<List<FavoritePrompt>>

    /* --------------------------------------------------- */
    /* FAVORITE COUNT */
    /* --------------------------------------------------- */

    @Query("SELECT COUNT(*) FROM favorite_prompts")
    suspend fun getFavoriteCount(): Int

    // Reactive count (🔥 VERY USEFUL for UI badge)
    @Query("SELECT COUNT(*) FROM favorite_prompts")
    fun observeFavoriteCount(): Flow<Int>
}
