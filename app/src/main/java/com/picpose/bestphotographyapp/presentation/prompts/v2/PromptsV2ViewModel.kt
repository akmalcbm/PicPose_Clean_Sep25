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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.picpose.bestphotographyapp.data.remote.dto.v2.V2PromptDto
import com.picpose.bestphotographyapp.data.repository.EngagementRepository
import com.picpose.bestphotographyapp.data.repository.V2PromptsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
) : ViewModel() {
    private val _uiState = MutableStateFlow(PromptsV2UiState())
    val uiState: StateFlow<PromptsV2UiState> = _uiState.asStateFlow()

    private var currentOffset: Int = 0

    fun initialize(initialCategory: String?) {
        val targetCategory = normalizeCategory(initialCategory)
        if (_uiState.value.prompts.isNotEmpty() && _uiState.value.selectedCategory == targetCategory) return
        loadPrompts(
            reset = true,
            forceRefresh = true,
            category = targetCategory,
            filter = _uiState.value.selectedFilter,
        )
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
}
