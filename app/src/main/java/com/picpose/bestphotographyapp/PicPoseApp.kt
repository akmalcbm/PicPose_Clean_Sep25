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
import com.picpose.bestphotographyapp.data.datastore.UserSessionManager
import com.picpose.bestphotographyapp.data.network.RetrofitClient
import com.picpose.bestphotographyapp.fcm.FcmTokenSyncManager
import com.picpose.bestphotographyapp.presentation.ads.AdsInitializer
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.tasks.await

@HiltAndroidApp
class PicPoseApp : Application(), ImageLoaderFactory {

    @Inject
    lateinit var adsInitializer: AdsInitializer

    /**
     * ✅ Application-wide safe coroutine scope
     */
    private val applicationScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default
    )

    override fun onCreate() {
        super.onCreate()

        // 🔹 Retrofit cache
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

        // 🔹 Firebase topic subscription
        subscribeToFirebaseTopics()

        // 🔹 Log current device token for admin-panel test sends
        logCurrentFcmTokenForTesting()

        // 🔹 Token sync to backend (on app start / periodic refresh window)
        syncFcmTokenOnAppStart()
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

    /**
     * 🔵 Firebase topic subscriptions
     */
    private fun subscribeToFirebaseTopics() {
        applicationScope.launch {
            try {
                FirebaseMessaging.getInstance().subscribeToTopic("all").await()
                FirebaseMessaging.getInstance().subscribeToTopic("android").await()

                Log.d("FCM", "✅ Subscribed to default topics")

            } catch (e: Exception) {
                Log.e("FCM", "❌ Topic subscription failed", e)
            }
        }
    }

    private fun logCurrentFcmTokenForTesting() {
        applicationScope.launch {
            try {
                val token = FirebaseMessaging.getInstance().token.await()
                Log.d("FCM_TOKEN", "Device FCM token: $token")
            } catch (e: Exception) {
                Log.e("FCM_TOKEN", "Failed to fetch device token", e)
            }
        }
    }

    private fun syncFcmTokenOnAppStart() {
        applicationScope.launch {
            val userId = UserSessionManager(this@PicPoseApp).userId.firstOrNull()?.toIntOrNull()
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
