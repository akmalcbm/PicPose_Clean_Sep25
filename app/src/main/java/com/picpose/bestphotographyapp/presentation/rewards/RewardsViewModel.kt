/**
 * ---
 * File: RewardsViewModel.kt
 * Layer: Presentation (MVVM)
 * Project: PicPose
 *
 * Purpose:
 * Owns screen state and coordinates the MVVM flow between Compose UI and repository/data operations.
 *
 * Interactions:
 * Observed by Compose screens. It transforms repository results into StateFlow values that the UI collects.
 *
 * Data Flow:
 * UI (Compose) -> ViewModel -> Repository -> Local/Remote Data Source -> Room/API
 *
 * Maintainer Notes:
 * - Expose observable UI state here, but keep composable rendering decisions in the UI layer.
 * - Business rules belong in repositories or dedicated domain classes if the project introduces use cases later.
 * ---
 */

package com.picpose.bestphotographyapp.presentation.rewards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.picpose.bestphotographyapp.data.local.datastore.UserSessionManager
import com.picpose.bestphotographyapp.data.remote.dto.v2.BasicV2Response
import com.picpose.bestphotographyapp.data.remote.dto.v2.PackSummaryDto
import com.picpose.bestphotographyapp.data.remote.dto.v2.ProgressEventDto
import com.picpose.bestphotographyapp.data.remote.dto.v2.PromptOfDayInHubDto
import com.picpose.bestphotographyapp.data.remote.dto.v2.RewardsHubDto
import com.picpose.bestphotographyapp.data.remote.dto.v2.V2PromptDto
import com.picpose.bestphotographyapp.data.repository.RewardsRepository
import com.picpose.bestphotographyapp.data.repository.V2ApiException
import com.picpose.bestphotographyapp.data.repository.V2PromptsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val DEFAULT_STREAK_REWARDS = listOf(10, 20, 30, 40, 50, 60, 100)

enum class RewardsAccessState {
    Loading,
    Guest,
    Authenticated,
    AuthExpired,
}

sealed interface RewardsUiEvent {
    data class ClaimSuccess(val pointsAdded: Int) : RewardsUiEvent
    data class AdRewardSuccess(val pointsAdded: Int) : RewardsUiEvent
    data class Error(val message: String) : RewardsUiEvent
}

data class CreditActivityItem(
    val id: String,
    val title: String,
    val delta: Int,
    val subtitle: String? = null,
)

data class RewardsUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isClaimingReward: Boolean = false,
    val isApplyingCode: Boolean = false,
    val accessState: RewardsAccessState = RewardsAccessState.Loading,
    val pointsBalance: Int = 0,
    val tokenBalances: Map<String, Int> = emptyMap(),
    val adRewardedToday: Boolean = false,
    val adDailyCount: Int? = null,
    val adDailyCap: Int? = null,
    val adRewardPoints: Int = 10,
    val adRewardAvailable: Boolean = true,
    val streakCount: Int = 0,
    val todayClaimed: Boolean = false,
    val rewardsSchedule: List<Int> = DEFAULT_STREAK_REWARDS,
    val promptOfTheDay: PromptOfDayInHubDto? = null,
    val publicPromptOfTheDay: V2PromptDto? = null,
    val promptOfDayMode: String? = null,
    val promptOfDayCost: Int = 0,
    val referralCode: String? = null,
    val referralStatus: String? = null,
    val referralStatusLabel: String = "",
    val hasAppliedReferralCode: Boolean = false,
    val canClaimReferralReward: Boolean = false,
    val isReferralRewardClaimed: Boolean = false,
    val referralReferredCount: Int = 0,
    val referralRewardedCount: Int = 0,
    val referralPendingCount: Int = 0,
    val packs: List<PackSummaryDto> = emptyList(),
    val ownedPackCount: Int = 0,
    val level: Int = 1,
    val xp: Int = 0,
    val nextLevelXp: Int = 0,
    val progressEvents: List<ProgressEventDto> = emptyList(),
    val recentCreditActivities: List<CreditActivityItem> = emptyList(),
    val statusMessage: String? = null,
)

