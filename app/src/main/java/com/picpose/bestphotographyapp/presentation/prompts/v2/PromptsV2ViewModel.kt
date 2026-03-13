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
import com.picpose.bestphotographyapp.data.repository.V2PromptsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PromptChipFilter { All, Free, Premium, Featured }

data class PromptsV2UiState(
    val isLoading: Boolean = false,
    val prompts: List<V2PromptDto> = emptyList(),
    val selectedFilter: PromptChipFilter = PromptChipFilter.All,
    val selectedCategory: String? = null,
    val query: String = "",
    val errorMessage: String? = null,
)

@HiltViewModel
class PromptsV2ViewModel @Inject constructor(
    private val promptsRepository: V2PromptsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PromptsV2UiState())
    val uiState: StateFlow<PromptsV2UiState> = _uiState.asStateFlow()

    fun loadPrompts(
        category: String? = _uiState.value.selectedCategory,
        query: String = _uiState.value.query,
        filter: PromptChipFilter = _uiState.value.selectedFilter,
        forceRefresh: Boolean = false,
    ) {
        if (_uiState.value.isLoading && !forceRefresh) return

        _uiState.update {
            it.copy(
                isLoading = true,
                selectedCategory = category,
                query = query,
                selectedFilter = filter,
                errorMessage = null,
            )
        }

        viewModelScope.launch {
            promptsRepository.getPrompts(
                category = category,
                query = query,
                featuredOnly = filter == PromptChipFilter.Featured,
                premiumOnly = when (filter) {
                    PromptChipFilter.Free -> false
                    PromptChipFilter.Premium -> true
                    else -> null
                },
            ).onSuccess { prompts ->
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        prompts = prompts,
                    )
                }
            }.onFailure { throwable ->
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        errorMessage = throwable.message ?: "Failed to load prompts.",
                    )
                }
            }
        }
    }

    fun onFilterSelected(filter: PromptChipFilter) {
        loadPrompts(filter = filter, forceRefresh = true)
    }

    fun onQueryChanged(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
