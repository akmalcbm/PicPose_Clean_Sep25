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

        // 🔥 NEW — Gemini dialog preference
        private val SKIP_GEMINI_DIALOG_KEY = booleanPreferencesKey("skip_gemini_dialog")
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

    /** Gemini dialog skip flow */
    val skipGeminiDialog: Flow<Boolean> = context.settingsDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            prefs[SKIP_GEMINI_DIALOG_KEY] ?: false
        }

    /** Save Gemini dialog preference */
    suspend fun setSkipGeminiDialog(skip: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[SKIP_GEMINI_DIALOG_KEY] = skip
        }
    }

    /** Reset Gemini preferences */
    suspend fun resetGeminiDialogPreference() {
        context.settingsDataStore.edit { prefs ->
            prefs.remove(SKIP_GEMINI_DIALOG_KEY)
        }
    }


}
