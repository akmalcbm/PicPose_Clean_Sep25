package com.picpose.bestphotographyapp

import android.app.Application
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.picpose.bestphotographyapp.data.admob.AdMobConfigManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

@HiltAndroidApp
class PicPoseApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()

        // ✅ Initialize Retrofit cache safely
        com.picpose.bestphotographyapp.data.network.RetrofitClient.initCache(this)

        // ✅ Initialize Google Ads asynchronously to avoid ANR
        initializeAdMobSafely()

        // ✅ Initialize AdMob config manager (background fetch)
        initializeAdMobConfig()
    }

    /**
     * Initialize Google Mobile Ads on background thread
     */
    private fun initializeAdMobSafely() {
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val testDeviceIds = listOf("33BE2250B43518CCDA7DE426D04EE231")
                val config = RequestConfiguration.Builder()
                    .setTestDeviceIds(testDeviceIds)
                    .build()
                MobileAds.setRequestConfiguration(config)

                // Delay slightly to ensure Google Play Services is ready
                delay(300)

                withContext(Dispatchers.Main) {
                    MobileAds.initialize(this@PicPoseApplication) { status ->
                        Log.d("PicPoseApp", "✅ AdMob initialized: ${status.adapterStatusMap.keys}")
                    }
                }
            } catch (e: Exception) {
                Log.e("PicPoseApp", "❌ Failed to initialize AdMob: ${e.message}")
            }
        }
    }

    /**
     * Fetch AdMob settings in background (safe)
     */
    @OptIn(DelicateCoroutinesApi::class)
    private fun initializeAdMobConfig() {
        GlobalScope.launch(Dispatchers.IO + SupervisorJob()) {
            try {
                val adMobConfig = AdMobConfigManager.getInstance(this@PicPoseApplication)
                adMobConfig.fetchAppSettings().first()
            } catch (e: Exception) {
                Log.w("PicPoseApp", "⚠️ AdMob config fetch failed: ${e.message}")
            }
        }
    }

    // ✅ Lazy singleton ImageLoader instance (only created once)
    private val imageLoader by lazy {
        ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25) // Use up to 25% of app memory for images
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(50L * 1024 * 1024) // 50 MB disk cache
                    .build()
            }
            .crossfade(true) // Optional smooth fade-in
            .respectCacheHeaders(false) // Ensures old images still load if offline
            .build()
    }

    override fun newImageLoader(): ImageLoader = imageLoader
}
