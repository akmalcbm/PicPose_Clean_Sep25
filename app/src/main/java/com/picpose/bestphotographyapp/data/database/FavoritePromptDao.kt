package com.picpose.bestphotographyapp.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FavoritePromptDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun addToFavorites(f: FavoritePrompt)

    @Query("DELETE FROM favorite_prompts WHERE prompt_id = :promptId")
    fun removeFromFavorites(promptId: String)

    @Query("SELECT COUNT(*) FROM favorite_prompts WHERE prompt_id = :promptId")
    fun isFavoriteCount(promptId: String): Int

    // convenience that returns boolean
    fun isFavorite(promptId: String): Boolean {
        return isFavoriteCount(promptId) > 0
    }

    @Query("SELECT * FROM favorite_prompts ORDER BY favoritedAt DESC")
    fun getAllFavorites(): List<FavoritePrompt>

    @Query("SELECT COUNT(*) FROM favorite_prompts")
    fun getFavoriteCount(): Int
}
