package com.picpose.bestphotographyapp.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.picpose.bestphotographyapp.data.models.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import kotlinx.coroutines.flow.first


/**
 * DataStore cache for AppSettings
 * Provides offline support and quick access to cached settings
 */
class AppSettingsCache(private val context: Context) {

    companion object {
        private val Context.appSettingsDataStore: DataStore<Preferences> by preferencesDataStore("app_settings_cache")
        private val APP_SETTINGS_JSON_KEY = stringPreferencesKey("app_settings_json")
        private val LAST_UPDATED_KEY = longPreferencesKey("last_updated")
        private val USER_ID_KEY = stringPreferencesKey("user_id")

    }

    private val gson = Gson()

    /**
     * Get cached app settings as Flow
     */
    val cachedSettings: Flow<AppSettings?> = context.appSettingsDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val json = preferences[APP_SETTINGS_JSON_KEY]
            if (json != null) {
                try {
                    gson.fromJson(json, AppSettings::class.java)
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }
        }

    /**
     * Get last updated timestamp
     */
    val lastUpdated: Flow<Long> = context.appSettingsDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[LAST_UPDATED_KEY] ?: 0L
        }

    /**
     * Save app settings to cache
     */
    suspend fun saveSettings(settings: AppSettings) {
        try {
            val json = gson.toJson(settings)
            context.appSettingsDataStore.edit { preferences ->
                preferences[APP_SETTINGS_JSON_KEY] = json
                preferences[LAST_UPDATED_KEY] = System.currentTimeMillis()
            }
        } catch (e: Exception) {
            // Log error but don't fail the operation
            android.util.Log.e("AppSettingsCache", "Failed to cache settings: ${e.message}")
        }
    }

    suspend fun getOrCreateUserId(): String {
        val prefs = context.appSettingsDataStore.data.first()

        val existing = prefs[USER_ID_KEY]
        if (!existing.isNullOrBlank()) {
            return existing
        }

        // Generate anonymous stable user id
        val newUserId = "guest_" + java.util.UUID.randomUUID().toString()

        context.appSettingsDataStore.edit { preferences ->
            preferences[USER_ID_KEY] = newUserId
        }

        return newUserId
    }


    /**
     * Clear cached settings
     */
    suspend fun clearCache() {
        context.appSettingsDataStore.edit { preferences ->
            preferences.remove(APP_SETTINGS_JSON_KEY)
            preferences.remove(LAST_UPDATED_KEY)
        }
    }


}
