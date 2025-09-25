package com.picpose.bestphotographyapp.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FavoritePromptDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addToFavorites(f: FavoritePrompt) // ✅ Added suspend

    @Query("DELETE FROM favorite_prompts WHERE prompt_id = :promptId")
    suspend fun removeFromFavorites(promptId: String) // ✅ Added suspend

    @Query("SELECT COUNT(*) FROM favorite_prompts WHERE prompt_id = :promptId")
    suspend fun isFavoriteCount(promptId: String): Int // ✅ Added suspend

    // convenience that returns boolean
    suspend fun isFavorite(promptId: String): Boolean {
        return isFavoriteCount(promptId) > 0
    }

    @Query("SELECT * FROM favorite_prompts ORDER BY favoritedAt DESC")
    suspend fun getAllFavorites(): List<FavoritePrompt> // ✅ Added suspend

    @Query("SELECT COUNT(*) FROM favorite_prompts")
    suspend fun getFavoriteCount(): Int // ✅ Added suspend
}