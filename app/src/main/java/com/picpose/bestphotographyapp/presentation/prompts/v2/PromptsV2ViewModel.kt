/**
 * ---
 * File: PromptsV2ViewModel.kt
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
import com.picpose.bestphotographyapp.domain.model.isPackOnlyPrompt
import com.picpose.bestphotographyapp.domain.model.supportsCreditsUnlock
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PromptChipFilter { All, Free, Premium, Featured }

data class PromptEngagementUi(
    val viewsCount: Int = 0,
    val likesCount: Int = 0,
    val favoritesCount: Int = 0,
    val isLiked: Boolean = false,
    val isFavorited: Boolean = false,
)

data class PromptsV2UiState(
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val prompts: List<V2PromptDto> = emptyList(),
    val pointsBalance: Int? = null,
    val unlockingPromptIds: Set<String> = emptySet(),
    val engagementByPromptId: Map<String, PromptEngagementUi> = emptyMap(),
    val categories: List<String> = listOf(CATEGORY_ALL),
    val selectedFilter: PromptChipFilter = PromptChipFilter.All,
    val selectedCategory: String = CATEGORY_ALL,
    val query: String = "",
    val totalPrompts: Int = 0,
    val errorMessage: String? = null,
)

private const val PAGE_SIZE = 40
private const val CATEGORY_ALL = "All"

@HiltViewModel
class PromptsV2ViewModel @Inject constructor(
    private val promptsRepository: V2PromptsRepository,
    private val engagementRepository: EngagementRepository,
    private val rewardsRepository: RewardsRepository,
    userSessionManager: UserSessionManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PromptsV2UiState())
    val uiState: StateFlow<PromptsV2UiState> = _uiState.asStateFlow()

    val isLoggedIn: StateFlow<Boolean> = userSessionManager.authenticatedSession
        .map { it != null }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    private var currentOffset: Int = 0

    init {
        viewModelScope.launch {
            isLoggedIn.collect { loggedIn ->
                if (!loggedIn) {
                    _uiState.update { it.copy(pointsBalance = null, unlockingPromptIds = emptySet()) }
                } else {
                    syncPointsBalance(forceRefresh = true)
                }
            }
        }
    }

    fun initialize(initialCategory: String?) {
        val targetCategory = normalizeCategory(initialCategory)
        if (_uiState.value.prompts.isNotEmpty() && _uiState.value.selectedCategory == targetCategory) return
        loadPrompts(
            reset = true,
            forceRefresh = true,
            category = targetCategory,
            filter = _uiState.value.selectedFilter,
        )
        if (isLoggedIn.value) {
            syncPointsBalance(forceRefresh = false)
        }
    }

    fun refresh() {
        loadPrompts(
            reset = true,
            forceRefresh = true,
            category = _uiState.value.selectedCategory,
            filter = _uiState.value.selectedFilter,
        )
    }

    fun loadNextPage() {
        if (_uiState.value.query.isNotBlank()) return
        loadPrompts(
            reset = false,
            forceRefresh = false,
            category = _uiState.value.selectedCategory,
            filter = _uiState.value.selectedFilter,
        )
    }

    fun onCategorySelected(category: String) {
        val normalized = normalizeCategory(category)
        if (normalized == _uiState.value.selectedCategory) return
        loadPrompts(
            reset = true,
            forceRefresh = true,
            category = normalized,
            filter = _uiState.value.selectedFilter,
        )
    }

    fun onFilterSelected(filter: PromptChipFilter) {
        if (filter == _uiState.value.selectedFilter) return
        loadPrompts(
            reset = true,
            forceRefresh = true,
            category = _uiState.value.selectedCategory,
            filter = filter,
        )
    }

    fun onQueryChanged(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    fun clearFiltersAndReload() {
        _uiState.update {
            it.copy(
                query = "",
                selectedCategory = CATEGORY_ALL,
                selectedFilter = PromptChipFilter.All,
            )
        }
        loadPrompts(
            reset = true,
            forceRefresh = true,
            category = CATEGORY_ALL,
            filter = PromptChipFilter.All,
        )
    }

    fun onLikeClicked(promptId: String) {
        val snapshot = _uiState.value
        val baseCounts = snapshot.engagementByPromptId[promptId]
            ?: snapshot.prompts.firstOrNull { it.id == promptId }?.let { prompt ->
                PromptEngagementUi(
                    viewsCount = prompt.views.coerceAtLeast(0),
                    likesCount = prompt.likes.coerceAtLeast(0),
                    favoritesCount = prompt.favorites.coerceAtLeast(0),
                )
            }
            ?: return

        viewModelScope.launch {
            runCatching {
                engagementRepository.handleLike(promptId = promptId, currentLikes = baseCounts.likesCount)
            }.onSuccess { result ->
                _uiState.update { state ->
                    val updated = state.engagementByPromptId.toMutableMap()
                    val current = updated[promptId] ?: baseCounts
                    updated[promptId] = current.copy(
                        isLiked = result.isLiked,
                        likesCount = result.newLikes.coerceAtLeast(0),
                    )
                    state.copy(engagementByPromptId = updated)
                }
            }.onFailure { throwable ->
                _uiState.update { state ->
                    state.copy(errorMessage = throwable.message ?: "Failed to update like")
                }
            }
        }
    }

    fun onFavoriteClicked(promptId: String) {
        val snapshot = _uiState.value
        val baseCounts = snapshot.engagementByPromptId[promptId]
            ?: snapshot.prompts.firstOrNull { it.id == promptId }?.let { prompt ->
                PromptEngagementUi(
                    viewsCount = prompt.views.coerceAtLeast(0),
                    likesCount = prompt.likes.coerceAtLeast(0),
                    favoritesCount = prompt.favorites.coerceAtLeast(0),
                )
            }
            ?: return

        viewModelScope.launch {
            runCatching {
                engagementRepository.handleBookmark(promptId = promptId, currentFavorites = baseCounts.favoritesCount)
            }.onSuccess { result ->
                _uiState.update { state ->
                    val updated = state.engagementByPromptId.toMutableMap()
                    val current = updated[promptId] ?: baseCounts
                    updated[promptId] = current.copy(
                        isFavorited = result.isBookmarked,
                        favoritesCount = result.newFavorites.coerceAtLeast(0),
                    )
                    state.copy(engagementByPromptId = updated)
                }
            }.onFailure { throwable ->
                _uiState.update { state ->
                    state.copy(errorMessage = throwable.message ?: "Failed to update favorite")
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun trackPromptUsage(
        promptId: String,
        action: EngagementRepository.PromptUsageAction = EngagementRepository.PromptUsageAction.COPY
    ) {
        viewModelScope.launch {
            engagementRepository.trackPromptUsage(promptId = promptId, action = action)
                .onFailure { throwable ->
                    Log.w("PromptsV2ViewModel", "Copy tracking failed for promptId=$promptId action=${action.wireValue}: ${throwable.message}")
                }
        }
    }

    fun unlockPromptWithPoints(promptId: String) {
        val snapshot = _uiState.value
        val prompt = snapshot.prompts.firstOrNull { it.id == promptId } ?: return
        if (!prompt.isLocked) {
            _uiState.update { it.copy(errorMessage = "This prompt is already unlocked.") }
            return
        }
        if (!prompt.supportsCreditsUnlock()) {
            val message = if (prompt.isPackOnlyPrompt()) {
                "This prompt is available via Premium Pack."
            } else {
                "Credits unlock is not available for this prompt."
            }
            _uiState.update { it.copy(errorMessage = message) }
            return
        }
        if (!isLoggedIn.value) {
            _uiState.update { it.copy(errorMessage = "Please login to unlock premium prompts.") }
            return
        }
        if (promptId in snapshot.unlockingPromptIds) return

        val cost = prompt.premiumUnlockCostPoints.coerceAtLeast(0)
        val balance = snapshot.pointsBalance
        if (balance != null && cost > balance) {
            _uiState.update { it.copy(errorMessage = "Not enough credits to unlock this prompt.") }
            return
        }

        _uiState.update { it.copy(unlockingPromptIds = it.unlockingPromptIds + promptId) }

        viewModelScope.launch {
            promptsRepository.unlockPromptWithPoints(promptId)
                .onSuccess { response ->
                    val successMessage = when {
                        response.duplicate -> "You already unlocked this prompt."
                        response.unlocked -> "Prompt unlocked successfully."
                        else -> response.message ?: "Unlock completed."
                    }
                    _uiState.update { current ->
                        val updatedPrompts = current.prompts.map { item ->
                            if (item.id == promptId) item.copy(isLocked = false) else item
                        }
                        current.copy(
                            prompts = updatedPrompts,
                            pointsBalance = response.pointsBalance ?: current.pointsBalance?.let { currentBalance ->
                                val fallbackCost = response.cost ?: cost
                                (currentBalance - fallbackCost).coerceAtLeast(0)
                            },
                            unlockingPromptIds = current.unlockingPromptIds - promptId,
                            errorMessage = successMessage,
                        )
                    }

                    syncPointsBalance(forceRefresh = true)
                    refreshCurrentFilterAfterUnlock()
                }
                .onFailure { throwable ->
                    _uiState.update { current ->
                        current.copy(
                            unlockingPromptIds = current.unlockingPromptIds - promptId,
                            errorMessage = mapUnlockError(throwable),
                        )
                    }
                }
        }
    }

    fun loadPrompts(
        reset: Boolean,
        forceRefresh: Boolean,
        category: String,
        filter: PromptChipFilter,
    ) {
        val current = _uiState.value
        if ((current.isLoading || current.isLoadingMore) && !forceRefresh) return
        if (!reset && !current.hasMore) return

        val normalizedCategory = normalizeCategory(category)
        val requestOffset = if (reset) 0 else currentOffset

        _uiState.update { state ->
            state.copy(
                isLoading = reset,
                isLoadingMore = !reset,
                selectedCategory = normalizedCategory,
                selectedFilter = filter,
                errorMessage = null,
                hasMore = if (reset) false else state.hasMore,
                prompts = if (reset) emptyList() else state.prompts,
                engagementByPromptId = if (reset) emptyMap() else state.engagementByPromptId,
            )
        }

        viewModelScope.launch {
            promptsRepository.getPromptsPage(
                category = normalizedCategory.takeIf { it != CATEGORY_ALL },
                query = null,
                featuredOnly = filter == PromptChipFilter.Featured,
                premiumOnly = when (filter) {
                    PromptChipFilter.Free -> false
                    PromptChipFilter.Premium -> true
                    else -> null
                },
                limit = PAGE_SIZE,
                offset = requestOffset,
            ).onSuccess { page ->
                val previousItems = if (reset) emptyList() else _uiState.value.prompts
                val mergedItems = if (reset) {
                    page.items.distinctBy { it.id }
                } else {
                    (previousItems + page.items).distinctBy { it.id }
                }
                val appendedCount = mergedItems.size - previousItems.size

                currentOffset = if (reset) {
                    page.items.size
                } else {
                    requestOffset + page.items.size
                }

                val hasMore = when {
                    appendedCount <= 0 -> false
                    page.hasMore -> true
                    else -> page.items.size >= PAGE_SIZE
                }

                val engagementMap = buildEngagementMap(mergedItems)
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        hasMore = hasMore,
                        prompts = mergedItems,
                        engagementByPromptId = engagementMap,
                        totalPrompts = page.total ?: mergedItems.size,
                        categories = buildCategories(
                            prompts = mergedItems,
                            selectedCategory = normalizedCategory,
                        ),
                    )
                }
            }.onFailure { throwable ->
                if (reset) {
                    currentOffset = 0
                }
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        hasMore = if (reset) false else state.hasMore,
                        errorMessage = throwable.message ?: "Failed to load prompts.",
                        categories = buildCategories(
                            prompts = state.prompts,
                            selectedCategory = state.selectedCategory,
                        ),
                    )
                }
            }
        }
    }

    private suspend fun buildEngagementMap(prompts: List<V2PromptDto>): Map<String, PromptEngagementUi> {
        val localByPromptId = engagementRepository.getAllStates().associateBy { it.promptId }
        return prompts.associate { prompt ->
            val local = localByPromptId[prompt.id]
            prompt.id to PromptEngagementUi(
                viewsCount = prompt.views.coerceAtLeast(0),
                likesCount = (prompt.likes.coerceAtLeast(0) + if (local?.isLiked == true) 1 else 0).coerceAtLeast(0),
                favoritesCount = (prompt.favorites.coerceAtLeast(0) + if (local?.isFavorited == true) 1 else 0).coerceAtLeast(0),
                isLiked = local?.isLiked == true,
                isFavorited = local?.isFavorited == true,
            )
        }
    }

    private fun buildCategories(
        prompts: List<V2PromptDto>,
        selectedCategory: String,
    ): List<String> {
        val ordered = linkedMapOf<String, String>()
        ordered[CATEGORY_ALL.lowercase()] = CATEGORY_ALL

        prompts.mapNotNull { it.category?.trim()?.takeIf(String::isNotBlank) }
            .forEach { category ->
                ordered.putIfAbsent(category.lowercase(), category)
            }

        if (!selectedCategory.equals(CATEGORY_ALL, ignoreCase = true)) {
            ordered.putIfAbsent(selectedCategory.lowercase(), selectedCategory)
        }

        return ordered.values.toList()
    }

    private fun normalizeCategory(raw: String?): String {
        val normalized = raw?.trim().orEmpty()
        return if (normalized.isBlank()) CATEGORY_ALL else normalized
    }

    private fun refreshCurrentFilterAfterUnlock() {
        val snapshot = _uiState.value
        loadPrompts(
            reset = true,
            forceRefresh = true,
            category = snapshot.selectedCategory,
            filter = snapshot.selectedFilter,
        )
    }

    private fun syncPointsBalance(forceRefresh: Boolean) {
        viewModelScope.launch {
            if (!isLoggedIn.value) return@launch

            if (!forceRefresh) {
                rewardsRepository.getCachedHub()?.let { hub ->
                    _uiState.update { it.copy(pointsBalance = hub.pointsBalance) }
                }
            }

            rewardsRepository.refreshHub()
                .onSuccess { hub ->
                    _uiState.update { it.copy(pointsBalance = hub.pointsBalance) }
                }
        }
    }

    private fun mapUnlockError(throwable: Throwable): String {
        val raw = throwable.message.orEmpty()
        if (throwable is IOException) {
            return "Network error while unlocking prompt. Please try again."
        }
        if (throwable is V2ApiException) {
            return when {
                throwable.code == 401 || throwable.code == 403 -> "Session expired. Please log in again."
                throwable.code == 402 || raw.contains("insufficient", ignoreCase = true) ->
                    raw.ifBlank { "Not enough credits to unlock this prompt." }
                raw.contains("already unlocked", ignoreCase = true) -> "You already unlocked this prompt."
                throwable.code in 500..599 -> "Server error while unlocking. Please try again."
                else -> raw.ifBlank { "Unable to unlock prompt right now." }
            }
        }
        return raw.ifBlank { "Unable to unlock prompt right now." }
    }
}
