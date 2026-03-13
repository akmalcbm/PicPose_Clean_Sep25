/**
 * ---
 * File: Converters.kt
 * Layer: Data (Room)
 * Project: PicPose
 *
 * Purpose:
 * Supports the PicPose app with feature-specific models, helpers, or UI building blocks.
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

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromStringList(list: List<String>?): String? {
        return if (list == null) null else gson.toJson(list)
    }

    @TypeConverter
    fun toStringList(value: String?): List<String>? {
        if (value == null) return null
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson<List<String>>(value, type)
    }
}
