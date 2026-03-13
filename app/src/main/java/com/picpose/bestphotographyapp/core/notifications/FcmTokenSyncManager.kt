/**
 * ---
 * File: FcmTokenSyncManager.kt
 * Layer: Infrastructure (FCM)
 * Project: PicPose
 *
 * Purpose:
 * Handles Firebase Cloud Messaging setup, token sync, or notification behavior.
 *
 * Interactions:
 * Bridges Firebase callbacks into app services, token sync, and deep-link style notification flows.
 *
 * Data Flow:
 * Feature-specific flow; see adjacent ViewModels and repositories for the full path.
 *
 * Maintainer Notes:
 * - Keep SDK-specific code isolated here so feature screens remain testable.
 * - TODO: Add analytics and remote-config driven rollout controls where appropriate.
 * ---
 */

package com.picpose.bestphotographyapp.core.notifications

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.picpose.bestphotographyapp.BuildConfig
import com.picpose.bestphotographyapp.data.remote.api.FcmApiService
import com.picpose.bestphotographyapp.data.remote.api.RegisterFcmTokenRequest
import com.picpose.bestphotographyapp.core.network.RetrofitClient
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

object FcmTokenSyncManager {

    private const val TAG = "FcmTokenSync"
    private const val PREFS = "fcm_sync_prefs"
    private const val KEY_LAST_TOKEN = "last_token"
    private const val KEY_LAST_SYNC_AT = "last_sync_at"
    private const val RESYNC_INTERVAL_MS = 7L * 24L * 60L * 60L * 1000L

    private val api: FcmApiService by lazy {
        RetrofitClient.createService(FcmApiService::class.java)
    }

    suspend fun syncCurrentToken(
        context: Context,
        userId: Int?,
        reason: String,
        force: Boolean = false
    ): Boolean {
        return try {
            val token = FirebaseMessaging.getInstance().token.await()
            syncToken(context, token, userId, reason, force)
        } catch (e: Exception) {
            Log.e(TAG, "token fetch failed reason=$reason", e)
            false
        }
    }

    suspend fun syncToken(
        context: Context,
        token: String,
        userId: Int?,
        reason: String,
        force: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        if (token.isBlank()) {
            Log.w(TAG, "skip sync: blank token reason=$reason")
            return@withContext false
        }

        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastToken = prefs.getString(KEY_LAST_TOKEN, null)
        val lastSyncAt = prefs.getLong(KEY_LAST_SYNC_AT, 0L)
        val now = System.currentTimeMillis()
        val shouldSync = force || (token != lastToken) || (now - lastSyncAt > RESYNC_INTERVAL_MS)

        if (!shouldSync) {
            Log.d(TAG, "skip sync: unchanged and fresh reason=$reason")
            return@withContext true
        }

        val locale = Locale.getDefault()
        val request = RegisterFcmTokenRequest(
            token = token,
            user_id = userId,
            platform = "android",
            app_version = BuildConfig.VERSION_NAME,
            device_model = Build.MODEL ?: "unknown",
            os_version = Build.VERSION.RELEASE ?: Build.VERSION.SDK_INT.toString(),
            language = locale.language.ifBlank { "en" },
            country = locale.country,
            timezone = TimeZone.getDefault().id
        )

        return@withContext try {
            val response = api.registerDevice(request)
            if (response.isSuccessful && response.body()?.success == true) {
                prefs.edit()
                    .putString(KEY_LAST_TOKEN, token)
                    .putLong(KEY_LAST_SYNC_AT, now)
                    .apply()
                Log.i(TAG, "sync success reason=$reason")
                true
            } else {
                Log.e(TAG, "sync failed http=${response.code()} reason=$reason")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "sync exception reason=$reason", e)
            false
        }
    }
}
