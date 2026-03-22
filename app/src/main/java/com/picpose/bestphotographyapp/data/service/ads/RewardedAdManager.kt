/**
 * ---
 * File: RewardedAdManager.kt
 * Layer: Data
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

package com.picpose.bestphotographyapp.data.service.ads

import android.app.Activity
import android.content.Context
import com.picpose.bestphotographyapp.components.ads.AdsManager
import com.picpose.bestphotographyapp.components.ads.RewardedAdController
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

data class RewardedAdUiState(
    val isLoading: Boolean = false,
    val isReady: Boolean = false,
    val isShowing: Boolean = false,
    val lastError: String? = null,
)

@Singleton
class RewardedAdManager @Inject constructor() {
    private var controller: RewardedAdController? = null
    private var placementKey: String = AdsManager.KEY_REWARDED_AD

    private val _uiState = MutableStateFlow(RewardedAdUiState())
    val uiState: StateFlow<RewardedAdUiState> = _uiState.asStateFlow()

    fun loadRewardedAd(
        context: Context,
        placementKey: String = AdsManager.KEY_REWARDED_AD,
    ) {
        this.placementKey = placementKey
        val rewardedController = controller ?: RewardedAdController(placementKey).also { controller = it }

        _uiState.value = _uiState.value.copy(isLoading = true, isShowing = false, lastError = null)
        rewardedController.preload(
            context = context.applicationContext,
            callbacks = object : RewardedAdController.Callbacks {
                override fun onLoaded() {
                    _uiState.value = RewardedAdUiState(isLoading = false, isReady = true, isShowing = false, lastError = null)
                }

                override fun onFailed(error: com.google.android.gms.ads.LoadAdError) {
                    _uiState.value = RewardedAdUiState(
                        isLoading = false,
                        isReady = false,
                        isShowing = false,
                        lastError = error.message,
                    )
                }
            }
        )
    }

    fun showRewardedAd(
        activity: Activity,
        placementKey: String = this.placementKey,
        onRewardEarned: (String) -> Unit,
        onUnavailable: (String) -> Unit,
        onDismissed: () -> Unit = {},
    ) {
        this.placementKey = placementKey
        val rewardedController = controller ?: RewardedAdController(placementKey).also {
            controller = it
        }

        if (_uiState.value.isShowing) {
            onUnavailable("A rewarded ad is already in progress.")
            return
        }

        if (!_uiState.value.isReady) {
            loadRewardedAd(activity.applicationContext, placementKey)
            onUnavailable("Preparing your reward…")
            return
        }

        val adRewardId = generateRewardId()
        var rewardDispatched = false
        _uiState.value = _uiState.value.copy(isShowing = true, lastError = null)

        rewardedController.show(
            activity = activity,
            callbacks = object : RewardedAdController.Callbacks {
                override fun onReward(reward: com.google.android.gms.ads.rewarded.RewardItem) {
                    if (!rewardDispatched) {
                        rewardDispatched = true
                        onRewardEarned(adRewardId)
                    }
                }

                override fun onDismissed() {
                    _uiState.value = RewardedAdUiState(isLoading = false, isReady = false, isShowing = false, lastError = null)
                    loadRewardedAd(activity.applicationContext, placementKey)
                    onDismissed()
                }

                override fun onFailedToShow(error: com.google.android.gms.ads.AdError) {
                    _uiState.value = RewardedAdUiState(
                        isLoading = false,
                        isReady = false,
                        isShowing = false,
                        lastError = error.message,
                    )
                    loadRewardedAd(activity.applicationContext, placementKey)
                    onUnavailable(error.message ?: "Rewarded ad failed to show.")
                }
            },
            onComplete = {
                if (!_uiState.value.isReady) {
                    loadRewardedAd(activity.applicationContext, placementKey)
                }
            }
        )
    }

    private fun generateRewardId(): String {
        return "ad_${System.currentTimeMillis()}_${Random.nextInt(1000, 9999)}"
    }
}
