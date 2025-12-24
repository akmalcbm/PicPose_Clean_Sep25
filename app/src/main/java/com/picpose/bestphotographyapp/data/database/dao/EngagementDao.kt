package com.picpose.bestphotographyapp.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.picpose.bestphotographyapp.data.database.entities.EngagementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EngagementDao {

    /* ---------------------------------------------------- */
    /* SNAPSHOT READ (One-time, non-reactive) */
    /* ---------------------------------------------------- */

    @Query("SELECT * FROM engagement_state")
    suspend fun getAll(): List<EngagementEntity>

    @Query("SELECT * FROM engagement_state WHERE promptId = :id LIMIT 1")
    suspend fun getById(id: String): EngagementEntity?

    /**
     * 🔥 REQUIRED FOR APP RESTART FIX
     * Used to preload PromptRepository cache
     */
    @Query("""
        SELECT promptId
        FROM engagement_state
        WHERE isFavorited = 1
    """)
    suspend fun getAllFavoritedPromptIds(): List<String>

    /* ---------------------------------------------------- */
    /* UPSERT (Single source write) */
    /* ---------------------------------------------------- */

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: EngagementEntity)

    /* ---------------------------------------------------- */
    /* LIKE / FAVORITE ACTIONS */
    /* ---------------------------------------------------- */

    @Query("""
        UPDATE engagement_state
        SET isLiked = :liked,
            updatedAt = :updatedAt
        WHERE promptId = :id
    """)
    suspend fun updateLike(
        id: String,
        liked: Boolean,
        updatedAt: Long
    )

    @Query("""
        UPDATE engagement_state
        SET isFavorited = :fav,
            updatedAt = :updatedAt
        WHERE promptId = :id
    """)
    suspend fun updateFavorite(
        id: String,
        fav: Boolean,
        updatedAt: Long
    )

    /* ---------------------------------------------------- */
    /* VIEW COUNT (Local, atomic increment) */
    /* ---------------------------------------------------- */

    @Query("""
        UPDATE engagement_state
        SET localViewCount = localViewCount + 1,
            updatedAt = :updatedAt
        WHERE promptId = :id
    """)
    suspend fun incrementView(
        id: String,
        updatedAt: Long
    )

    /* ---------------------------------------------------- */
    /* 🔥 REACTIVE STREAMS (CRITICAL FOR UI) */
    /* ---------------------------------------------------- */

    /**
     * Observe ALL engagement states
     * Used by:
     * - All Prompts Screen
     * - Detail Screen
     * - Like / Favorite icon sync
     */
    @Query("SELECT * FROM engagement_state")
    fun observeAll(): Flow<List<EngagementEntity>>

    /**
     * 🔥 FAVORITES ONLY (ORDER PRESERVED)
     * Used by:
     * - Favorites Screen
     * - Bookmarked lists
     */
    @Query("""
        SELECT * FROM engagement_state
        WHERE isFavorited = 1
        ORDER BY updatedAt DESC
    """)
    fun observeFavorites(): Flow<List<EngagementEntity>>
}
