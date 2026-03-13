/**
 * ---
 * File: GuideLikeStore.kt
 * Layer: Data
 * Project: PicPose
 *
 * Purpose:
 * Wraps DataStore or preference-like persistence used for lightweight local settings and session state.
 *
 * Interactions:
 * Used by ViewModels, repositories, or app startup classes for lightweight persisted preferences and session flags.
 *
 * Data Flow:
 * Feature-specific flow; see adjacent ViewModels and repositories for the full path.
 *
 * Maintainer Notes:
 * - Keep responsibilities narrow and update this header when the file grows into a larger abstraction.
 * ---
 */

package com.picpose.bestphotographyapp.data.local.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GuideLikeStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val Context.guideLikesDataStore by preferencesDataStore(name = "guide_likes")
    }

    suspend fun isLiked(guideId: String): Boolean {
        if (guideId.isBlank()) return false
        val key = booleanPreferencesKey(prefKeyFor(guideId))
        return context.guideLikesDataStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .first()[key] ?: false
    }

    suspend fun setLiked(guideId: String, liked: Boolean) {
        if (guideId.isBlank()) return
        val key = booleanPreferencesKey(prefKeyFor(guideId))
        context.guideLikesDataStore.edit { prefs ->
            prefs[key] = liked
        }
    }

    suspend fun getLocalViewCount(guideId: String): Int {
        if (guideId.isBlank()) return 0
        val key = intPreferencesKey(viewKeyFor(guideId))
        return context.guideLikesDataStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .first()[key]?.coerceAtLeast(0) ?: 0
    }

    suspend fun incrementViewCount(guideId: String): Int {
        if (guideId.isBlank()) return 0
        val key = intPreferencesKey(viewKeyFor(guideId))
        var newCount = 0
        context.guideLikesDataStore.edit { prefs ->
            newCount = (prefs[key] ?: 0).coerceAtLeast(0) + 1
            prefs[key] = newCount
        }
        return newCount
    }

    private fun prefKeyFor(guideId: String): String =
        "guide_like_${guideId.trim().lowercase()}"

    private fun viewKeyFor(guideId: String): String =
        "guide_view_count_${guideId.trim().lowercase()}"
}
