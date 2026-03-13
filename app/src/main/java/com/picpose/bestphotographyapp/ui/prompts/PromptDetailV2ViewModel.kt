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

package com.picpose.bestphotographyapp.ui.prompts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.picpose.bestphotographyapp.data.datastore.UserSessionManager
import com.picpose.bestphotographyapp.data.models.v2.V2PromptDto
import com.picpose.bestphotographyapp.data.repository.EngagementRepository
import com.picpose.bestphotographyapp.data.repository.V2ApiException
import com.picpose.bestphotographyapp.data.repository.V2FeatureUnavailableException
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
    val isUnlockingWithPoints: Boolean = false,
    val isUnlockingWithToken: Boolean = false,
    val isUnlockingWithAd: Boolean = false,
)

@HiltViewModel
class PromptDetailV2ViewModel @Inject constructor(
    private val promptsRepository: V2PromptsRepository,
    private val engagementRepository: EngagementRepository,
    userSessionManager: UserSessionManager,
) : ViewModel() {
    private var similarOffset: Int = 0
    private val similarLimit: Int = 10
    private var hasMoreSimilar: Boolean = true
    private var similarPromptClickCount: Int = 0
    private val interstitialInterval: Int = 3

    private val _uiState = MutableStateFlow(PromptDetailV2UiState())
    val uiState: StateFlow<PromptDetailV2UiState> = _uiState.asStateFlow()

    val isLoggedIn: StateFlow<Boolean> = userSessionManager.userToken
        .map { !it.isNullOrBlank() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

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
                    loadSimilarPrompts(reset = true)
                }
                .onFailure { throwable ->
                    _uiState.update { current ->
                        current.copy(
                            isLoading = false,
                            message = throwable.message ?: "Failed to load prompt.",
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
        launchUnlock("points") {
            promptsRepository.unlockPromptWithPoints(promptId)
        }
    }

    fun unlockWithToken(promptId: String) {
        launchUnlock("token") {
            promptsRepository.unlockPromptWithToken(promptId)
        }
    }

    fun unlockWithAd(promptId: String, adRewardId: String) {
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

    private fun launchUnlock(
        channel: String,
        block: suspend () -> Result<*>,
    ) {
        _uiState.update { current ->
            when (channel) {
                "points" -> current.copy(isUnlockingWithPoints = true, message = null)
                "token" -> current.copy(isUnlockingWithToken = true, message = null)
                else -> current.copy(isUnlockingWithAd = true, message = null)
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
                                channel == "ad" && result is com.picpose.bestphotographyapp.data.models.v2.UnlockResponseDto && result.duplicate ->
                                    "Reward already claimed."
                                result is com.picpose.bestphotographyapp.data.models.v2.UnlockResponseDto && result.unlocked ->
                                    "Prompt unlocked."
                                else -> "Unlock completed."
                            }
                        )
                    }
                }
                .onFailure { throwable ->
                    val message = when (throwable) {
                        is V2FeatureUnavailableException -> throwable.message
                        is V2ApiException -> throwable.message
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
