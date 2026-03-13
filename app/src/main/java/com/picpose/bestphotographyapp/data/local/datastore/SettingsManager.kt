/**
 * ---
 * File: SettingsManager.kt
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
        private val BG_REMOVAL_DISCLOSURE_ACCEPTED_KEY =
            booleanPreferencesKey("bg_removal_disclosure_accepted")

        // 🔔 Notification permission tracking
        private val NOTIFICATION_PERMISSION_REQUESTED_KEY =
            booleanPreferencesKey("notification_permission_requested")
        private val NOTIFICATION_PERMISSION_DENIED_AT_OPEN_KEY =
            intPreferencesKey("notification_permission_denied_at_open")
        private val NOTIFICATION_PERMISSION_LAST_PROMPT_OPEN_KEY =
            intPreferencesKey("notification_permission_last_prompt_open")
        private val APP_OPEN_COUNT_KEY = intPreferencesKey("app_open_count")
    }

    /** Theme mode flow */
    val themeMode: Flow<ThemeMode> = context.settingsDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> ThemeMode.fromStorage(prefs[THEME_MODE_KEY]) }

    /** Language flow */
    val language: Flow<String> = context.settingsDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> prefs[LANGUAGE_KEY] ?: "en" }

    /** Notification flow */
    val notificationsEnabled: Flow<Boolean> = context.settingsDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> prefs[NOTIFICATIONS_ENABLED_KEY] ?: true }

    /** Save theme mode */
    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { prefs ->
            prefs[THEME_MODE_KEY] = mode.storageValue
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

    /** Notification permission requested */
    val notificationPermissionRequested: Flow<Boolean> = context.settingsDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> prefs[NOTIFICATION_PERMISSION_REQUESTED_KEY] ?: false }

    /** Background-removal cloud processing disclosure */
    val bgRemovalDisclosureAccepted: Flow<Boolean> = context.settingsDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> prefs[BG_REMOVAL_DISCLOSURE_ACCEPTED_KEY] ?: false }

    /** App open count */
    val appOpenCount: Flow<Int> = context.settingsDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> prefs[APP_OPEN_COUNT_KEY] ?: 0 }

    /** Open count when user last denied notifications */
    val notificationPermissionDeniedAtOpen: Flow<Int> = context.settingsDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> prefs[NOTIFICATION_PERMISSION_DENIED_AT_OPEN_KEY] ?: -1 }

    /** Open count when we last showed a permission prompt */
    val notificationPermissionLastPromptOpen: Flow<Int> = context.settingsDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> prefs[NOTIFICATION_PERMISSION_LAST_PROMPT_OPEN_KEY] ?: -1 }

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

    /** Mark notification permission requested */
    suspend fun setNotificationPermissionRequested(requested: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[NOTIFICATION_PERMISSION_REQUESTED_KEY] = requested
        }
    }

    /** Increment app open count */
    suspend fun incrementAppOpenCount() {
        context.settingsDataStore.edit { prefs ->
            val current = prefs[APP_OPEN_COUNT_KEY] ?: 0
            prefs[APP_OPEN_COUNT_KEY] = current + 1
        }
    }

    /** Record open count when user denied notification permission */
    suspend fun setNotificationPermissionDeniedAtOpen(openCount: Int) {
        context.settingsDataStore.edit { prefs ->
            prefs[NOTIFICATION_PERMISSION_DENIED_AT_OPEN_KEY] = openCount
        }
    }

    /** Record open count when permission prompt was shown */
    suspend fun setNotificationPermissionLastPromptOpen(openCount: Int) {
        context.settingsDataStore.edit { prefs ->
            prefs[NOTIFICATION_PERMISSION_LAST_PROMPT_OPEN_KEY] = openCount
        }
    }

    suspend fun setBgRemovalDisclosureAccepted(accepted: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[BG_REMOVAL_DISCLOSURE_ACCEPTED_KEY] = accepted
        }
    }


}
