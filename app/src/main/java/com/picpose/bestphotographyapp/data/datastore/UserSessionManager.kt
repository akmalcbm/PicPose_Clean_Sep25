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
 * Updated for real-time UI sync across Profile & Settings screens.
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
        private val HAS_SKIPPED_AUTH_KEY = booleanPreferencesKey("has_skipped_auth")
    }

    /** ✅ Emit helper with safe fallback for IOException */
    private fun <T> Flow<Preferences>.safeMap(transform: suspend (Preferences) -> T): Flow<T> =
        catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }.map(transform)

    /** ✅ Observables */
    val isLoggedIn: Flow<Boolean> = context.userDataStore.data.safeMap {
        it[IS_LOGGED_IN_KEY] ?: false
    }

    val hasSkippedAuth: Flow<Boolean> = context.userDataStore.data.safeMap {
        it[HAS_SKIPPED_AUTH_KEY] ?: false
    }

    val userId: Flow<String?> = context.userDataStore.data.safeMap { it[USER_ID_KEY] }
    val userEmail: Flow<String?> = context.userDataStore.data.safeMap { it[USER_EMAIL_KEY] }
    val userName: Flow<String?> = context.userDataStore.data.safeMap { it[USER_NAME_KEY] }
    val userProfilePicture: Flow<String?> = context.userDataStore.data.safeMap { it[USER_PROFILE_PICTURE_KEY] }
    val userToken: Flow<String?> = context.userDataStore.data.safeMap { it[USER_TOKEN_KEY] }

    /**
     * ✅ Save user session after login or register
     * Automatically sets `is_logged_in = true`
     */
    suspend fun saveUserSession(
        userId: String,
        email: String,
        name: String,
        profilePicture: String? = null,
        token: String? = null
    ) {
        context.userDataStore.edit { prefs ->
            prefs[USER_ID_KEY] = userId
            prefs[USER_EMAIL_KEY] = email
            prefs[USER_NAME_KEY] = name
            profilePicture?.let { prefs[USER_PROFILE_PICTURE_KEY] = it }
            token?.let { prefs[USER_TOKEN_KEY] = it }
            prefs[IS_LOGGED_IN_KEY] = true
            prefs[HAS_SKIPPED_AUTH_KEY] = false // reset skip if user logs in
        }
    }

    /**
     * ✅ Update only name, photo, or bio after Edit Profile
     */
    suspend fun updateUserProfile(name: String, profilePicture: String?) {
        context.userDataStore.edit { prefs ->
            prefs[USER_NAME_KEY] = name
            profilePicture?.let { prefs[USER_PROFILE_PICTURE_KEY] = it }
        }
    }

    /**
     * ✅ Clear all user data on logout
     */
    suspend fun clearUserSession() {
        context.userDataStore.edit { prefs -> prefs.clear() }
    }

    /**
     * ✅ Set skip auth flag
     */
    suspend fun setSkipAuth(skipped: Boolean) {
        context.userDataStore.edit { prefs ->
            prefs[HAS_SKIPPED_AUTH_KEY] = skipped
        }
    }
}
