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
