/**
 * ---
 * File: AdsFrequencyManager.kt
 * Layer: Presentation (UI)
 * Project: PicPose
 *
 * Purpose:
 * Supports the PicPose app with feature-specific models, helpers, or UI building blocks.
 *
 * Interactions:
 * Works with nearby classes in the same layer to keep responsibilities separated and easier to maintain.
 *
 * Data Flow:
 * Feature-specific flow; see adjacent ViewModels and repositories for the full path.
 *
 * Maintainer Notes:
 * - Keep SDK-specific code isolated here so feature screens remain testable.
 * - TODO: Add analytics and remote-config driven rollout controls where appropriate.
 * ---
 */

package com.picpose.bestphotographyapp.components.ads

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

object AdsFrequencyManager {

    private const val HOUR_MS = 60 * 60 * 1000L

    @Volatile
    private var store: DataStore<Preferences>? = null

    private val lock = Mutex()

    private val Context.adsFrequencyDataStore: DataStore<Preferences> by preferencesDataStore(
        name = "ads_frequency_store"
    )

    fun initialize(context: Context) {
        if (store != null) return
        synchronized(this) {
            if (store == null) {
                store = context.applicationContext.adsFrequencyDataStore
                AdsLog.i(AdsLog.TAG_FREQ, "[AdsFreq] action=initialize status=OK")
            }
        }
    }

    fun canShow(placementKey: String, limitPerHour: Int): Boolean {
        val dataStore = store ?: return true
        val normalizedLimit = limitPerHour.coerceAtLeast(1)

        return runBlocking(Dispatchers.IO) {
            lock.withLock {
                val now = System.currentTimeMillis()
                val countKey = intPreferencesKey("${placementKey}_count")
                val windowStartKey = longPreferencesKey("${placementKey}_window_start")

                val prefs = dataStore.data
                    .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
                    .first()

                val currentCount = prefs[countKey] ?: 0
                val windowStart = prefs[windowStartKey] ?: 0L

                // Rolling-hour window anchored to each placement's last window start.
                if (windowStart <= 0L || now - windowStart >= HOUR_MS) {
                    dataStore.edit { mutable ->
                        mutable[windowStartKey] = now
                        mutable[countKey] = 0
                    }
                    AdsLog.d(
                        AdsLog.TAG_FREQ,
                        "[AdsFreq] placement=$placementKey canShow=true currentCount=0 windowStart=$now limitPerHour=$normalizedLimit reason=WINDOW_RESET"
                    )
                    return@withLock true
                }
                val canShow = currentCount < normalizedLimit
                AdsLog.d(
                    AdsLog.TAG_FREQ,
                    "[AdsFreq] placement=$placementKey canShow=$canShow currentCount=$currentCount windowStart=$windowStart limitPerHour=$normalizedLimit"
                )
                canShow
            }
        }
    }

    fun markShown(placementKey: String) {
        val dataStore = store ?: return

        runBlocking(Dispatchers.IO) {
            lock.withLock {
                val now = System.currentTimeMillis()
                val countKey = intPreferencesKey("${placementKey}_count")
                val windowStartKey = longPreferencesKey("${placementKey}_window_start")

                val current = dataStore.data
                    .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
                    .map { prefs ->
                        (prefs[countKey] ?: 0) to (prefs[windowStartKey] ?: 0L)
                    }
                    .first()

                val currentCount = current.first
                val windowStart = current.second
                val shouldResetWindow = windowStart <= 0L || now - windowStart >= HOUR_MS

                dataStore.edit { mutable ->
                    if (shouldResetWindow) {
                        mutable[windowStartKey] = now
                        mutable[countKey] = 1
                        AdsLog.d(
                            AdsLog.TAG_FREQ,
                            "[AdsFreq] placement=$placementKey markShown=true newCount=1 windowStart=$now reason=WINDOW_RESET"
                        )
                    } else {
                        val updated = currentCount + 1
                        mutable[countKey] = updated
                        AdsLog.d(
                            AdsLog.TAG_FREQ,
                            "[AdsFreq] placement=$placementKey markShown=true newCount=$updated windowStart=$windowStart reason=INCREMENT"
                        )
                    }
                }
            }
        }
    }
}
