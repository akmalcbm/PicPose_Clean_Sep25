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
import com.picpose.bestphotographyapp.data.models.AdConfig
import com.picpose.bestphotographyapp.data.network.RetrofitClient
import com.picpose.bestphotographyapp.presentation.ads.AdsManager
import com.picpose.bestphotographyapp.utils.Constants
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

@HiltAndroidApp
class PicPoseApplication : Application(), ImageLoaderFactory {

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
        fetchAdMobConfig()

        // 🔹 Firebase topic subscription
        subscribeToFirebaseTopics()
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
                    MobileAds.initialize(this@PicPoseApplication) { status ->
                        Log.d(
                            "PicPoseApp",
                            "✅ AdMob initialized: ${status.adapterStatusMap.keys}"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("PicPoseApp", "❌ AdMob init failed", e)
            }
        }
    }

    /**
     * 🔵 Fetch AdMob config safely
     */
    private fun fetchAdMobConfig() {
        applicationScope.launch(Dispatchers.IO) {
            try {
                // 🔹 Currently using TEST ads
                val adConfig = AdConfig(
                    appId = Constants.TEST_APP_ID,
                    bannerId = Constants.TEST_BANNER_ID,
                    bannerId2 = Constants.TEST_BANNER_ID_2,
                    interstitialId = Constants.TEST_INTERSTITIAL_ID,
                    interstitialId2 = Constants.TEST_INTERSTITIAL_ID_2,
                    nativeId = Constants.TEST_NATIVE_ID,
                    nativeId2 = Constants.TEST_NATIVE_ID_2,
                    nativeId3 = Constants.TEST_NATIVE_ID_2,
                    rewardedId = Constants.TEST_REWARDED_ID,
                )

                // 🔹 Initialize central AdsManager
                AdsManager.init(adConfig)
                Log.d("PicPoseApp", "✅ AdsManager initialized with TEST Ad IDs")

            } catch (e: Exception) {
                Log.w("PicPoseApp", "⚠️ Ad config init failed", e)
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