@HiltViewModel
class RewardsViewModel @Inject constructor(
    private val rewardsRepository: RewardsRepository,
    private val promptsRepository: V2PromptsRepository,
    userSessionManager: UserSessionManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RewardsUiState())
    val uiState: StateFlow<RewardsUiState> = _uiState.asStateFlow()
    private val _events = MutableSharedFlow<RewardsUiEvent>()
    val events = _events.asSharedFlow()

    private val sessionAccessState: StateFlow<RewardsAccessState> = userSessionManager.authenticatedSession
        .map { session ->
            if (session == null) RewardsAccessState.Guest else RewardsAccessState.Authenticated
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = RewardsAccessState.Loading,
        )

    val authState: StateFlow<RewardsAccessState> = sessionAccessState

    val isLoggedIn: StateFlow<Boolean> = sessionAccessState
        .map { it == RewardsAccessState.Authenticated }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false,
        )

    private val currentUserId: StateFlow<String?> = userSessionManager.userId
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    fun loadRewards(forceRefresh: Boolean = false) {
        when (sessionAccessState.value) {
            RewardsAccessState.Loading -> {
                _uiState.update { state ->
                    state.copy(
                        isLoading = true,
                        isRefreshing = false,
                        accessState = RewardsAccessState.Loading,
                        statusMessage = null,
                    )
                }
            }
            RewardsAccessState.Authenticated -> loadLoggedInRewards(forceRefresh)
            RewardsAccessState.Guest -> loadLoggedOutRewards()
            RewardsAccessState.AuthExpired -> loadLoggedOutRewards()
        }
    }

    fun refresh() {
        loadRewards(forceRefresh = true)
    }

    fun claimDailyLogin() {
        if (!canRunAuthenticatedAction()) {
            return
        }
        viewModelScope.launch {
            val previousBalance = _uiState.value.pointsBalance
            _uiState.update { it.copy(isClaimingReward = true) }
            rewardsRepository.claimDailyLogin()
                .onSuccess { response ->
                    applyWalletAction(response)
                    val latestBalance = response.pointsBalance ?: _uiState.value.pointsBalance
                    val pointsAdded = response.pointsAdded ?: (latestBalance - previousBalance).coerceAtLeast(0)
                    if (pointsAdded > 0) {
                        pushRecentCreditActivity(
                            title = "Daily streak reward",
                            delta = pointsAdded,
                            subtitle = "Daily streak",
                        )
                    }
                    _events.emit(RewardsUiEvent.ClaimSuccess(pointsAdded = pointsAdded))
                    loadLoggedInRewards(forceRefresh = true)
                }
                .onFailure { throwable ->
                    if (throwable.isUnauthorizedRequest()) {
                        markAuthExpired()
                        _events.emit(RewardsUiEvent.Error("Session expired. Please log in again."))
                        return@onFailure
                    }
                    val message = mapDailyClaimError(throwable)
                    _uiState.update { it.copy(statusMessage = message, isClaimingReward = false) }
                    _events.emit(RewardsUiEvent.Error(message))
                }
        }
    }

    fun applyReferralCode(code: String) {
        if (!canRunAuthenticatedAction()) {
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isApplyingCode = true) }
            rewardsRepository.applyReferralCode(code.trim())
                .onSuccess { response ->
                    _uiState.update {
                        it.copy(
                            isApplyingCode = false,
                            statusMessage = if (response.alreadyApplied == true || response.alreadyClaimed == true) {
                                "You can apply only one code"
                            } else {
                                "Code applied successfully. Reward will unlock after your first premium unlock"
                            }
                        )
                    }
                    loadLoggedInRewards(forceRefresh = true)
                }
                .onFailure { throwable ->
                    if (throwable.isUnauthorizedRequest()) {
                        markAuthExpired()
                        return@onFailure
                    }
                    val message = referralApplyErrorMessage(throwable)
                    _uiState.update { it.copy(statusMessage = message, isApplyingCode = false) }
                    _events.emit(RewardsUiEvent.Error(message))
                }
        }
    }

    fun claimReferralReward() {
        if (!canRunAuthenticatedAction()) {
            return
        }
        viewModelScope.launch {
            if (!_uiState.value.canClaimReferralReward) {
                return@launch
            }
            rewardsRepository.claimReferralReward()
                .onSuccess { response ->
                    val pointsAdded = (response.referrerPointsAdded + response.refereePointsAdded).coerceAtLeast(0)
                    if (pointsAdded > 0) {
                        pushRecentCreditActivity(
                            title = "Referral reward",
                            delta = pointsAdded,
                            subtitle = "Invite & earn",
                        )
                    }
                    _uiState.update { state ->
                        state.copy(
                            referralStatus = "REWARDED",
                            referralStatusLabel = "Reward Claimed",
                            canClaimReferralReward = false,
                            isReferralRewardClaimed = true,
                            statusMessage = "Reward credited to your wallet",
                        )
                    }
                    loadLoggedInRewards(forceRefresh = true)
                }
                .onFailure { throwable ->
                    if (throwable.isUnauthorizedRequest()) {
                        markAuthExpired()
                        return@onFailure
                    }
                    val message = referralClaimErrorMessage(throwable)
                    _uiState.update { it.copy(statusMessage = message) }
                    _events.emit(RewardsUiEvent.Error(message))
                }
        }
    }

    fun rewardAdPoints(adRewardId: String) {
        if (!canRunAuthenticatedAction()) {
            return
        }
        viewModelScope.launch {
            val previousBalance = _uiState.value.pointsBalance
            rewardsRepository.rewardAdPoints(adRewardId)
                .onSuccess { response ->
                    val latestBalance = response.pointsBalance ?: _uiState.value.pointsBalance
                    val pointsAdded = response.pointsAdded ?: (latestBalance - previousBalance).coerceAtLeast(0)
                    _uiState.update { current ->
                        val responseDailyCount = response.adDailyCount ?: current.adDailyCount
                        val responseDailyCap = response.adDailyCap ?: current.adDailyCap
                        val isRewardAvailable = response.adRewardAvailable
                            ?: when {
                                responseDailyCount != null && responseDailyCap != null && responseDailyCap > 0 ->
                                    responseDailyCount < responseDailyCap
                                else -> current.adRewardAvailable
                            }
                        current.copy(
                            pointsBalance = latestBalance,
                            adRewardedToday = pointsAdded > 0 || !isRewardAvailable || current.adRewardedToday,
                            adDailyCount = responseDailyCount,
                            adDailyCap = responseDailyCap,
                            adRewardPoints = response.adRewardPoints ?: current.adRewardPoints,
                            adRewardAvailable = isRewardAvailable,
                            statusMessage = response.message ?: when {
                                response.pointsAdded == 0 -> "Reward already claimed."
                                else -> "Credits added successfully."
                            },
                        )
                    }
                    if (pointsAdded > 0) {
                        pushRecentCreditActivity(
                            title = "Reward ad completed",
                            delta = pointsAdded,
                            subtitle = "Reward ad",
                        )
                        _events.emit(RewardsUiEvent.AdRewardSuccess(pointsAdded = pointsAdded))
                    }
                    loadLoggedInRewards(forceRefresh = true)
                }
                .onFailure { throwable ->
                    if (throwable.isUnauthorizedRequest()) {
                        markAuthExpired()
                        return@onFailure
                    }
                    val message = throwable.message ?: "Failed to add ad reward."
                    _uiState.update { it.copy(statusMessage = message) }
                    _events.emit(RewardsUiEvent.Error(message))
                }
        }
    }

    fun setStatusMessage(message: String?) {
        _uiState.update { it.copy(statusMessage = message) }
    }

    private fun loadLoggedInRewards(forceRefresh: Boolean) {
        _uiState.update {
            it.copy(
                isLoading = !forceRefresh && it.pointsBalance == 0 && it.promptOfTheDay == null,
                isRefreshing = forceRefresh,
                isClaimingReward = false,
                isApplyingCode = false,
                accessState = RewardsAccessState.Authenticated,
                statusMessage = null,
            )
        }

        viewModelScope.launch {
            val userId = currentUserId.value ?: currentUserId.filterNotNull().firstOrNull()
            if (!userId.isNullOrBlank()) {
                rewardsRepository.getCachedReferralCode(userId)?.let { cachedCode ->
                    _uiState.update { current -> current.copy(referralCode = cachedCode) }
                }
            }
            rewardsRepository.getCachedHub()?.let(::applyHub)

            rewardsRepository.refreshHub(userIdForCache = userId)
                .onSuccess { hub ->
                    applyHub(hub)
                }
                .onFailure { throwable ->
                    if (throwable.isUnauthorizedRequest()) {
                        markAuthExpired()
                        return@onFailure
                    }
                    _uiState.update { current ->
                        current.copy(
                            isLoading = false,
                            isRefreshing = false,
                            isClaimingReward = false,
                            isApplyingCode = false,
                            accessState = RewardsAccessState.Authenticated,
                            statusMessage = throwable.message ?: "Failed to load rewards hub.",
                        )
                    }
                }
        }

        viewModelScope.launch {
            val userId = currentUserId.value ?: currentUserId.filterNotNull().firstOrNull()
            if (!userId.isNullOrBlank()) {
                rewardsRepository.getMyCode(userId)
                    .onSuccess { response ->
                        response.code?.takeIf { it.isNotBlank() }?.let { latestCode ->
                            _uiState.update { current -> current.copy(referralCode = latestCode) }
                        }
                    }
            }
        }

        viewModelScope.launch {
            rewardsRepository.getProgress()
                .onSuccess { progress ->
                    _uiState.update { current ->
                        current.copy(
                            level = progress.level,
                            xp = progress.xp,
                            nextLevelXp = progress.nextLevelXp,
                            progressEvents = progress.recentEvents,
                        )
                    }
                }
        }
    }

    private fun loadLoggedOutRewards() {
        _uiState.update {
            it.copy(
                isLoading = true,
                isRefreshing = false,
                isClaimingReward = false,
                isApplyingCode = false,
                accessState = RewardsAccessState.Guest,
                pointsBalance = 0,
                tokenBalances = emptyMap(),
                adRewardedToday = false,
                adDailyCount = null,
                adDailyCap = null,
                adRewardPoints = 10,
                adRewardAvailable = true,
                streakCount = 0,
                todayClaimed = false,
                rewardsSchedule = DEFAULT_STREAK_REWARDS,
                referralCode = null,
                referralStatus = null,
                referralStatusLabel = "",
                hasAppliedReferralCode = false,
                canClaimReferralReward = false,
                isReferralRewardClaimed = false,
                referralReferredCount = 0,
                referralRewardedCount = 0,
                referralPendingCount = 0,
                packs = emptyList(),
                ownedPackCount = 0,
                level = 1,
                xp = 0,
                nextLevelXp = 0,
                progressEvents = emptyList(),
                recentCreditActivities = emptyList(),
                statusMessage = null,
            )
        }

        viewModelScope.launch {
            promptsRepository.getPromptOfTheDay()
                .onSuccess { prompt ->
                    _uiState.update { current ->
                        current.copy(
                            isLoading = false,
                            accessState = RewardsAccessState.Guest,
                            publicPromptOfTheDay = prompt,
                            promptOfTheDay = null,
                            promptOfDayMode = if (prompt?.isLocked == true) "PREMIUM" else "FREE",
                            promptOfDayCost = prompt?.premiumUnlockCostPoints ?: 0,
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update { current ->
                        current.copy(
                            isLoading = false,
                            accessState = RewardsAccessState.Guest,
                            statusMessage = throwable.message ?: "Rewards are unavailable right now.",
                        )
                    }
                }
        }
    }

    private fun applyHub(hub: RewardsHubDto) {
        _uiState.update { current ->
            current.copy(
                isLoading = false,
                isRefreshing = false,
                isClaimingReward = false,
                isApplyingCode = false,
                accessState = RewardsAccessState.Authenticated,
                pointsBalance = hub.pointsBalance,
                tokenBalances = hub.tokenBalances,
                adDailyCount = hub.adDailyCount,
                adDailyCap = hub.adDailyCap,
                adRewardPoints = hub.adRewardPoints?.takeIf { it > 0 } ?: current.adRewardPoints,
                adRewardAvailable = hub.adRewardAvailable
                    ?: when {
                        hub.adDailyCount != null && hub.adDailyCap != null && hub.adDailyCap > 0 ->
                            hub.adDailyCount < hub.adDailyCap
                        else -> current.adRewardAvailable
                    },
                adRewardedToday = when {
                    hub.adRewardAvailable != null -> !hub.adRewardAvailable
                    hub.adDailyCount != null && hub.adDailyCap != null && hub.adDailyCap > 0 ->
                        hub.adDailyCount >= hub.adDailyCap
                    else -> current.adRewardedToday
                },
                streakCount = hub.streakCount,
                todayClaimed = hub.todayClaimed,
                rewardsSchedule = hub.rewardsSchedule.ifEmpty { DEFAULT_STREAK_REWARDS },
                promptOfTheDay = hub.promptOfTheDay,
                publicPromptOfTheDay = hub.promptOfTheDay?.post,
                promptOfDayMode = hub.promptOfTheDay?.potdMode,
                promptOfDayCost = hub.promptOfTheDay?.potdUnlockCostPoints ?: 0,
                referralCode = hub.referral?.myCode ?: hub.referral?.code,
                referralStatus = hub.referral?.status,
                referralStatusLabel = referralStatusLabel(hub.referral?.status),
                hasAppliedReferralCode = !hub.referral?.status.isNullOrBlank(),
                canClaimReferralReward = hub.referral?.status.equals("QUALIFIED", ignoreCase = true),
                isReferralRewardClaimed = hub.referral?.status.equals("REWARDED", ignoreCase = true),
                referralReferredCount = hub.referral?.referredCount ?: hub.referral?.stats?.qualified ?: 0,
                referralRewardedCount = hub.referral?.rewardedCount ?: hub.referral?.stats?.rewarded ?: 0,
                referralPendingCount = hub.referral?.pendingCount ?: hub.referral?.stats?.pending ?: 0,
                packs = hub.packs?.active.orEmpty(),
                ownedPackCount = hub.packs?.ownedCount ?: 0,
                level = hub.progress?.level ?: current.level,
                xp = hub.progress?.xp ?: current.xp,
                nextLevelXp = hub.progress?.nextLevelXp ?: current.nextLevelXp,
                statusMessage = null,
            )
        }
    }

    private fun applyWalletAction(response: BasicV2Response) {
        _uiState.update { current ->
            current.copy(
                isClaimingReward = false,
                pointsBalance = response.pointsBalance ?: current.pointsBalance,
                todayClaimed = response.claimed == true || response.alreadyClaimed == true || current.todayClaimed,
                statusMessage = when {
                    response.alreadyClaimed == true -> "Today's streak reward is already claimed."
                    response.claimed == true -> "Streak reward claimed."
                    else -> response.message
                },
            )
        }
    }

    private fun referralApplyErrorMessage(throwable: Throwable): String {
        val message = throwable.message.orEmpty()
        return when {
            message.contains("one code", ignoreCase = true) -> "You can apply only one code"
            message.contains("already applied", ignoreCase = true) -> "You can apply only one code"
            else -> throwable.message ?: "Failed to apply referral code."
        }
    }

    private fun referralClaimErrorMessage(throwable: Throwable): String {
        val message = throwable.message.orEmpty()
        return when {
            message.contains("first premium unlock", ignoreCase = true) -> "Reward will unlock after your first premium unlock"
            message.contains("already rewarded", ignoreCase = true) -> "Reward credited to your wallet"
            else -> throwable.message ?: "Referral reward is not ready yet."
        }
    }

    private fun referralStatusLabel(status: String?): String {
        return when (status?.trim()?.uppercase()) {
            "PENDING" -> "Complete your first unlock to qualify"
            "QUALIFIED" -> "You are qualified. Claim your reward now."
            "REWARDED" -> "Reward Claimed"
            else -> "Apply a code first"
        }
    }

    private fun canRunAuthenticatedAction(): Boolean {
        return when (sessionAccessState.value) {
            RewardsAccessState.Loading -> {
                _uiState.update { current ->
                    current.copy(
                        accessState = RewardsAccessState.Loading,
                        isClaimingReward = false,
                        isApplyingCode = false,
                    )
                }
                false
            }
            RewardsAccessState.Guest -> {
                _uiState.update { current ->
                    current.copy(
                        accessState = RewardsAccessState.Guest,
                        isClaimingReward = false,
                        isApplyingCode = false,
                    )
                }
                false
            }
            RewardsAccessState.Authenticated -> {
                // Recover from stale AuthExpired UI when session is valid again.
                if (_uiState.value.accessState == RewardsAccessState.AuthExpired) {
                    _uiState.update { current -> current.copy(accessState = RewardsAccessState.Authenticated) }
                }
                true
            }
            RewardsAccessState.AuthExpired -> {
                markAuthExpired()
                false
            }
        }
    }

    private fun markAuthExpired() {
        _uiState.update { current ->
            current.copy(
                isLoading = false,
                isRefreshing = false,
                isClaimingReward = false,
                isApplyingCode = false,
                accessState = RewardsAccessState.AuthExpired,
                statusMessage = null,
            )
        }
    }

    private fun Throwable.isUnauthorizedRequest(): Boolean {
        return this is V2ApiException && (code == 401 || code == 403)
    }

    private fun mapDailyClaimError(throwable: Throwable): String {
        val raw = throwable.message.orEmpty()
        if (throwable is IOException) {
            return "Network error while claiming reward. Please try again."
        }
        if (throwable is V2ApiException) {
            return when {
                throwable.code == 401 || throwable.code == 403 -> "Session expired. Please log in again."
                raw.contains("already claimed", ignoreCase = true) -> "You already claimed today's streak reward."
                throwable.code in 500..599 -> "Server error while claiming reward. Please try again."
                throwable.code == 400 -> "Invalid reward request. Please refresh and try again."
                else -> raw.ifBlank { "Unable to claim reward right now." }
            }
        }
        return when {
            raw.contains("already claimed", ignoreCase = true) -> "You already claimed today's streak reward."
            raw.isBlank() -> "Unable to claim reward right now."
            else -> raw
        }
    }

    private fun pushRecentCreditActivity(
        title: String,
        delta: Int,
        subtitle: String? = null,
    ) {
        if (delta == 0) return
        val item = CreditActivityItem(
            id = "credit_${System.currentTimeMillis()}_${delta}_${title.hashCode()}",
            title = title,
            delta = delta,
            subtitle = subtitle,
        )
        _uiState.update { current ->
            current.copy(
                recentCreditActivities = (listOf(item) + current.recentCreditActivities).take(5)
            )
        }
    }
}
