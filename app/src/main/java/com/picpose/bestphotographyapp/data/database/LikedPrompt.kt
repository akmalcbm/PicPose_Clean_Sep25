package com.picpose.bestphotographyapp.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "liked_prompts")
data class LikedPrompt(
    @PrimaryKey val promptId: String,     // unique prompt id from server
    val likedAt: Long = System.currentTimeMillis()
)
