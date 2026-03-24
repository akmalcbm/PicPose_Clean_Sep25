package com.picpose.bestphotographyapp.domain.model

import com.picpose.bestphotographyapp.data.remote.dto.v2.V2PromptDto

enum class PremiumSourceType {
    NONE,
    DIRECT,
    PACK_ONLY,
    PACK_AND_DIRECT,
    UNKNOWN,
}

enum class PromptVisibilityMode {
    GENERAL_FEED,
    PREMIUM_PACK_ONLY,
    HIDDEN,
}

data class PromptUnlockOptions(
    val canUnlockWithCredits: Boolean,
    val creditCost: Int?,
    val canUnlockWithRewardedAd: Boolean,
    val canUnlockWithToken: Boolean,
    val canAccessViaSubscription: Boolean,
    val canViewPack: Boolean,
    val primaryPackId: Int?,
    val primaryPackName: String?,
    val primaryPackDescription: String?,
    val primaryPackThumbnailUrl: String?,
    val primaryPackPricePoints: Int?,
    val ownsPrimaryPack: Boolean,
)

data class PromptAccessState(
    val isPremium: Boolean,
    val isLocked: Boolean,
    val isUnlocked: Boolean,
    val visibilityMode: PromptVisibilityMode,
    val premiumSourceType: PremiumSourceType,
    val unlockOptions: PromptUnlockOptions,
)

fun V2PromptDto.toPromptAccessState(): PromptAccessState {
    val methods = availableUnlockMethods.map { it.uppercase() }.toSet()

    val resolvedPremium = isPremium
    val resolvedPackOnly = isPackOnly ?: premiumSourceType.equals("PACK_ONLY", ignoreCase = true)
    val visibleInGeneral = isVisibleInGeneralFeed ?: !resolvedPackOnly

    val canViewPack = (primaryPackId != null) || premiumPackIds.isNotEmpty()
    val canUnlockWithCredits = when {
        isCreditUnlockable != null -> isCreditUnlockable
        methods.contains("CREDITS") -> true
        else -> resolvedPremium && premiumUnlockCostPoints > 0
    }
    val canUnlockWithRewardedAd = isRewardedUnlockable ?: methods.contains("REWARDED_AD")
    val canUnlockWithToken = isTokenUnlockable ?: methods.contains("TOKEN")
    val canAccessViaSubscription = isSubscriberUnlockable ?: methods.contains("SUBSCRIPTION")

    val resolvedIsUnlocked = alreadyUnlocked ?: !isLocked
    val resolvedIsLocked = !resolvedIsUnlocked

    val visibilityMode = when {
        !resolvedPremium -> PromptVisibilityMode.GENERAL_FEED
        resolvedPackOnly && !visibleInGeneral -> PromptVisibilityMode.PREMIUM_PACK_ONLY
        visibleInGeneral -> PromptVisibilityMode.GENERAL_FEED
        else -> PromptVisibilityMode.HIDDEN
    }

    val source = when {
        premiumSourceType.equals("NONE", ignoreCase = true) -> PremiumSourceType.NONE
        premiumSourceType.equals("DIRECT", ignoreCase = true) -> PremiumSourceType.DIRECT
        premiumSourceType.equals("PACK_ONLY", ignoreCase = true) -> PremiumSourceType.PACK_ONLY
        premiumSourceType.equals("PACK_AND_DIRECT", ignoreCase = true) -> PremiumSourceType.PACK_AND_DIRECT
        else -> PremiumSourceType.UNKNOWN
    }

    return PromptAccessState(
        isPremium = resolvedPremium,
        isLocked = resolvedIsLocked,
        isUnlocked = resolvedIsUnlocked,
        visibilityMode = visibilityMode,
        premiumSourceType = source,
        unlockOptions = PromptUnlockOptions(
            canUnlockWithCredits = canUnlockWithCredits,
            creditCost = if (canUnlockWithCredits) premiumUnlockCostPoints.coerceAtLeast(0) else null,
            canUnlockWithRewardedAd = canUnlockWithRewardedAd,
            canUnlockWithToken = canUnlockWithToken,
            canAccessViaSubscription = canAccessViaSubscription,
            canViewPack = canViewPack,
            primaryPackId = primaryPackId,
            primaryPackName = primaryPackName,
            primaryPackDescription = primaryPackDescription,
            primaryPackThumbnailUrl = primaryPackThumbnailUrl,
            primaryPackPricePoints = primaryPackPricePoints,
            ownsPrimaryPack = primaryPackOwned,
        ),
    )
}

fun V2PromptDto.supportsCreditsUnlock(): Boolean = toPromptAccessState().unlockOptions.canUnlockWithCredits
fun V2PromptDto.supportsRewardedUnlock(): Boolean = toPromptAccessState().unlockOptions.canUnlockWithRewardedAd
fun V2PromptDto.supportsTokenUnlock(): Boolean = toPromptAccessState().unlockOptions.canUnlockWithToken
fun V2PromptDto.supportsSubscriberAccess(): Boolean = toPromptAccessState().unlockOptions.canAccessViaSubscription
fun V2PromptDto.isPackOnlyPrompt(): Boolean = toPromptAccessState().visibilityMode == PromptVisibilityMode.PREMIUM_PACK_ONLY
