/**
 * ---
 * File: AdConfig.kt
 * Layer: Data
 * Project: PicPose
 *
 * Purpose:
 * Defines DTOs and domain-facing models that represent prompt, user, stats, or settings data.
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

package com.picpose.bestphotographyapp.data.remote.dto

data class AdConfig(
    val appId: String,
    val bannerId: String,
    val bannerId2: String,
    val interstitialId: String,
    val interstitialId2: String,
    val nativeId: String,
    val nativeId2: String,
    val nativeId3: String,
    val rewardedId: String
)