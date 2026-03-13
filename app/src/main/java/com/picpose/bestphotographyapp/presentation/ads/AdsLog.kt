/**
 * ---
 * File: AdsLog.kt
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

package com.picpose.bestphotographyapp.presentation.ads

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.picpose.bestphotographyapp.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.IOException

object AdsLog {

    const val TAG_INIT = "AdsInit"
    const val TAG_REPO = "AdsRepo"
    const val TAG_CACHE = "AdsCache"
    const val TAG_MANAGER = "AdsManager"
    const val TAG_FREQ = "AdsFreq"
    const val TAG_BANNER = "AdMobBanner"
    const val TAG_INTER = "AdMobInter"
    const val TAG_REWARD = "AdMobReward"
    const val TAG_NATIVE = "AdMobNative"
    const val TAG_UI = "AdsUI"

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val Context.adsLogDataStore: DataStore<Preferences> by preferencesDataStore("ads_log_settings")
    private val VERBOSE_KEY = booleanPreferencesKey("ads_verbose_logging_enabled")

    @Volatile
    private var initialized = false
    @Volatile
    private var verboseEnabled = BuildConfig.DEBUG

    fun init(context: Context) {
        if (initialized) return
        initialized = true

        if (!BuildConfig.DEBUG) {
            verboseEnabled = false
            return
        }

        appScope.launch {
            val saved = context.applicationContext.adsLogDataStore.data
                .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
                .map { prefs -> prefs[VERBOSE_KEY] ?: true }
                .first()
            verboseEnabled = saved
            Log.i(TAG_INIT, "[AdsInit] verbose_logging=$saved")
        }
    }

    fun setVerbose(context: Context, enabled: Boolean) {
        if (!BuildConfig.DEBUG) return
        verboseEnabled = enabled
        appScope.launch {
            context.applicationContext.adsLogDataStore.edit { prefs ->
                prefs[VERBOSE_KEY] = enabled
            }
        }
    }

    fun isVerboseEnabled(): Boolean {
        if (!BuildConfig.DEBUG) return false
        return verboseEnabled
    }

    fun d(tag: String, msg: String) {
        if (BuildConfig.DEBUG && verboseEnabled) Log.d(tag, msg)
    }

    fun i(tag: String, msg: String) {
        if (BuildConfig.DEBUG && verboseEnabled) Log.i(tag, msg)
    }

    fun w(tag: String, msg: String) {
        if (BuildConfig.DEBUG && verboseEnabled) Log.w(tag, msg)
    }

    fun e(tag: String, msg: String, tr: Throwable? = null) {
        if (tr != null) Log.e(tag, msg, tr) else Log.e(tag, msg)
    }

    fun maskAdUnitId(adUnitId: String?): String {
        if (adUnitId.isNullOrBlank()) return "null"
        val clean = adUnitId.trim()
        if (clean.length <= 14) return "${clean.take(4)}...${clean.takeLast(2)}"
        return "${clean.take(10)}...${clean.takeLast(4)}"
    }

    fun maskDeviceId(deviceId: String?): String {
        if (deviceId.isNullOrBlank()) return "null"
        return "***${deviceId.takeLast(4)}"
    }
}
