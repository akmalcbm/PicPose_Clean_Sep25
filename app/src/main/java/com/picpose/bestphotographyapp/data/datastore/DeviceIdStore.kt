package com.picpose.bestphotographyapp.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.util.UUID

class DeviceIdStore(private val context: Context) {

    companion object {
        private val Context.deviceIdDataStore: DataStore<Preferences> by preferencesDataStore("device_id_store")
        private val DEVICE_ID_KEY = stringPreferencesKey("stable_device_id")
    }

    suspend fun getOrCreateDeviceId(): String {
        val existing = context.deviceIdDataStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .map { it[DEVICE_ID_KEY] }
            .first()

        if (!existing.isNullOrBlank()) return existing

        val newId = UUID.randomUUID().toString()
        context.deviceIdDataStore.edit { prefs ->
            if (prefs[DEVICE_ID_KEY].isNullOrBlank()) {
                prefs[DEVICE_ID_KEY] = newId
            }
        }

        return context.deviceIdDataStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .map { it[DEVICE_ID_KEY] ?: newId }
            .first()
    }
}
