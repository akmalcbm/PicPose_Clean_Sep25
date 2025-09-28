package com.picpose.bestphotographyapp

import android.app.Application
import androidx.lifecycle.lifecycleScope
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.picpose.bestphotographyapp.data.admob.AdMobConfigManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltAndroidApp
class PicPoseApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()

        // ✅ Initialize Google AdMob
        MobileAds.initialize(this) { initializationStatus ->
            // AdMob initialization complete
        }

        // ✅ Enable test ads during development
        val testDeviceIds = listOf("33BE2250B43518CCDA7DE426D04EE231") // Use your actual test device ID
        val configuration = RequestConfiguration.Builder()
            .setTestDeviceIds(testDeviceIds)
            .build()
        MobileAds.setRequestConfiguration(configuration)
        
        // ✅ Initialize AdMob config manager and fetch settings
        initializeAdMobConfig()
    }
    
    private fun initializeAdMobConfig() {
        try {
            val adMobConfig = AdMobConfigManager.getInstance(this)
            // Fetch settings in background - this will cache them for immediate use
            kotlinx.coroutines.GlobalScope.launch {
                try {
                    adMobConfig.fetchAppSettings().first()
                } catch (e: Exception) {
                    // Settings will fallback to test IDs if server fetch fails
                    android.util.Log.w("PicPoseApp", "AdMob config fetch failed, using fallback: ${e.message}")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("PicPoseApp", "Failed to initialize AdMob config: ${e.message}")
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25) // Use 25% of device memory for image cache
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(this.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(50 * 1024 * 1024) // 50MB disk cache
                    .build()
            }
            .build()
    }
}