/**
 * ---
 * File: NativeAdUiState.kt
 * Layer: Presentation (UI)
 * Project: PicPose
 *
 * Purpose:
 * Supports the PicPose app with feature-specific models, helpers, or UI building blocks.
 *
 * Interactions:
 * Works with nearby classes in the same layer to keep responsibilities separated and easier to maintain.
 *
 * Data Flow:
 * Feature-specific flow; see adjacent ViewModels and repositories for the full path.
 *
 * Maintainer Notes:
 * - Keep SDK-specific code isolated here so feature screens remain testable.
 * - TODO: Add analytics and remote-config driven rollout controls where appropriate.
 * ---
 */

package com.picpose.bestphotographyapp.components.ads

import com.google.android.gms.ads.nativead.NativeAd

sealed interface NativeAdUiState {
    data object Loading : NativeAdUiState
    data class Loaded(val ad: NativeAd) : NativeAdUiState
    data object Failed : NativeAdUiState
    data object Disabled : NativeAdUiState
}

