/**
 * ---
 * File: PicPoseApp.kt
 * Layer: Application / Infrastructure
 * Project: PicPose
 *
 * Purpose:
 * Custom `Application` implementation for PicPose. It initializes app-wide
 * services such as Hilt, crash reporting, token tracking, networking cache,
 * ads warm-up, notification setup, and the shared Coil image loader.
 *
 * Interactions:
 * - Starts `TokenProvider` so network interceptors can read current auth state.
 * - Coordinates startup notification work with `NotificationSettingsCoordinator`
 *   and `FcmTokenSyncManager`.
 * - Warms ads and image loading infrastructure before feature screens request them.
 *
 * Data Flow:
 * App launch -> PicPoseApp -> shared SDK/service initialization -> feature layers consume dependencies
 *
 * Maintainer Notes:
 * - Keep screen-specific behavior out of this class.
 * - TODO: Move startup-heavy SDK initialization behind remote config if cold start grows.
 * ---
 */

package com.picpose.bestphotographyapp

import android.app.Application
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.facebook.FacebookSdk
import com.facebook.appevents.AppEventsLogger
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.firebase.messaging.FirebaseMessaging
import com.picpose.bestphotographyapp.core.crash.CrashReporter
import com.picpose.bestphotographyapp.data.datastore.SettingsManager
import com.picpose.bestphotographyapp.data.datastore.UserSessionManager
import com.picpose.bestphotographyapp.data.network.RetrofitClient
import com.picpose.bestphotographyapp.data.network.TokenProvider
import com.picpose.bestphotographyapp.fcm.FcmTokenSyncManager
import com.picpose.bestphotographyapp.fcm.NotificationSettingsCoordinator
import com.picpose.bestphotographyapp.presentation.ads.AdsInitializer
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.tasks.await

@HiltAndroidApp
class PicPoseApp : Application(), ImageLoaderFactory {

    @Inject
    lateinit var adsInitializer: AdsInitializer

    @Inject
    lateinit var crashReporter: CrashReporter

    @Inject
    lateinit var tokenProvider: TokenProvider

    /**
     * Long-lived scope for startup work that should not be tied to a screen.
     */
    private val applicationScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default
    )

    override fun onCreate() {
        super.onCreate()

        crashReporter.configureCollection(enabled = !BuildConfig.DEBUG)
        crashReporter.setAccountType("unknown")
        tokenProvider.start()

        // Prepare shared network cache before repositories start issuing requests.
        RetrofitClient.initCache(this)

        // 🔹 Facebook SDK
        initFacebookSdk()

        // 🔹 AdMob
        initAdMobSafely()
        adsInitializer.warmUpOnAppStart(
            context = this,
            appScope = applicationScope,
            forceRefresh = false
        )

        setupNotificationsOnAppStart()

    }

    /**
     * 🔵 Facebook SDK initialization
     */
    private fun initFacebookSdk() {
        FacebookSdk.setApplicationId(getString(R.string.facebook_app_id))
        FacebookSdk.setClientToken(getString(R.string.facebook_client_token))
        FacebookSdk.setAutoInitEnabled(true)
        FacebookSdk.fullyInitialize()
        AppEventsLogger.activateApp(this)
    }

    private fun setupNotificationsOnAppStart() {
        applicationScope.launch {
            val settingsManager = SettingsManager(this@PicPoseApp)
            val notificationsEnabled = settingsManager.notificationsEnabled.first()

            if (!notificationsEnabled) {
                Log.i("FCM", "Notifications disabled by app preference; skipping topic/token setup")
                return@launch
            }

            // Channels must exist before the app can safely display notifications.
            NotificationSettingsCoordinator.ensureNotificationChannels(this@PicPoseApp)
            subscribeToTopics()
            logCurrentFcmTokenForTesting()
            syncFcmTokenOnAppStart()
        }
    }

    private suspend fun subscribeToTopics() {
        try {
            listOf("all", "android", "general", "guides", "prompts").forEach { topic ->
                FirebaseMessaging.getInstance().subscribeToTopic(topic).await()
            }
            Log.d("FCM", "✅ Subscribed to configured topics")
        } catch (e: Exception) {
            Log.e("FCM", "❌ Topic subscription failed", e)
        }
    }

    private fun logCurrentFcmTokenForTesting() {
        if (!BuildConfig.DEBUG) return
        applicationScope.launch {
            try {
                val token = FirebaseMessaging.getInstance().token.await()
                val tokenPreview = token.take(8) + "..."
                Log.d("FCM_TOKEN", "Device FCM token fetched: $tokenPreview")
            } catch (e: Exception) {
                Log.e("FCM_TOKEN", "Failed to fetch device token", e)
            }
        }
    }

    private fun syncFcmTokenOnAppStart() {
        applicationScope.launch {
            val userSessionManager = UserSessionManager(this@PicPoseApp)
            val userIdRaw = userSessionManager.userId.firstOrNull()
            val userId = userIdRaw?.toIntOrNull()
            crashReporter.setUserIdentifier(userIdRaw)
            FcmTokenSyncManager.syncCurrentToken(
                context = this@PicPoseApp,
                userId = userId,
                reason = "app_start",
                force = false
            )
        }
    }

    /**
     * 🔵 Safe AdMob initialization
     */
    private fun initAdMobSafely() {
        applicationScope.launch {
            try {
                val testDeviceIds = listOf("33BE2250B43518CCDA7DE426D04EE231")
                val config = RequestConfiguration.Builder()
                    .setTestDeviceIds(testDeviceIds)
                    .build()

                MobileAds.setRequestConfiguration(config)

                withContext(Dispatchers.Main) {
                    adsInitializer.initSdk(this@PicPoseApp)
                    Log.d("PicPoseApp", "✅ Ads SDK initialization triggered")
                }
            } catch (e: Exception) {
                Log.e("PicPoseApp", "❌ AdMob init failed", e)
            }
        }
    }


    /**
     * 🖼️ Coil ImageLoader (singleton)
     */
    private val imageLoader by lazy {
        ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(50L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .respectCacheHeaders(false)
            .build()
    }

    override fun newImageLoader(): ImageLoader = imageLoader
}
