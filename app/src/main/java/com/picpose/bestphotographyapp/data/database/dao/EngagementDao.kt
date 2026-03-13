/**
 * ---
 * File: EngagementDao.kt
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

package com.picpose.bestphotographyapp.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.picpose.bestphotographyapp.data.database.entities.EngagementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EngagementDao {

    /* ---------------------------------------------------- */
    /* SNAPSHOT READ (One-time, non-reactive) */
    /* ---------------------------------------------------- */

    @Query("SELECT * FROM engagement_state") // ✅ engagement_state ही रखें
    suspend fun getAll(): List<EngagementEntity>

    @Query("SELECT * FROM engagement_state WHERE promptId = :id LIMIT 1") // ✅
    suspend fun getById(id: String): EngagementEntity?

    @Query("SELECT promptId FROM engagement_state WHERE isFavorited = 1") // ✅
    suspend fun getAllFavoritedPromptIds(): List<String>

    /* ---------------------------------------------------- */
    /* OBSERVE BY ID (NEW - For single prompt state) */
    /* ---------------------------------------------------- */

    @Query("SELECT * FROM engagement_state WHERE promptId = :id LIMIT 1") // ✅
    fun observeById(id: String): Flow<EngagementEntity?>

    /* ---------------------------------------------------- */
    /* UPSERT */
    /* ---------------------------------------------------- */

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: EngagementEntity)

    @Upsert
    suspend fun upsertNew(entity: EngagementEntity)

    /* ---------------------------------------------------- */
    /* DELETE OPERATIONS */
    /* ---------------------------------------------------- */

    @Query("DELETE FROM engagement_state WHERE promptId = :id") // ✅
    suspend fun delete(id: String)

    @Query("DELETE FROM engagement_state") // ✅
    suspend fun deleteAll()

    /* ---------------------------------------------------- */
    /* LIKE / FAVORITE ACTIONS */
    /* ---------------------------------------------------- */

    @Query(
        """
        UPDATE engagement_state
        SET isLiked = :liked,
            updatedAt = :updatedAt
        WHERE promptId = :id
    """
    )
    suspend fun updateLike(
        id: String,
        liked: Boolean,
        updatedAt: Long
    )

    @Query(
        """
        UPDATE engagement_state
        SET isFavorited = :fav,
            updatedAt = :updatedAt
        WHERE promptId = :id
    """
    )
    suspend fun updateFavorite(
        id: String,
        fav: Boolean,
        updatedAt: Long
    )

    /* ---------------------------------------------------- */
    /* VIEW COUNT */
    /* ---------------------------------------------------- */

    @Query(
        """
        UPDATE engagement_state
        SET localViewCount = localViewCount + 1,
            pendingViewSync = pendingViewSync + 1,
            updatedAt = :updatedAt
        WHERE promptId = :id
    """
    )
    suspend fun incrementView(id: String, updatedAt: Long)

    /* ---------------------------------------------------- */
    /* PENDING SYNC MANAGEMENT */
    /* ---------------------------------------------------- */

    @Query(
        """
        UPDATE engagement_state
        SET pendingViewSync = 0
        WHERE promptId = :id
    """
    )
    suspend fun clearPendingSync(id: String)

    @Query(
        """
        UPDATE engagement_state
        SET pendingViewSync = :newValue
        WHERE promptId = :id
    """
    )
    suspend fun setPendingSync(id: String, newValue: Int)

    /* ---------------------------------------------------- */
    /* BATCH OPERATIONS */
    /* ---------------------------------------------------- */

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<EngagementEntity>)

    /* ---------------------------------------------------- */
    /* REACTIVE STREAMS */
    /* ---------------------------------------------------- */

    @Query("SELECT * FROM engagement_state")
    fun observeAll(): Flow<List<EngagementEntity>>

    @Query(
        """
        SELECT * FROM engagement_state
        WHERE isFavorited = 1
        ORDER BY updatedAt DESC
    """
    )
    fun observeFavorites(): Flow<List<EngagementEntity>>

    @Query(
        """
        SELECT * FROM engagement_state
        WHERE isLiked = 1
        ORDER BY updatedAt DESC
    """
    )
    fun observeLiked(): Flow<List<EngagementEntity>>

    /* ---------------------------------------------------- */
    /* STATISTICS & ANALYTICS */
    /* ---------------------------------------------------- */

    @Query("SELECT COUNT(*) FROM engagement_state WHERE isFavorited = 1")
    suspend fun getFavoriteCount(): Int

    @Query("SELECT COUNT(*) FROM engagement_state WHERE isLiked = 1")
    suspend fun getLikeCount(): Int

    @Query("SELECT SUM(localViewCount) FROM engagement_state")
    suspend fun getTotalLocalViews(): Int?

    @Query("SELECT SUM(pendingViewSync) FROM engagement_state")
    suspend fun getTotalPendingSync(): Int?

    /* ---------------------------------------------------- */
    /* UTILITY QUERIES */
    /* ---------------------------------------------------- */

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM engagement_state 
            WHERE promptId = :id AND isFavorited = 1
        )
    """
    )
    suspend fun isFavorited(id: String): Boolean

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM engagement_state 
            WHERE promptId = :id AND isLiked = 1
        )
    """
    )
    suspend fun isLiked(id: String): Boolean

    @Query("SELECT localViewCount FROM engagement_state WHERE promptId = :id")
    suspend fun getLocalViewCount(id: String): Int?

    @Query("SELECT pendingViewSync FROM engagement_state WHERE promptId = :id")
    suspend fun getPendingSyncCount(id: String): Int?
}