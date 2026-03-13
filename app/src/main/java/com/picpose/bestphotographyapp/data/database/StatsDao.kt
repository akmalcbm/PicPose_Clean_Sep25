/**
 * ---
 * File: StatsDao.kt
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
import kotlinx.coroutines.flow.Flow

@Dao
/**
 * DAO for the `quick_stats` table.
 *
 * The table stores one latest snapshot, so callers read `LIMIT 1` and replace
 * the row wholesale whenever a newer stats payload arrives from the backend.
 */
interface StatsDao {
    /** Emits the latest cached quick-stats snapshot for the Home screen. */
    @Query("SELECT * FROM quick_stats LIMIT 1")
    fun getStats(): Flow<StatsEntity?>

    /** Inserts or replaces the current stats snapshot. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStats(stats: StatsEntity)

    /** Clears cached stats when the app needs a hard refresh or cleanup. */
    @Query("DELETE FROM quick_stats")
    suspend fun clearStats()
}
