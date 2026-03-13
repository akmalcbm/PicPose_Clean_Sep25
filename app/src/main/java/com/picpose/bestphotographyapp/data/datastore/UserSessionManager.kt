/**
 * ---
 * File: UserSessionManager.kt
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
 * Clean & fixed DataStore for user session.
 * - Bio stored as REAL server bio (nullable)
 * - Never stores fallback or empty-string bio
 * - Profile always consistent after login/logout
 */
class UserSessionManager(private val context: Context) {

    companion object {
        private val Context.userDataStore: DataStore<Preferences> by preferencesDataStore("user_session")

        private val USER_ID_KEY = stringPreferencesKey("user_id")
        private val USER_EMAIL_KEY = stringPreferencesKey("user_email")
        private val USER_NAME_KEY = stringPreferencesKey("user_name")
        private val USER_PROFILE_PICTURE_KEY = stringPreferencesKey("user_profile_picture")
        private val USER_BIO_KEY = stringPreferencesKey("user_bio")  // nullable allowed
        private val USER_TOKEN_KEY = stringPreferencesKey("user_token")
        private val USER_EMAIL_VERIFIED_KEY = booleanPreferencesKey("user_email_verified")

        private val IS_LOGGED_IN_KEY = booleanPreferencesKey("is_logged_in")
        private val HAS_SKIPPED_AUTH_KEY = booleanPreferencesKey("has_skipped_auth")
        private val PRIVACY_TERMS_ACCEPTED_KEY = booleanPreferencesKey("privacy_terms_accepted")
    }

    /** helper */
    private fun <T> Flow<Preferences>.safeMap(transform: suspend (Preferences) -> T): Flow<T> =
        catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map(transform)

    /** Observables */
    val isLoggedIn: Flow<Boolean> = context.userDataStore.data.safeMap {
        it[IS_LOGGED_IN_KEY] ?: false
    }

    val hasSkippedAuth: Flow<Boolean> = context.userDataStore.data.safeMap {
        it[HAS_SKIPPED_AUTH_KEY] ?: false
    }

    val hasAcceptedPrivacyTerms: Flow<Boolean> = context.userDataStore.data.safeMap {
        it[PRIVACY_TERMS_ACCEPTED_KEY] ?: false
    }

    val userId: Flow<String?> = context.userDataStore.data.safeMap { it[USER_ID_KEY] }
    val userEmail: Flow<String?> = context.userDataStore.data.safeMap { it[USER_EMAIL_KEY] }
    val userName: Flow<String?> = context.userDataStore.data.safeMap { it[USER_NAME_KEY] }

    val userProfilePicture: Flow<String?> =
        context.userDataStore.data.safeMap { it[USER_PROFILE_PICTURE_KEY] }

    /** ❗ FIX — Keep bio nullable, do NOT return "" */
    val userBio: Flow<String?> = context.userDataStore.data.safeMap {
        it[USER_BIO_KEY]     // null allowed
    }

    val userToken: Flow<String?> = context.userDataStore.data.safeMap {
        it[USER_TOKEN_KEY]
    }

    val userEmailVerified: Flow<Boolean> = context.userDataStore.data.safeMap {
        it[USER_EMAIL_VERIFIED_KEY] ?: false
    }

    /**
     * Save session after login or profile update
     * Empty bio is intentionally NOT saved (null stays null)
     */
    suspend fun saveUserSession(
        userId: String,
        email: String,
        name: String,
        profilePicture: String? = null,
        bio: String? = null,     // store real only
        token: String? = null,
        emailVerified: Boolean = false
    ) {
        context.userDataStore.edit { prefs ->

            prefs[USER_ID_KEY] = userId
            prefs[USER_EMAIL_KEY] = email
            prefs[USER_NAME_KEY] = name

            profilePicture?.let { prefs[USER_PROFILE_PICTURE_KEY] = it }

            // ❗ SAVE ONLY REAL BIO (null allowed)
            if (!bio.isNullOrBlank()) {
                prefs[USER_BIO_KEY] = bio
            } else {
                prefs.remove(USER_BIO_KEY)
            }

            token?.let { prefs[USER_TOKEN_KEY] = it }
            prefs[USER_EMAIL_VERIFIED_KEY] = emailVerified

            prefs[IS_LOGGED_IN_KEY] = true
            prefs[HAS_SKIPPED_AUTH_KEY] = false
        }
    }

    /**
     * Update limited fields (Edit Profile)
     * Never store empty-string bio; remove key instead.
     */
    suspend fun updateUserProfile(
        name: String? = null,
        profilePicture: String? = null,
        bio: String? = null
    ) {
        context.userDataStore.edit { prefs ->
            name?.let { prefs[USER_NAME_KEY] = it }
            profilePicture?.let { prefs[USER_PROFILE_PICTURE_KEY] = it }

            if (bio != null && bio.isNotBlank()) {
                prefs[USER_BIO_KEY] = bio
            } else if (bio == null || bio.isBlank()) {
                prefs.remove(USER_BIO_KEY)
            }
        }
    }

    suspend fun setSkipAuth(skipped: Boolean) {
        context.userDataStore.edit { prefs ->
            prefs[HAS_SKIPPED_AUTH_KEY] = skipped
        }
    }

    suspend fun setPrivacyTermsAccepted(accepted: Boolean) {
        context.userDataStore.edit { prefs ->
            prefs[PRIVACY_TERMS_ACCEPTED_KEY] = accepted
        }
    }

    /**
     * Clear full session
     */
    suspend fun clearUserSession() {
        context.userDataStore.edit { prefs ->
            val acceptedPrivacyTerms = prefs[PRIVACY_TERMS_ACCEPTED_KEY] ?: false
            prefs.clear()
            prefs[PRIVACY_TERMS_ACCEPTED_KEY] = acceptedPrivacyTerms
            prefs[HAS_SKIPPED_AUTH_KEY] = false
            prefs[USER_EMAIL_VERIFIED_KEY] = false
        }
    }
}
