package com.picpose.bestphotographyapp.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.picpose.bestphotographyapp.data.models.v2.RewardsHubDto
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Singleton
class RewardsHubCache @Inject constructor(
    private val context: Context,
    private val gson: Gson,
) {
    private companion object {
        private val Context.rewardsHubStore by preferencesDataStore(name = "rewards_hub_cache")
        val REWARDS_HUB_JSON = stringPreferencesKey("rewards_hub_json")
        val REWARDS_HUB_UPDATED_AT = longPreferencesKey("rewards_hub_updated_at")
    }

    val cachedHub: Flow<RewardsHubDto?> = context.rewardsHubStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences: Preferences ->
            preferences[REWARDS_HUB_JSON]?.let { raw: String ->
                runCatching { gson.fromJson(raw, RewardsHubDto::class.java) }.getOrNull()
            }
        }

    val lastUpdated: Flow<Long> = context.rewardsHubStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences: Preferences -> preferences[REWARDS_HUB_UPDATED_AT] ?: 0L }

    suspend fun save(hub: RewardsHubDto) {
        context.rewardsHubStore.edit { preferences ->
            preferences[REWARDS_HUB_JSON] = gson.toJson(hub)
            preferences[REWARDS_HUB_UPDATED_AT] = System.currentTimeMillis()
        }
    }

    suspend fun readOnce(): RewardsHubDto? = cachedHub.first()

    suspend fun clear() {
        context.rewardsHubStore.edit { preferences ->
            preferences.remove(REWARDS_HUB_JSON)
            preferences.remove(REWARDS_HUB_UPDATED_AT)
        }
    }
}
