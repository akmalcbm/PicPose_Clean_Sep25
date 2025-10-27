package com.picpose.bestphotographyapp.data.admob

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.picpose.bestphotographyapp.data.models.Admob
import com.picpose.bestphotographyapp.data.models.AppSettings
import com.picpose.bestphotographyapp.data.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Manages AdMob configuration with server-side fetching and local caching
 */
class AdMobConfigManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "AdMobConfigManager"
        private const val PREFS_NAME = "admob_config"
        private const val KEY_APP_ID = "app_id"
        private const val KEY_BANNER1_ID = "banner1_id"
        private const val KEY_BANNER2_ID = "banner2_id"
        private const val KEY_INTERSTITIAL1_ID = "interstitial1_id"
        private const val KEY_INTERSTITIAL2_ID = "interstitial2_id"
        private const val KEY_NATIVE1_ID = "native1_id"
        private const val KEY_NATIVE2_ID = "native2_id"
        private const val KEY_NATIVE3_ID = "native3_id"
        private const val KEY_REWARDED1_ID = "rewarded1_id"
        private const val KEY_LAST_UPDATE = "last_update"
        private const val CACHE_VALIDITY_MS = 24 * 60 * 60 * 1000L // 24 hours
        
        // Fallback test IDs (Google's test ad unit IDs)
        private const val TEST_APP_ID = "ca-app-pub-3940256099942544~3347511713"
        private const val TEST_BANNER_ID = "ca-app-pub-3940256099942544/6300978111"
        private const val TEST_INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712"
        private const val TEST_NATIVE_ID = "ca-app-pub-3940256099942544/2247696110"
        private const val TEST_REWARDED_ID = "ca-app-pub-3940256099942544/5224354917"
        
        @Volatile
        private var INSTANCE: AdMobConfigManager? = null
        
        fun getInstance(context: Context): AdMobConfigManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AdMobConfigManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * Fetch app settings from server with caching
     */
    suspend fun fetchAppSettings(): Flow<Result<AppSettings>> = flow {
        try {
            // Check if cached data is still valid
            val lastUpdate = prefs.getLong(KEY_LAST_UPDATE, 0)
            val currentTime = System.currentTimeMillis()
            
            if (currentTime - lastUpdate < CACHE_VALIDITY_MS) {
                // Return cached data if still valid
                val cachedSettings = getCachedSettings()
                if (cachedSettings != null) {
                    Log.d(TAG, "Returning cached AdMob settings")
                    emit(Result.success(cachedSettings))
                    return@flow
                }
            }
            
            // Fetch from server
            Log.d(TAG, "Fetching AdMob settings from server")
            val response = RetrofitClient.apiService.getAppSettings()
            
            if (response.isSuccessful && response.body()?.success == true) {
                val appSettings = response.body()?.data
                if (appSettings != null) {
                    // Cache the settings
                    cacheSettings(appSettings)
                    Log.d(TAG, "AdMob settings fetched and cached successfully")
                    emit(Result.success(appSettings))
                } else {
                    Log.w(TAG, "Server returned empty data, using fallback")
                    emit(Result.success(getFallbackSettings()))
                }
            } else {
                Log.w(TAG, "Failed to fetch AdMob settings: ${response.message()}")
                emit(Result.success(getFallbackSettings()))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching AdMob settings: ${e.message}")
            // Try to return cached data as fallback
            val cachedSettings = getCachedSettings()
            if (cachedSettings != null) {
                emit(Result.success(cachedSettings))
            } else {
                emit(Result.success(getFallbackSettings()))
            }
        }
    }.flowOn(Dispatchers.IO)
    
    /**
     * Get current app settings (cached or fallback)
     */
    fun getCurrentSettings(): AppSettings {
        return getCachedSettings() ?: getFallbackSettings()
    }
    
    /**
     * Get specific ad unit IDs
     */
    fun getBanner1Id(): String = getCurrentSettings().banner1Id.ifEmpty { TEST_BANNER_ID }
    fun getBanner2Id(): String = getCurrentSettings().banner2Id.ifEmpty { TEST_BANNER_ID }
    fun getInterstitial1Id(): String = getCurrentSettings().interstitial1Id.ifEmpty { TEST_INTERSTITIAL_ID }
    fun getInterstitial2Id(): String = getCurrentSettings().interstitial2Id.ifEmpty { TEST_INTERSTITIAL_ID }
    fun getNative1Id(): String = getCurrentSettings().native1Id.ifEmpty { TEST_NATIVE_ID }
    fun getNative2Id(): String = getCurrentSettings().native2Id.ifEmpty { TEST_NATIVE_ID }
    fun getNative3Id(): String = getCurrentSettings().native3Id.ifEmpty { TEST_NATIVE_ID }
    fun getRewarded1Id(): String = getCurrentSettings().rewarded1Id.ifEmpty { TEST_REWARDED_ID }
    fun getAppId(): String = getCurrentSettings().appId.ifEmpty { TEST_APP_ID }
    
    /**
     * Force refresh settings from server (ignores cache)
     */
    suspend fun forceRefreshSettings(): Flow<Result<AppSettings>> = flow {
        try {
            Log.d(TAG, "Force refreshing AdMob settings from server")
            val response = RetrofitClient.apiService.getAppSettings()
            
            if (response.isSuccessful && response.body()?.success == true) {
                val appSettings = response.body()?.data
                if (appSettings != null) {
                    // Cache the settings
                    cacheSettings(appSettings)
                    Log.d(TAG, "AdMob settings force refreshed and cached successfully")
                    emit(Result.success(appSettings))
                } else {
                    Log.w(TAG, "Server returned empty data during force refresh")
                    emit(Result.success(getFallbackSettings()))
                }
            } else {
                Log.w(TAG, "Failed to force refresh AdMob settings: ${response.message()}")
                emit(Result.success(getFallbackSettings()))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during force refresh: ${e.message}")
            // Return cached data if available, otherwise fallback
            val cachedSettings = getCachedSettings()
            if (cachedSettings != null) {
                emit(Result.success(cachedSettings))
            } else {
                emit(Result.success(getFallbackSettings()))
            }
        }
    }.flowOn(Dispatchers.IO)
    
    /**
     * Clear cached settings (forces next fetch from server)
     */
    fun clearCache() {
        prefs.edit().clear().apply()
        Log.d(TAG, "AdMob settings cache cleared")
    }
    
    private fun cacheSettings(settings: AppSettings) {
        prefs.edit().apply {
            putString(KEY_APP_ID, settings.appId)
            putString(KEY_BANNER1_ID, settings.banner1Id)
            putString(KEY_BANNER2_ID, settings.banner2Id)
            putString(KEY_INTERSTITIAL1_ID, settings.interstitial1Id)
            putString(KEY_INTERSTITIAL2_ID, settings.interstitial2Id)
            putString(KEY_NATIVE1_ID, settings.native1Id)
            putString(KEY_NATIVE2_ID, settings.native2Id)
            putString(KEY_NATIVE3_ID, settings.native3Id)
            putString(KEY_REWARDED1_ID, settings.rewarded1Id)
            putLong(KEY_LAST_UPDATE, System.currentTimeMillis())
            apply()
        }
    }
    
    private fun getCachedSettings(): AppSettings? {
        val appId = prefs.getString(KEY_APP_ID, "") ?: ""
        if (appId.isEmpty()) return null
        
        return AppSettings(
            admob = Admob(
                appId = appId,
                banner1Id = prefs.getString(KEY_BANNER1_ID, "") ?: "",
                banner2Id = prefs.getString(KEY_BANNER2_ID, "") ?: "",
                interstitial1Id = prefs.getString(KEY_INTERSTITIAL1_ID, "") ?: "",
                interstitial2Id = prefs.getString(KEY_INTERSTITIAL2_ID, "") ?: "",
                native1Id = prefs.getString(KEY_NATIVE1_ID, "") ?: "",
                native2Id = prefs.getString(KEY_NATIVE2_ID, "") ?: "",
                native3Id = prefs.getString(KEY_NATIVE3_ID, "") ?: "",
                rewarded1Id = prefs.getString(KEY_REWARDED1_ID, "") ?: ""
            )
        )
    }
    
    private fun getFallbackSettings(): AppSettings {
        return AppSettings(
            admob = Admob(
                appId = TEST_APP_ID,
                banner1Id = TEST_BANNER_ID,
                banner2Id = TEST_BANNER_ID,
                interstitial1Id = TEST_INTERSTITIAL_ID,
                interstitial2Id = TEST_INTERSTITIAL_ID,
                native1Id = TEST_NATIVE_ID,
                native2Id = TEST_NATIVE_ID,
                native3Id = TEST_NATIVE_ID,
                rewarded1Id = TEST_REWARDED_ID
            )
        )
    }
}