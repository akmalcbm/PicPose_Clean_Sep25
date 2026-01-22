package com.picpose.bestphotographyapp.data

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.picpose.bestphotographyapp.presentation.ads.AdsManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

/**
 * AdManager - Singleton for managing interstitial ads with frequency control
 * 
 * Features:
 * - Alternates between interstitial1 and interstitial2
 * - Tracks click count and shows ads every N clicks (configurable)
 * - Preloads ads for better UX
 * - Provides suspendable functions for Compose integration
 * - Uses test ad IDs as fallback when server IDs are missing
 */
class AdManager private constructor() {
    
    companion object {
        private const val TAG = "AdManager"
        
        @Volatile
        private var INSTANCE: AdManager? = null
        
        fun getInstance(): AdManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AdManager().also { INSTANCE = it }
            }
        }
    }
    
    // Ad unit IDs from server (set via initialize)
    private var interstitial1Id: String = AdsManager.interstitialId()
    private var interstitial2Id: String = AdsManager.interstitialId2()
    
    // Click counter and frequency control
    private val clickCounter = AtomicInteger(0)
    private var showAdEveryNClicks: Int = 3 // Default: show ad every 3 clicks
    
    // Preloaded ads
    private var interstitialAd1: InterstitialAd? = null
    private var interstitialAd2: InterstitialAd? = null
    
    // Track which ad to show next (alternating)
    private var useAd1Next = true
    
    /**
     * Initialize AdManager with AppSettings
     * Should be called once from Application or main activity
     */
    fun initialize(clickFrequency: Int = 3) {
        Log.d(TAG, "Initializing AdManager with server settings")
        
        // Use server IDs if available, fallback to test IDs
        interstitial1Id = AdsManager.interstitialId() //appSettings.interstitial1Id.ifEmpty { TEST_INTERSTITIAL_ID }
        interstitial2Id = AdsManager.interstitialId2() //appSettings.interstitial2Id.ifEmpty { TEST_INTERSTITIAL_ID }
        
        showAdEveryNClicks = clickFrequency.coerceAtLeast(1) // Minimum 1
        
        Log.d(TAG, "AdManager initialized - interstitial1: $interstitial1Id, interstitial2: $interstitial2Id, frequency: $showAdEveryNClicks")
    }
    
    /**
     * Preload interstitial ads in the background
     */
    fun preloadAds(context: Context?) {
        if (context == null) {
            Log.w(TAG, "Cannot preload ads - context is null")
            return
        }
        Log.d(TAG, "Preloading interstitial ads")
        loadInterstitialAd1(context)
        loadInterstitialAd2(context)
    }
    
    /**
     * Check if an interstitial ad should be shown based on click count
     */
    fun shouldShowInterstitial(): Boolean {
        val count = clickCounter.get()
        val shouldShow = count > 0 && count % showAdEveryNClicks == 0
        Log.d(TAG, "shouldShowInterstitial: count=$count, shouldShow=$shouldShow")
        return shouldShow
    }
    
    /**
     * Increment click counter (call when user clicks similar prompt)
     */
    fun incrementClickCount() {
        val newCount = clickCounter.incrementAndGet()
        Log.d(TAG, "Click count incremented to: $newCount")
    }
    
    /**
     * Reset click counter (useful for testing or manual reset)
     */
    fun resetClickCount() {
        clickCounter.set(0)
        Log.d(TAG, "Click count reset to 0")
    }
    
    /**
     * Show interstitial ad with suspend support for Compose
     * Returns true if ad was shown, false otherwise
     */
    suspend fun showInterstitialAndWait(activity: Activity): Boolean = withContext(Dispatchers.Main) {
        val deferred = CompletableDeferred<Boolean>()
        
        try {
            // Get the ad to show (alternating between ad1 and ad2)
            val adToShow = if (useAd1Next) {
                interstitialAd1.also { useAd1Next = false }
            } else {
                interstitialAd2.also { useAd1Next = true }
            }
            
            if (adToShow == null) {
                Log.w(TAG, "No interstitial ad available to show")
                deferred.complete(false)
                return@withContext false
            }
            
            // Set up callback
            adToShow.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d(TAG, "Interstitial ad dismissed")
                    // Preload next ad
                    if (useAd1Next) {
                        loadInterstitialAd2(activity)
                    } else {
                        loadInterstitialAd1(activity)
                    }
                    deferred.complete(true)
                }
                
                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.e(TAG, "Interstitial ad failed to show: ${adError.message}")
                    deferred.complete(false)
                }
                
                override fun onAdShowedFullScreenContent() {
                    Log.d(TAG, "Interstitial ad showed")
                    // Clear the reference so it can't be shown again
                    if (useAd1Next) {
                        interstitialAd2 = null
                    } else {
                        interstitialAd1 = null
                    }
                }
            }
            
            // Show the ad
            adToShow.show(activity)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error showing interstitial ad: ${e.message}")
            deferred.complete(false)
        }
        
        // Wait for ad to be dismissed or failed
        deferred.await()
    }
    
    /**
     * Load interstitial ad 1
     */
    private fun loadInterstitialAd1(context: Context) {
        if (interstitialAd1 != null) {
            Log.d(TAG, "Interstitial ad 1 already loaded")
            return
        }
        
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            context,
            interstitial1Id,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.d(TAG, "Interstitial ad 1 loaded successfully")
                    interstitialAd1 = ad
                }
                
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.w(TAG, "Interstitial ad 1 failed to load: ${loadAdError.message}")
                    interstitialAd1 = null
                }
            }
        )
    }
    
    /**
     * Load interstitial ad 2
     */
    private fun loadInterstitialAd2(context: Context) {
        if (interstitialAd2 != null) {
            Log.d(TAG, "Interstitial ad 2 already loaded")
            return
        }
        
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            context,
            interstitial2Id,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.d(TAG, "Interstitial ad 2 loaded successfully")
                    interstitialAd2 = ad
                }
                
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.w(TAG, "Interstitial ad 2 failed to load: ${loadAdError.message}")
                    interstitialAd2 = null
                }
            }
        )
    }
    
    /**
     * Clean up resources (call from onDestroy if needed)
     */
    fun cleanup() {
        Log.d(TAG, "Cleaning up AdManager")
        interstitialAd1 = null
        interstitialAd2 = null
    }
}
