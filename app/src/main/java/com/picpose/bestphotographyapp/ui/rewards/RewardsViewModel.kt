package com.picpose.bestphotographyapp.ui.rewards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.picpose.bestphotographyapp.data.datastore.UserSessionManager
import com.picpose.bestphotographyapp.data.models.v2.BasicV2Response
import com.picpose.bestphotographyapp.data.models.v2.PackSummaryDto
import com.picpose.bestphotographyapp.data.models.v2.ProgressEventDto
import com.picpose.bestphotographyapp.data.models.v2.PromptOfDayInHubDto
import com.picpose.bestphotographyapp.data.models.v2.RewardsHubDto
import com.picpose.bestphotographyapp.data.models.v2.V2PromptDto
import com.picpose.bestphotographyapp.data.repository.RewardsRepository
import com.picpose.bestphotographyapp.data.repository.V2PromptsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val DEFAULT_STREAK_REWARDS = listOf(10, 20, 30, 40, 50, 60, 100)

data class RewardsUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val pointsBalance: Int = 0,
    val tokenBalances: Map<String, Int> = emptyMap(),
    val streakCount: Int = 0,
    val todayClaimed: Boolean = false,
    val rewardsSchedule: List<Int> = DEFAULT_STREAK_REWARDS,
    val promptOfTheDay: PromptOfDayInHubDto? = null,
    val publicPromptOfTheDay: V2PromptDto? = null,
    val promptOfDayMode: String? = null,
    val promptOfDayCost: Int = 0,
    val referralCode: String? = null,
    val referralStatus: String? = null,
    val referralReferredCount: Int = 0,
    val referralRewardedCount: Int = 0,
    val referralPendingCount: Int = 0,
    val packs: List<PackSummaryDto> = emptyList(),
    val ownedPackCount: Int = 0,
    val level: Int = 1,
    val xp: Int = 0,
    val nextLevelXp: Int = 0,
    val progressEvents: List<ProgressEventDto> = emptyList(),
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

    val isLoggedIn: StateFlow<Boolean> = userSessionManager.userToken
        .map { !it.isNullOrBlank() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    fun loadRewards(forceRefresh: Boolean = false) {
        val loggedIn = isLoggedIn.value
        if (loggedIn) {
            loadLoggedInRewards(forceRefresh)
        } else {
            loadLoggedOutRewards()
        }
    }

    fun refresh() {
        loadRewards(forceRefresh = true)
    }

    fun claimDailyLogin() {
        viewModelScope.launch {
            rewardsRepository.claimDailyLogin()
                .onSuccess { response ->
                    applyWalletAction(response)
                    loadLoggedInRewards(forceRefresh = true)
                }
                .onFailure { throwable ->
                    _uiState.update { it.copy(statusMessage = throwable.message ?: "Failed to claim streak reward.") }
                }
        }
    }

    fun applyReferralCode(code: String) {
        viewModelScope.launch {
            rewardsRepository.applyReferralCode(code.trim())
                .onSuccess { response ->
                    _uiState.update {
                        it.copy(
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
                    _uiState.update { it.copy(statusMessage = referralApplyErrorMessage(throwable)) }
                }
        }
    }

    fun claimReferralReward() {
        viewModelScope.launch {
            rewardsRepository.claimReferralReward()
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(statusMessage = "Reward credited to your wallet")
                    }
                    loadLoggedInRewards(forceRefresh = true)
                }
                .onFailure { throwable ->
                    _uiState.update { it.copy(statusMessage = referralClaimErrorMessage(throwable)) }
                }
        }
    }

    fun rewardAdPoints(adRewardId: String) {
        viewModelScope.launch {
            rewardsRepository.rewardAdPoints(adRewardId)
                .onSuccess { response ->
                    _uiState.update { current ->
                        current.copy(
                            pointsBalance = response.pointsBalance ?: current.pointsBalance,
                            statusMessage = when {
                                response.pointsAdded == 0 -> "Reward already claimed."
                                else -> response.message ?: "Credits added successfully."
                            },
                        )
                    }
                    loadLoggedInRewards(forceRefresh = true)
                }
                .onFailure { throwable ->
                    _uiState.update { it.copy(statusMessage = throwable.message ?: "Failed to add ad reward.") }
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
                statusMessage = null,
            )
        }

        viewModelScope.launch {
            rewardsRepository.getCachedHub()?.let(::applyHub)

            rewardsRepository.refreshHub()
                .onSuccess { hub ->
                    applyHub(hub)
                }
                .onFailure { throwable ->
                    _uiState.update { current ->
                        current.copy(
                            isLoading = false,
                            isRefreshing = false,
                            statusMessage = throwable.message ?: "Failed to load rewards hub.",
                        )
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
                pointsBalance = 0,
                tokenBalances = emptyMap(),
                streakCount = 0,
                todayClaimed = false,
                rewardsSchedule = DEFAULT_STREAK_REWARDS,
                referralCode = null,
                referralStatus = null,
                referralReferredCount = 0,
                referralRewardedCount = 0,
                referralPendingCount = 0,
                packs = emptyList(),
                ownedPackCount = 0,
                level = 1,
                xp = 0,
                nextLevelXp = 0,
                progressEvents = emptyList(),
                statusMessage = null,
            )
        }

        viewModelScope.launch {
            promptsRepository.getPromptOfTheDay()
                .onSuccess { prompt ->
                    _uiState.update { current ->
                        current.copy(
                            isLoading = false,
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
                pointsBalance = hub.pointsBalance,
                tokenBalances = hub.tokenBalances,
                streakCount = hub.streakCount,
                todayClaimed = hub.todayClaimed,
                rewardsSchedule = hub.rewardsSchedule.ifEmpty { DEFAULT_STREAK_REWARDS },
                promptOfTheDay = hub.promptOfTheDay,
                publicPromptOfTheDay = hub.promptOfTheDay?.post,
                promptOfDayMode = hub.promptOfTheDay?.potdMode,
                promptOfDayCost = hub.promptOfTheDay?.potdUnlockCostPoints ?: 0,
                referralCode = hub.referral?.myCode ?: hub.referral?.code,
                referralStatus = hub.referral?.status,
                referralReferredCount = hub.referral?.referredCount ?: hub.referral?.stats?.qualified ?: 0,
                referralRewardedCount = hub.referral?.rewardedCount ?: hub.referral?.stats?.rewarded ?: 0,
                referralPendingCount = hub.referral?.pendingCount ?: hub.referral?.stats?.pending ?: 0,
                packs = hub.packs?.active.orEmpty(),
                ownedPackCount = hub.packs?.ownedCount ?: 0,
                level = hub.progress?.level ?: current.level,
                xp = hub.progress?.xp ?: current.xp,
                nextLevelXp = hub.progress?.nextLevelXp ?: current.nextLevelXp,
            )
        }
    }

    private fun applyWalletAction(response: BasicV2Response) {
        _uiState.update { current ->
            current.copy(
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
}
