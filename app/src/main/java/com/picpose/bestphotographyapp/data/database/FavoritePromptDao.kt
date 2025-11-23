package com.picpose.bestphotographyapp.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FavoritePromptDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addToFavorites(f: FavoritePrompt) // ✅ FIXED: Added suspend

    @Query("DELETE FROM favorite_prompts WHERE prompt_id = :promptId")
    suspend fun removeFromFavorites(promptId: String) // ✅ FIXED: Added suspend

    @Query("SELECT COUNT(*) FROM favorite_prompts WHERE prompt_id = :promptId")
    suspend fun isFavoriteCount(promptId: String): Int // ✅ FIXED: Added suspend

    // ✅ FIXED: Made suspend and proper coroutine handling
    suspend fun isBookmarked(promptId: String): Boolean {
        return isFavoriteCount(promptId) > 0
    }

    @Query("SELECT * FROM favorite_prompts ORDER BY favoritedAt DESC")
    suspend fun getAllFavorites(): List<FavoritePrompt> // ✅ FIXED: Added suspend

    @Query("SELECT COUNT(*) FROM favorite_prompts")
    suspend fun getFavoriteCount(): Int // ✅ FIXED: Added suspend
}