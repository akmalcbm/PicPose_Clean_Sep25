/**
 * ---
 * File: Constants.kt
 * Layer: Core
 * Project: PicPose
 *
 * Purpose:
 * Provides app-wide helpers, constants, analytics, locale, formatting, or cross-cutting abstractions.
 *
 * Interactions:
 * Works with nearby classes in the same layer to keep responsibilities separated and easier to maintain.
 *
 * Data Flow:
 * Feature-specific flow; see adjacent ViewModels and repositories for the full path.
 *
 * Maintainer Notes:
 * - Keep responsibilities narrow and update this header when the file grows into a larger abstraction.
 * ---
 */

package com.picpose.bestphotographyapp.core.constants

import com.picpose.bestphotographyapp.BuildConfig

object Constants {

    // ===============================
    // ENVIRONMENT
    // ===============================

    const val IS_TEST_ADS = true
    // true  -> Test Ads
    // false -> Live Ads (Server side)

    // ===============================
    // ADMOB TEST IDS
    // ===============================

    const val TEST_APP_ID =
        "ca-app-pub-3940256099942544~3347511713"

    const val TEST_BANNER_ID =
        "ca-app-pub-3940256099942544/6300978111"

    const val TEST_BANNER_ID_2 =
        "ca-app-pub-3940256099942544/6300978111"

    const val TEST_INTERSTITIAL_ID =
        "ca-app-pub-3940256099942544/1033173712"

    const val TEST_INTERSTITIAL_ID_2 =
        "ca-app-pub-3940256099942544/1033173712"

    const val TEST_NATIVE_ID =
        "ca-app-pub-3940256099942544/2247696110"

    const val TEST_NATIVE_ID_2 =
        "ca-app-pub-3940256099942544/2247696110"

    const val TEST_NATIVE_ID_3   =
        "ca-app-pub-3940256099942544/2247696110"

    const val TEST_REWARDED_ID =
        "ca-app-pub-3940256099942544/5224354917"

    // Google official fallback test IDs (used as debug fallback if custom IDs are empty)
    private const val GOOGLE_TEST_BANNER_ID = "ca-app-pub-3940256099942544/6300978111"
    private const val GOOGLE_TEST_INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712"
    private const val GOOGLE_TEST_NATIVE_ID = "ca-app-pub-3940256099942544/2247696110"
    private const val GOOGLE_TEST_REWARDED_ID = "ca-app-pub-3940256099942544/5224354917"

    fun fallbackAdUnitIdFor(key: String): String? {
        val normalizedKey = key.trim().lowercase()
        val custom = when (normalizedKey) {
            "home_banner", "banner_1" -> TEST_BANNER_ID
            "banner_2" -> TEST_BANNER_ID_2
            "home_interstitial", "detail_interstitial", "interstitial_1" -> TEST_INTERSTITIAL_ID
            "interstitial_2" -> TEST_INTERSTITIAL_ID_2
            "native_ad", "native_1" -> TEST_NATIVE_ID
            "native_2" -> TEST_NATIVE_ID_2
            "native_3" -> TEST_NATIVE_ID_3
            "rewarded_ad", "rewarded_1" -> TEST_REWARDED_ID
            else -> ""
        }

        val google = when (normalizedKey) {
            "home_banner", "banner_1", "banner_2" -> GOOGLE_TEST_BANNER_ID
            "home_interstitial", "detail_interstitial", "interstitial_1", "interstitial_2" -> GOOGLE_TEST_INTERSTITIAL_ID
            "native_ad", "native_1", "native_2", "native_3" -> GOOGLE_TEST_NATIVE_ID
            "rewarded_ad", "rewarded_1" -> GOOGLE_TEST_REWARDED_ID
            else -> ""
        }

        return if (BuildConfig.DEBUG) {
            custom.ifBlank { google }.ifBlank { null }
        } else {
            custom.ifBlank { null }
        }
    }
}
