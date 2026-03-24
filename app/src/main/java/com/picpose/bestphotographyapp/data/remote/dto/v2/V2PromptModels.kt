/**
 * ---
 * File: V2PromptModels.kt
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

package com.picpose.bestphotographyapp.data.remote.dto.v2

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
data class V2PromptDto(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String = "",
    @SerializedName("shortPrompt") val shortPrompt: String? = null,
    @SerializedName("fullPrompt") val fullPrompt: String? = null,
    @SerializedName("imageUrl") val imageUrl: String? = null,
    @SerializedName("imageUrl2") val imageUrl2: String? = null,
    @SerializedName("category") val category: String? = null,
    @SerializedName("tags") val tags: List<String> = emptyList(),
    @SerializedName("likes") val likes: Int = 0,
    @SerializedName("favorites") val favorites: Int = 0,
    @SerializedName("copies") val copies: Int = 0,
    @SerializedName("views") val views: Int = 0,
    @SerializedName("isPopular") val isPopular: Boolean = false,
    @SerializedName("isFeatured") val isFeatured: Boolean = false,
    @SerializedName("status") val status: String? = null,
    @SerializedName("priority") val priority: Int = 0,
    @SerializedName("createdAt") val createdAt: String? = null,
    @SerializedName("updatedAt") val updatedAt: String? = null,
    @SerializedName("tier") val tier: String = "FREE",
    @SerializedName("premiumUnlockCostPoints") val premiumUnlockCostPoints: Int = 0,
    @SerializedName("premiumPack") val premiumPack: String? = null,
    @SerializedName("isPremium") val backendIsPremium: Boolean? = null,
    @SerializedName("premiumSourceType") val premiumSourceType: String? = null,
    @SerializedName("accessType") val accessType: String? = null,
    @SerializedName("isVisibleInGeneralFeed") val isVisibleInGeneralFeed: Boolean? = null,
    @SerializedName("isPackOnly") val isPackOnly: Boolean? = null,
    @SerializedName("isCreditUnlockable") val isCreditUnlockable: Boolean? = null,
    @SerializedName("isRewardedUnlockable") val isRewardedUnlockable: Boolean? = null,
    @SerializedName("isTokenUnlockable") val isTokenUnlockable: Boolean? = null,
    @SerializedName("isSubscriberUnlockable") val isSubscriberUnlockable: Boolean? = null,
    @SerializedName("availableUnlockMethods") val availableUnlockMethods: List<String> = emptyList(),
    @SerializedName("premiumPackIds") val premiumPackIds: List<Int> = emptyList(),
    @SerializedName("primaryPackId") val primaryPackId: Int? = null,
    @SerializedName("primaryPackName") val primaryPackName: String? = null,
    @SerializedName("primaryPackDescription") val primaryPackDescription: String? = null,
    @SerializedName("primaryPackThumbnailUrl") val primaryPackThumbnailUrl: String? = null,
    @SerializedName("primaryPackPricePoints") val primaryPackPricePoints: Int? = null,
    @SerializedName("primaryPackOwned") val primaryPackOwned: Boolean = false,
    @SerializedName("isLocked") val isLocked: Boolean = false,
    @SerializedName("alreadyUnlocked") val alreadyUnlocked: Boolean? = null,
    @SerializedName("teaserText") val teaserText: String? = null,
) {
    val isPremium: Boolean
        get() = backendIsPremium ?: (
            tier.equals("PREMIUM", ignoreCase = true) ||
            premiumUnlockCostPoints > 0 ||
            !premiumPack.isNullOrBlank()
            )
}

@Keep
data class V2PromptListResponseDto(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: List<V2PromptDto> = emptyList(),
    @SerializedName("total") val total: Int = 0,
    @SerializedName("limit") val limit: Int? = null,
    @SerializedName("offset") val offset: Int? = null,
    @SerializedName("hasMore") val hasMore: Boolean = false,
)

@Keep
data class V2PromptDetailResponseDto(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: V2PromptDto? = null,
)

@Keep
data class PromptOfDayResponseDto(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("day_date") val dayDate: String? = null,
    @SerializedName("potd_mode") val potdMode: String? = null,
    @SerializedName("potd_unlock_cost_points") val potdUnlockCostPoints: Int = 0,
    @SerializedName("post") val post: V2PromptDto? = null,
)

@Keep
data class UnlockPromptByAdRequest(
    @SerializedName("post_id") val postId: String,
    @SerializedName("ad_reward_id") val adRewardId: String,
)

@Keep
data class UnlockPromptByPointsRequest(
    @SerializedName("post_id") val postId: String,
)

@Keep
data class UnlockPromptByTokenRequest(
    @SerializedName("post_id") val postId: String,
)

@Keep
data class UnlockResponseDto(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String? = null,
    @SerializedName("unlocked") val unlocked: Boolean = false,
    @SerializedName("duplicate") val duplicate: Boolean = false,
    @SerializedName("points_balance") val pointsBalance: Int? = null,
    @SerializedName("token_balance") val tokenBalance: Int? = null,
    @SerializedName("cost") val cost: Int? = null,
    @SerializedName("level_reward_points") val levelRewardPoints: Int? = null,
)
