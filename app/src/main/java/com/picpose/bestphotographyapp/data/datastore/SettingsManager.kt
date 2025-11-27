package com.picpose.bestphotographyapp.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * DataStore for app settings and preferences
 */
class SettingsManager(private val context: Context) {

    companion object {
        private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore("app_settings")

        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")   // system | light | dark
        private val LANGUAGE_KEY = stringPreferencesKey("language")
        private val NOTIFICATIONS_ENABLED_KEY = booleanPreferencesKey("notifications_enabled")
    }

    /** Theme mode flow */
    val themeMode: Flow<String> = context.settingsDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> prefs[THEME_MODE_KEY] ?: "system" }

    /** Language flow */
    val language: Flow<String> = context.settingsDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> prefs[LANGUAGE_KEY] ?: "en" }

    /** Notification flow */
    val notificationsEnabled: Flow<Boolean> = context.settingsDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> prefs[NOTIFICATIONS_ENABLED_KEY] ?: true }

    /** Save theme mode */
    suspend fun setThemeMode(mode: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[THEME_MODE_KEY] = mode  // system | light | dark
        }
    }

    /** Save language */
    suspend fun setLanguage(languageCode: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[LANGUAGE_KEY] = languageCode
        }
    }

    /** Save notifications */
    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[NOTIFICATIONS_ENABLED_KEY] = enabled
        }
    }
}
