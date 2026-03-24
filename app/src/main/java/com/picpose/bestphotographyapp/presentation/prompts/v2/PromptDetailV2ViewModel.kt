/**
 * ---
 * File: PromptDetailV2ViewModel.kt
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

package com.picpose.bestphotographyapp.presentation.prompts.v2

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.picpose.bestphotographyapp.data.local.datastore.UserSessionManager
import com.picpose.bestphotographyapp.data.remote.dto.v2.V2PromptDto
import com.picpose.bestphotographyapp.data.repository.EngagementRepository
import com.picpose.bestphotographyapp.data.repository.RewardsRepository
import com.picpose.bestphotographyapp.data.repository.V2ApiException
import com.picpose.bestphotographyapp.data.repository.V2PromptsRepository
import com.picpose.bestphotographyapp.domain.model.toPromptAccessState
import com.picpose.bestphotographyapp.domain.model.supportsCreditsUnlock
import com.picpose.bestphotographyapp.domain.model.supportsRewardedUnlock
import com.picpose.bestphotographyapp.domain.model.supportsTokenUnlock
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PromptDetailV2UiState(
    val isLoading: Boolean = false,
    val prompt: V2PromptDto? = null,
    val similarPrompts: List<V2PromptDto> = emptyList(),
    val isLoadingMoreSimilar: Boolean = false,
    val hasMoreSimilar: Boolean = false,
    val showAdLoader: Boolean = false,
    val viewsCount: Int = 0,
    val likesCount: Int = 0,
    val favoritesCount: Int = 0,
    val isLikedLocal: Boolean = false,
    val isFavoriteLocal: Boolean = false,
    val message: String? = null,
    val requiresLogin: Boolean = false,
    val isUnlockingWithPoints: Boolean = false,
    val isUnlockingWithToken: Boolean = false,
    val isUnlockingWithAd: Boolean = false,
    val pointsBalance: Int? = null,
    val isPointsBalanceLoading: Boolean = false,
)

enum class PromptDetailAuthState {
    Loading,
    LoggedOut,
    LoggedIn,
}

@HiltViewModel
class PromptDetailV2ViewModel @Inject constructor(
    private val promptsRepository: V2PromptsRepository,
    private val engagementRepository: EngagementRepository,
    private val rewardsRepository: RewardsRepository,
    private val userSessionManager: UserSessionManager,
) : ViewModel() {
    private var similarOffset: Int = 0
    private val similarLimit: Int = 10
    private var hasMoreSimilar: Boolean = true
    private var similarPromptClickCount: Int = 0
    private val interstitialInterval: Int = 3
    private var lastPointsSyncToken: String? = null

    private val _uiState = MutableStateFlow(PromptDetailV2UiState())
    val uiState: StateFlow<PromptDetailV2UiState> = _uiState.asStateFlow()

    val authState: StateFlow<PromptDetailAuthState> = userSessionManager.authenticatedSession
        .map { session ->
            if (session == null) PromptDetailAuthState.LoggedOut else PromptDetailAuthState.LoggedIn
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PromptDetailAuthState.Loading,
        )

    val isLoggedIn: StateFlow<Boolean> = authState
        .map { it == PromptDetailAuthState.LoggedIn }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    init {
        viewModelScope.launch {
            userSessionManager.authenticatedSession.collectLatest { session ->
                if (session == null) {
                    lastPointsSyncToken = null
                    _uiState.update {
                        it.copy(
                            pointsBalance = null,
                            isPointsBalanceLoading = false,
                        )
                    }
                } else {
                    val currentToken = session.token
                    if (currentToken != lastPointsSyncToken) {
                        lastPointsSyncToken = currentToken
                        syncPointsBalance(forceRefresh = false, reportFailureToUser = false)
                    }
                }
            }
        }
    }

    fun loadPrompt(promptId: String, forceRefresh: Boolean = false) {
        if (_uiState.value.isLoading && !forceRefresh) return

        _uiState.update {
            it.copy(
                isLoading = true,
                prompt = if (forceRefresh) null else it.prompt,
                similarPrompts = if (forceRefresh) emptyList() else it.similarPrompts,
                isLoadingMoreSimilar = false,
                hasMoreSimilar = false,
                showAdLoader = false,
                viewsCount = 0,
                likesCount = 0,
                favoritesCount = 0,
                isLikedLocal = false,
                isFavoriteLocal = false,
                message = null,
                requiresLogin = false,
            )
        }
        similarOffset = 0
        hasMoreSimilar = true

        viewModelScope.launch {
            promptsRepository.getPromptDetail(promptId)
                .onSuccess { prompt ->
                    runCatching {
                        engagementRepository.registerView(prompt.id)
                    }
                    val localState = runCatching { engagementRepository.getState(prompt.id) }.getOrNull()
                    val likesCount = prompt.likes.coerceAtLeast(0) + if (localState?.isLiked == true) 1 else 0
                    val favoritesCount = prompt.favorites.coerceAtLeast(0) + if (localState?.isFavorited == true) 1 else 0
                    val viewsCount = prompt.views.coerceAtLeast(0) + (localState?.localViewCount ?: 0).coerceAtLeast(0)

                    _uiState.update { current ->
                        current.copy(
                            isLoading = false,
                            prompt = prompt,
                            viewsCount = viewsCount,
                            likesCount = likesCount,
                            favoritesCount = favoritesCount,
                            isLikedLocal = localState?.isLiked == true,
                            isFavoriteLocal = localState?.isFavorited == true,
                        )
                    }
                    if (prompt.supportsCreditsUnlock() && authState.value == PromptDetailAuthState.LoggedIn) {
                        syncPointsBalance(forceRefresh = false, reportFailureToUser = false)
                    }
                    loadSimilarPrompts(reset = true)
                }
                .onFailure { throwable ->
                    _uiState.update { current ->
                        current.copy(
                            isLoading = false,
                            requiresLogin = throwable is V2ApiException && (throwable.code == 401 || throwable.code == 403),
                            message = if (throwable is V2ApiException && (throwable.code == 401 || throwable.code == 403)) {
                                "Session expired. Please login again."
                            } else {
                                throwable.message ?: "Failed to load prompt."
                            },
                        )
                    }
                }
        }
    }

    fun loadMoreSimilarPrompts() {
        if (_uiState.value.isLoadingMoreSimilar || !hasMoreSimilar) return
        loadSimilarPrompts(reset = false)
    }

    fun unlockWithPoints(promptId: String) {
        val prompt = _uiState.value.prompt ?: return
        if (!prompt.supportsCreditsUnlock()) {
            _uiState.update { it.copy(message = "Credits unlock is not available for this prompt.") }
            return
        }
        if (_uiState.value.isUnlockingWithPoints) return
        if (authState.value != PromptDetailAuthState.LoggedIn) {
            _uiState.update {
                it.copy(
                    requiresLogin = true,
                    message = "Please login to unlock premium prompts.",
                )
            }
            return
        }

        val unlockCost = resolveCreditUnlockCost(prompt)
        _uiState.update { current ->
            current.copy(
                isUnlockingWithPoints = true,
                message = null,
                requiresLogin = false,
            )
        }

        viewModelScope.launch {
            val resolvedBalance = ensurePointsBalanceLoaded(forceRefresh = false)
            if (resolvedBalance == null) {
                _uiState.update { current ->
                    current.copy(
                        isUnlockingWithPoints = false,
                        message = "Unable to verify your credits right now. Please try again.",
                    )
                }
                return@launch
            }

            if (unlockCost > resolvedBalance) {
                _uiState.update { current ->
                    current.copy(
                        isUnlockingWithPoints = false,
                        message = buildInsufficientCreditsMessage(unlockCost, resolvedBalance),
                    )
                }
                return@launch
            }

            promptsRepository.unlockPromptWithPoints(promptId)
                .onSuccess { result ->
                    val serverCost = result.cost ?: unlockCost
                    val latestBalance = result.pointsBalance
                        ?: (resolvedBalance - serverCost).coerceAtLeast(0)

                    _uiState.update { current ->
                        current.copy(
                            pointsBalance = latestBalance,
                            message = when {
                                result.duplicate -> "You already unlocked this prompt."
                                result.unlocked -> "Prompt unlocked."
                                else -> "Unlock completed."
                            },
                        )
                    }

                    loadPrompt(promptId, forceRefresh = true)
                    syncPointsBalance(forceRefresh = true, reportFailureToUser = false)
                }
                .onFailure { throwable ->
                    val requiresLogin = throwable is V2ApiException &&
                        (throwable.code == 401 || throwable.code == 403)
                    _uiState.update { current ->
                        current.copy(
                            requiresLogin = requiresLogin,
                            message = mapPointsUnlockError(
                                throwable = throwable,
                                requiredCredits = unlockCost,
                                currentBalance = resolvedBalance,
                            ),
                        )
                    }
                }

            _uiState.update { current ->
                current.copy(isUnlockingWithPoints = false)
            }
        }
    }

    fun unlockWithToken(promptId: String) {
        val prompt = _uiState.value.prompt ?: return
        if (!prompt.supportsTokenUnlock()) {
            _uiState.update { it.copy(message = "Token unlock is not available for this prompt.") }
            return
        }
        launchUnlock("token") {
            promptsRepository.unlockPromptWithToken(promptId)
        }
    }

    fun unlockWithAd(promptId: String, adRewardId: String) {
        val prompt = _uiState.value.prompt ?: return
        if (!prompt.supportsRewardedUnlock()) {
            _uiState.update { it.copy(message = "Ad unlock is not available for this prompt.") }
            return
        }
        launchUnlock("ad") {
            promptsRepository.unlockPromptWithAd(promptId, adRewardId)
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun onFavoriteClicked(promptId: String) {
        if (_uiState.value.prompt?.id != promptId) return

        viewModelScope.launch {
            runCatching {
                engagementRepository.handleBookmark(
                    promptId = promptId,
                    currentFavorites = _uiState.value.favoritesCount,
                )
            }.onSuccess { result ->
                _uiState.update { current ->
                    current.copy(
                        isFavoriteLocal = result.isBookmarked,
                        favoritesCount = result.newFavorites,
                        message = if (result.isBookmarked) "Added to Favorites" else "Removed from favorites",
                    )
                }
            }.onFailure { throwable ->
                _uiState.update { current ->
                    current.copy(message = throwable.message ?: "Failed to update favorite")
                }
            }
        }
    }

    fun onLikeClicked(promptId: String) {
        if (_uiState.value.prompt?.id != promptId) return

        viewModelScope.launch {
            runCatching {
                engagementRepository.handleLike(
                    promptId = promptId,
                    currentLikes = _uiState.value.likesCount,
                )
            }.onSuccess { result ->
                _uiState.update { current ->
                    current.copy(
                        isLikedLocal = result.isLiked,
                        likesCount = result.newLikes,
                    )
                }
            }.onFailure { throwable ->
                _uiState.update { current ->
                    current.copy(message = throwable.message ?: "Failed to update like")
                }
            }
        }
    }

    fun onSimilarPromptClicked() {
        similarPromptClickCount += 1
    }

    fun shouldShowInterstitial(): Boolean {
        return similarPromptClickCount > 0 && similarPromptClickCount % interstitialInterval == 0
    }

    fun setShowAdLoader(show: Boolean) {
        _uiState.update { it.copy(showAdLoader = show) }
    }

    fun setMessage(message: String) {
        _uiState.update { it.copy(message = message) }
    }

    fun trackPromptUsage(
        promptId: String,
        action: EngagementRepository.PromptUsageAction = EngagementRepository.PromptUsageAction.COPY,
    ) {
        viewModelScope.launch {
            engagementRepository.trackPromptUsage(
                promptId = promptId,
                action = action,
            ).onSuccess { result ->
                _uiState.update { current ->
                    current.copy(
                        prompt = current.prompt?.takeIf { it.id == promptId }?.copy(copies = result.copies)
                            ?: current.prompt
                    )
                }
            }.onFailure { throwable ->
                Log.w(
                    "PromptDetailV2VM",
                    "Copy tracking failed for promptId=$promptId action=${action.wireValue}: ${throwable.message}"
                )
            }
        }
    }

    private fun resolveCreditUnlockCost(prompt: V2PromptDto): Int {
        val unlockOptions = prompt.toPromptAccessState().unlockOptions
        return (unlockOptions.creditCost ?: prompt.premiumUnlockCostPoints).coerceAtLeast(0)
    }

    private suspend fun ensurePointsBalanceLoaded(forceRefresh: Boolean): Int? {
        if (authState.value != PromptDetailAuthState.LoggedIn) {
            return null
        }

        val currentBalance = _uiState.value.pointsBalance
        if (!forceRefresh && currentBalance != null) {
            return currentBalance
        }

        var fallbackBalance: Int? = currentBalance
        if (!forceRefresh && fallbackBalance == null) {
            rewardsRepository.getCachedHub()?.let { cached ->
                fallbackBalance = cached.pointsBalance
                _uiState.update { state ->
                    state.copy(
                        pointsBalance = cached.pointsBalance,
                        isPointsBalanceLoading = false,
                    )
                }
            }
            if (fallbackBalance != null) {
                return fallbackBalance
            }
        }

        _uiState.update { state ->
            state.copy(isPointsBalanceLoading = true)
        }

        val refreshedBalance = rewardsRepository.refreshHub()
            .onSuccess { hub ->
                _uiState.update { state ->
                    state.copy(
                        pointsBalance = hub.pointsBalance,
                        isPointsBalanceLoading = false,
                    )
                }
            }
            .onFailure {
                _uiState.update { state ->
                    state.copy(isPointsBalanceLoading = false)
                }
            }
            .getOrNull()
            ?.pointsBalance

        return refreshedBalance ?: fallbackBalance
    }

    private fun syncPointsBalance(forceRefresh: Boolean, reportFailureToUser: Boolean) {
        viewModelScope.launch {
            if (!forceRefresh) {
                rewardsRepository.getCachedHub()?.let { cached ->
                    _uiState.update { state ->
                        state.copy(pointsBalance = cached.pointsBalance)
                    }
                }
            }

            _uiState.update { state ->
                state.copy(isPointsBalanceLoading = forceRefresh || state.pointsBalance == null)
            }

            rewardsRepository.refreshHub()
                .onSuccess { hub ->
                    _uiState.update { state ->
                        state.copy(
                            pointsBalance = hub.pointsBalance,
                            isPointsBalanceLoading = false,
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update { state ->
                        state.copy(isPointsBalanceLoading = false)
                    }
                    if (reportFailureToUser) {
                        _uiState.update { state ->
                            state.copy(message = throwable.message ?: "Unable to refresh your credits right now.")
                        }
                    }
                }
        }
    }

    private fun buildInsufficientCreditsMessage(requiredCredits: Int, currentBalance: Int): String {
        return "You need $requiredCredits credits, but your balance is only $currentBalance. Visit Rewards to earn more."
    }

    private fun mapPointsUnlockError(
        throwable: Throwable,
        requiredCredits: Int,
        currentBalance: Int,
    ): String {
        val raw = throwable.message.orEmpty()
        if (throwable is IOException) {
            return "Network error while unlocking prompt. Please try again."
        }
        if (throwable is V2ApiException) {
            return when {
                throwable.code == 401 || throwable.code == 403 -> "Session expired. Please login again."
                throwable.code == 404 || raw.contains("not eligible", ignoreCase = true) ->
                    "This prompt cannot be unlocked with credits."
                throwable.code == 402 || raw.contains("insufficient", ignoreCase = true) -> {
                    if (raw.contains("you need", ignoreCase = true) && raw.contains("credits", ignoreCase = true)) {
                        raw
                    } else {
                        buildInsufficientCreditsMessage(requiredCredits, currentBalance)
                    }
                }
                throwable.code in 500..599 -> "Server error while unlocking. Please try again."
                else -> raw.ifBlank { "Unable to unlock prompt right now." }
            }
        }
        return raw.ifBlank { "Unable to unlock prompt right now." }
    }

    private fun launchUnlock(
        channel: String,
        block: suspend () -> Result<*>,
    ) {
        _uiState.update { current ->
            when (channel) {
                "points" -> current.copy(isUnlockingWithPoints = true, message = null, requiresLogin = false)
                "token" -> current.copy(isUnlockingWithToken = true, message = null, requiresLogin = false)
                else -> current.copy(isUnlockingWithAd = true, message = null, requiresLogin = false)
            }
        }

        viewModelScope.launch {
            block()
                .onSuccess { result ->
                    val promptId = _uiState.value.prompt?.id ?: return@onSuccess
                    loadPrompt(promptId, forceRefresh = true)
                    _uiState.update { current ->
                        current.copy(
                            message = when {
                                channel == "ad" && result is com.picpose.bestphotographyapp.data.remote.dto.v2.UnlockResponseDto && result.duplicate ->
                                    "Reward already claimed."
                                result is com.picpose.bestphotographyapp.data.remote.dto.v2.UnlockResponseDto && result.unlocked ->
                                    "Prompt unlocked."
                                else -> "Unlock completed."
                            }
                        )
                    }
                }
                .onFailure { throwable ->
                    val message = when (throwable) {
                        is V2ApiException -> {
                            if (throwable.code == 401 || throwable.code == 403) {
                                _uiState.update { current ->
                                    current.copy(requiresLogin = true)
                                }
                                "Session expired. Please login again."
                            } else if (throwable.code == 404) {
                                "This unlock option is not available for this prompt."
                            } else {
                                throwable.message
                            }
                        }
                        else -> throwable.message ?: "Unlock failed."
                    }
                    _uiState.update { current ->
                        current.copy(message = message)
                    }
                }

            _uiState.update { current ->
                current.copy(
                    isUnlockingWithPoints = false,
                    isUnlockingWithToken = false,
                    isUnlockingWithAd = false,
                )
            }
        }
    }

    private fun loadSimilarPrompts(reset: Boolean) {
        val currentPrompt = _uiState.value.prompt ?: return
        val category = currentPrompt.category?.takeIf { it.isNotBlank() } ?: return

        if (reset) {
            similarOffset = 0
            hasMoreSimilar = true
            _uiState.update { it.copy(similarPrompts = emptyList(), isLoadingMoreSimilar = false) }
        } else if (!hasMoreSimilar) {
            return
        }

        _uiState.update { it.copy(isLoadingMoreSimilar = !reset) }

        viewModelScope.launch {
            promptsRepository.getPrompts(
                category = category,
                limit = similarLimit,
                offset = similarOffset,
            ).onSuccess { fetched ->
                val filtered = fetched
                    .filter { it.id != currentPrompt.id }
                    .distinctBy { it.id }

                similarOffset += similarLimit
                hasMoreSimilar = fetched.size >= similarLimit

                _uiState.update { current ->
                    val merged = if (reset) {
                        filtered
                    } else {
                        (current.similarPrompts + filtered).distinctBy { it.id }
                    }
                    current.copy(
                        similarPrompts = merged,
                        isLoadingMoreSimilar = false,
                        hasMoreSimilar = hasMoreSimilar,
                    )
                }
            }.onFailure {
                _uiState.update { it.copy(isLoadingMoreSimilar = false, hasMoreSimilar = false) }
            }
        }
    }
}
