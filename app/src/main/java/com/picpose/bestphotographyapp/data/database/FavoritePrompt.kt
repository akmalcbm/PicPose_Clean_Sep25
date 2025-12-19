package com.picpose.bestphotographyapp.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_prompts")
data class FavoritePrompt(
    @PrimaryKey
    val promptId: String,              // server id (唯一)
    val favoritedAt: Long = System.currentTimeMillis()
)



/*
package com.picpose.bestphotographyapp.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_prompts")
data class FavoritePrompt(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,                 // local PK
    @ColumnInfo(name = "prompt_id")
    val promptId: String,              // id from server (unique)
    val title: String?,
    val shortPrompt: String?,
    val fullPrompt: String?,
    val imageUrl: String?,
    val category: String?,
    val likes: Int? = 0,
    val isPopular: Boolean? = false,
    val tags: List<String>? = null,    // Room can't store list directly - see converter
    val status: String? = null,
    val priority: Int? = 0,
    val dateAdded: Long? = null,
    val favoritedAt: Long? = null
)
*/
