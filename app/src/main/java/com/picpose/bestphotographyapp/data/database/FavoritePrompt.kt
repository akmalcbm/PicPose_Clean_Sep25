/**
 * ---
 * File: FavoritePrompt.kt
 * Layer: Data (Room)
 * Project: PicPose
 *
 * Purpose:
 * Defines a Room entity or local persistence model stored inside the PicPose database.
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

package com.picpose.bestphotographyapp.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_prompts")
/**
 * Minimal Room entity for a favorited prompt.
 *
 * Only the prompt id and the timestamp are stored locally. Full prompt details
 * are resolved later from API/cache layers so this table stays small and stable.
 */
data class FavoritePrompt(
    @PrimaryKey
    val promptId: String,
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
