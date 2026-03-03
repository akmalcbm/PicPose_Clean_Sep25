package com.picpose.bestphotographyapp.data.models.v2

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
data class BasicV2Response(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String? = null,
    @SerializedName("claimed") val claimed: Boolean? = null,
    @SerializedName("already_claimed") val alreadyClaimed: Boolean? = null,
    @SerializedName("already_applied") val alreadyApplied: Boolean? = null,
    @SerializedName("unlocked") val unlocked: Boolean? = null,
    @SerializedName("points_added") val pointsAdded: Int? = null,
    @SerializedName("points_balance") val pointsBalance: Int? = null,
    @SerializedName("milestone_hit") val milestoneHit: Boolean? = null,
    @SerializedName("cost") val cost: Int? = null,
)

@Keep
data class StreakStatusDto(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String? = null,
    @SerializedName("streak_count") val streakCount: Int = 0,
    @SerializedName("last_claim_date") val lastClaimDate: String? = null,
    @SerializedName("today_claimed") val todayClaimed: Boolean = false,
    @SerializedName("today_reward_points") val todayRewardPoints: Int = 0,
    @SerializedName("next_day_reward_points") val nextDayRewardPoints: Int = 0,
    @SerializedName("rewards_schedule") val rewardsSchedule: List<Int> = emptyList(),
    @SerializedName("points_balance") val pointsBalance: Int = 0,
    @SerializedName("token_balances") val tokenBalances: Map<String, Int> = emptyMap(),
)

@Keep
data class RewardAdPointsRequest(
    @SerializedName("ad_reward_id") val adRewardId: String,
)

@Keep
data class RewardsHubDto(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String? = null,
    @SerializedName("points_balance") val pointsBalance: Int = 0,
    @SerializedName("streak_count") val streakCount: Int = 0,
    @SerializedName("today_claimed") val todayClaimed: Boolean = false,
    @SerializedName("rewards_schedule") val rewardsSchedule: List<Int> = emptyList(),
    @SerializedName("prompt_of_the_day") val promptOfTheDay: PromptOfDayInHubDto? = null,
    @SerializedName("referral") val referral: ReferralHubDto? = null,
    @SerializedName("packs") val packs: HubPacksDto? = null,
    @SerializedName("progress") val progress: HubProgressDto? = null,
    @SerializedName("token_balances") val tokenBalances: Map<String, Int> = emptyMap(),
    @SerializedName("ab_flags") val abFlags: List<ExperimentAssignmentDto> = emptyList(),
)

@Keep
data class PromptOfDayInHubDto(
    @SerializedName("day_date") val dayDate: String? = null,
    @SerializedName("potd_mode") val potdMode: String? = null,
    @SerializedName("potd_unlock_cost_points") val potdUnlockCostPoints: Int = 0,
    @SerializedName("post") val post: V2PromptDto? = null,
)

@Keep
data class ReferralHubDto(
    @SerializedName("my_code") val myCode: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("referred_count") val referredCount: Int = 0,
    @SerializedName("rewarded_count") val rewardedCount: Int = 0,
    @SerializedName("pending_count") val pendingCount: Int = 0,
    @SerializedName("qualified_count") val qualifiedCount: Int = 0,
    @SerializedName("code") val code: String? = null,
    @SerializedName("stats") val stats: ReferralStatsDto? = null,
)

@Keep
data class ReferralStatsDto(
    @SerializedName("pending") val pending: Int = 0,
    @SerializedName("qualified") val qualified: Int = 0,
    @SerializedName("rewarded") val rewarded: Int = 0,
)

@Keep
data class HubPacksDto(
    @SerializedName("active") val active: List<PackSummaryDto> = emptyList(),
    @SerializedName("owned_count") val ownedCount: Int = 0,
)

@Keep
data class PackSummaryDto(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String = "",
    @SerializedName("description") val description: String? = null,
    @SerializedName("pricePoints") val pricePoints: Int = 0,
    @SerializedName("itemCount") val itemCount: Int = 0,
    @SerializedName("isActive") val isActive: Boolean = false,
    @SerializedName("createdAt") val createdAt: String? = null,
    @SerializedName("ownsPack") val ownsPack: Boolean = false,
)

@Keep
data class HubProgressDto(
    @SerializedName("xp") val xp: Int = 0,
    @SerializedName("level") val level: Int = 1,
    @SerializedName("next_level_xp") val nextLevelXp: Int = 0,
    @SerializedName("points_reward_next") val pointsRewardNext: Int = 0,
)

@Keep
data class GetMyCodeResponseDto(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String? = null,
    @SerializedName("code") val code: String? = null,
)

@Keep
data class ApplyReferralCodeRequest(
    @SerializedName("code") val code: String,
)

@Keep
data class ReferralClaimResponseDto(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String? = null,
    @SerializedName("referrer_points_added") val referrerPointsAdded: Int = 0,
    @SerializedName("referee_points_added") val refereePointsAdded: Int = 0,
)

@Keep
data class PacksResponseDto(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: List<PackSummaryDto> = emptyList(),
)

@Keep
data class PackDetailsResponseDto(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String? = null,
    @SerializedName("pack") val pack: PackSummaryDto? = null,
    @SerializedName("items") val items: List<V2PromptDto> = emptyList(),
)

@Keep
data class PointsPackUnlockRequest(
    @SerializedName("pack_id") val packId: Int,
)

@Keep
data class ProgressResponseDto(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String? = null,
    @SerializedName("xp") val xp: Int = 0,
    @SerializedName("level") val level: Int = 1,
    @SerializedName("next_level_xp") val nextLevelXp: Int = 0,
    @SerializedName("points_reward_next") val pointsRewardNext: Int = 0,
    @SerializedName("points_balance") val pointsBalance: Int = 0,
    @SerializedName("recent_events") val recentEvents: List<ProgressEventDto> = emptyList(),
)

@Keep
data class ProgressEventDto(
    @SerializedName("eventType") val eventType: String? = null,
    @SerializedName("xpDelta") val xpDelta: Int = 0,
    @SerializedName("createdAt") val createdAt: String? = null,
    @SerializedName("refType") val refType: String? = null,
    @SerializedName("refId") val refId: String? = null,
)

@Keep
data class V2ExperimentsResponseDto(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String? = null,
    @SerializedName("experiments") val experiments: List<ExperimentAssignmentDto> = emptyList(),
)

@Keep
data class ExperimentAssignmentDto(
    @SerializedName("key") val key: String,
    @SerializedName("variant") val variant: String,
    @SerializedName("payload") val payload: Map<String, Any?>? = null,
)
