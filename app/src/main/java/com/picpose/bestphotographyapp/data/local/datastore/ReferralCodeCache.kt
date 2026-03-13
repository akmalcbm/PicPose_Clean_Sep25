/**
 * ---
 * File: ReferralCodeCache.kt
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
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Singleton
class ReferralCodeCache @Inject constructor(
    private val context: Context,
) {
    private companion object {
        private val Context.referralCodeStore by preferencesDataStore(name = "referral_code_cache")
        val REFERRAL_CODE = stringPreferencesKey("referral_code")
        val REFERRAL_OWNER_USER_ID = stringPreferencesKey("referral_owner_user_id")
    }

    fun cachedCode(userId: String): Flow<String?> = context.referralCodeStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            val ownerId = preferences[REFERRAL_OWNER_USER_ID]
            if (ownerId == userId) preferences[REFERRAL_CODE] else null
        }

    suspend fun readOnce(userId: String): String? = cachedCode(userId).first()

    suspend fun save(userId: String, code: String) {
        context.referralCodeStore.edit { preferences ->
            preferences[REFERRAL_OWNER_USER_ID] = userId
            preferences[REFERRAL_CODE] = code
        }
    }

    suspend fun clear() {
        context.referralCodeStore.edit { preferences ->
            preferences.remove(REFERRAL_OWNER_USER_ID)
            preferences.remove(REFERRAL_CODE)
        }
    }
}
