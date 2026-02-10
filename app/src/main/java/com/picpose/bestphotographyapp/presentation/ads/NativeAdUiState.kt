package com.picpose.bestphotographyapp.presentation.ads

import com.google.android.gms.ads.nativead.NativeAd

sealed interface NativeAdUiState {
    data object Loading : NativeAdUiState
    data class Loaded(val ad: NativeAd) : NativeAdUiState
    data object Failed : NativeAdUiState
    data object Disabled : NativeAdUiState
}

