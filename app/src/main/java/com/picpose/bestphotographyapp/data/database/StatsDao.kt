package com.picpose.bestphotographyapp.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StatsDao {
    @Query("SELECT * FROM quick_stats LIMIT 1")
    fun getStats(): Flow<StatsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStats(stats: StatsEntity)

    @Query("DELETE FROM quick_stats")
    suspend fun clearStats()
}
