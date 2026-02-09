package com.picpose.bestphotographyapp.data.admob

import android.content.Context
import com.picpose.bestphotographyapp.core.constants.Constants
import com.picpose.bestphotographyapp.data.models.AppSettings
import com.picpose.bestphotographyapp.presentation.ads.AdsLog
import com.picpose.bestphotographyapp.presentation.ads.AdsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Legacy bridge kept for backward compatibility. New logic lives in AdsManager.
 */
class AdMobConfigManager private constructor(context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: AdMobConfigManager? = null

        fun getInstance(context: Context): AdMobConfigManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AdMobConfigManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    suspend fun fetchAppSettings(): Flow<Result<AppSettings>> = flow {
        AdsLog.d(AdsLog.TAG_UI, "[AdsUI] AdMobConfigManager.fetchAppSettings legacy_bridge=true")
        emit(Result.success(AppSettings()))
    }.flowOn(Dispatchers.IO)

    fun getCurrentSettings(): AppSettings = AppSettings()

    fun bannerId() = AdsManager.bannerIdOrNull() ?: Constants.TEST_BANNER_ID
    fun bannerId2() = AdsManager.bannerId2OrNull() ?: Constants.TEST_BANNER_ID_2
    fun interstitialId() = AdsManager.interstitialIdOrNull() ?: Constants.TEST_INTERSTITIAL_ID
    fun interstitialId2() = AdsManager.interstitialId2OrNull() ?: Constants.TEST_INTERSTITIAL_ID_2
    fun nativeId() = AdsManager.nativeIdOrNull() ?: Constants.TEST_NATIVE_ID
    fun nativeId2() = AdsManager.nativeId2OrNull() ?: Constants.TEST_NATIVE_ID_2
    fun nativeId3() = AdsManager.nativeId3OrNull() ?: Constants.TEST_NATIVE_ID_3
    fun rewardedId() = AdsManager.rewardedIdOrNull() ?: Constants.TEST_REWARDED_ID

    suspend fun forceRefreshSettings(): Flow<Result<AppSettings>> = flow {
        AdsLog.d(AdsLog.TAG_UI, "[AdsUI] AdMobConfigManager.forceRefreshSettings legacy_bridge=true")
        emit(Result.success(AppSettings()))
    }.flowOn(Dispatchers.IO)

    fun clearCache() = Unit
}
