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
 * DataStore for user session management
 */
class UserSessionManager(private val context: Context) {

    companion object {
        private val Context.userDataStore: DataStore<Preferences> by preferencesDataStore("user_session")
        private val USER_ID_KEY = stringPreferencesKey("user_id")
        private val USER_EMAIL_KEY = stringPreferencesKey("user_email")
        private val USER_NAME_KEY = stringPreferencesKey("user_name")
        private val USER_PROFILE_PICTURE_KEY = stringPreferencesKey("user_profile_picture")
        private val USER_TOKEN_KEY = stringPreferencesKey("user_token")
        private val IS_LOGGED_IN_KEY = booleanPreferencesKey("is_logged_in")
    }

    val isLoggedIn: Flow<Boolean> = context.userDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[IS_LOGGED_IN_KEY] ?: false
        }

    val userId: Flow<String?> = context.userDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[USER_ID_KEY]
        }

    val userEmail: Flow<String?> = context.userDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[USER_EMAIL_KEY]
        }

    val userName: Flow<String?> = context.userDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[USER_NAME_KEY]
        }

    val userProfilePicture: Flow<String?> = context.userDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[USER_PROFILE_PICTURE_KEY]
        }

    suspend fun saveUserSession(
        userId: String,
        email: String,
        name: String,
        profilePicture: String? = null,
        token: String? = null
    ) {
        context.userDataStore.edit { preferences ->
            preferences[USER_ID_KEY] = userId
            preferences[USER_EMAIL_KEY] = email
            preferences[USER_NAME_KEY] = name
            profilePicture?.let { preferences[USER_PROFILE_PICTURE_KEY] = it }
            token?.let { preferences[USER_TOKEN_KEY] = it }
            preferences[IS_LOGGED_IN_KEY] = true
        }
    }

    suspend fun clearUserSession() {
        context.userDataStore.edit { preferences ->
            preferences.clear()
        }
    }

    suspend fun updateUserProfile(name: String, profilePicture: String?) {
        context.userDataStore.edit { preferences ->
            preferences[USER_NAME_KEY] = name
            profilePicture?.let { preferences[USER_PROFILE_PICTURE_KEY] = it }
        }
    }
}
