package com.picpose.bestphotographyapp.data.database.dao

import androidx.room.*
import com.picpose.bestphotographyapp.data.database.entities.EngagementEntity
import kotlinx.coroutines.flow.Flow   // ✅ REQUIRED

@Dao
interface EngagementDao {

    /* ------------------------------------ */
    /* SNAPSHOT READ (one-time) */
    /* ------------------------------------ */

    @Query("SELECT * FROM engagement_state")
    suspend fun getAll(): List<EngagementEntity>

    @Query("SELECT * FROM engagement_state WHERE promptId = :id")
    suspend fun getById(id: String): EngagementEntity?

    /* ------------------------------------ */
    /* UPSERT */
    /* ------------------------------------ */

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: EngagementEntity)

    /* ------------------------------------ */
    /* LIKE / FAVORITE */
    /* ------------------------------------ */

    @Query("UPDATE engagement_state SET isLiked = :liked WHERE promptId = :id")
    suspend fun updateLike(id: String, liked: Boolean)

    @Query("UPDATE engagement_state SET isFavorited = :fav WHERE promptId = :id")
    suspend fun updateFavorite(id: String, fav: Boolean)

    /* ------------------------------------ */
    /* VIEW */
    /* ------------------------------------ */

    @Query("""
        UPDATE engagement_state
        SET localViewCount = localViewCount + 1
        WHERE promptId = :id
    """)
    suspend fun incrementView(id: String)

    /* ------------------------------------ */
    /* 🔥 REACTIVE OBSERVER (MOST IMPORTANT) */
    /* ------------------------------------ */

    @Query("SELECT * FROM engagement_state")
    fun observeAll(): Flow<List<EngagementEntity>>
}
