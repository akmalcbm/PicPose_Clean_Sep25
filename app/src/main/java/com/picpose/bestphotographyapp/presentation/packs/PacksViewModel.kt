/**
 * ---
 * File: PacksViewModel.kt
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

package com.picpose.bestphotographyapp.presentation.packs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.picpose.bestphotographyapp.data.local.datastore.UserSessionManager
import com.picpose.bestphotographyapp.data.remote.dto.v2.PackSummaryDto
import com.picpose.bestphotographyapp.data.repository.RewardsRepository
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

enum class PackFilter {
    ALL,
    OWNED,
    BEST_VALUE
}

data class PacksUiState(
    val isLoading: Boolean = false,
    val packs: List<PackSummaryDto> = emptyList(),
    val selectedFilter: PackFilter = PackFilter.ALL,
    val message: String? = null,
)

@HiltViewModel
class PacksViewModel @Inject constructor(
    private val rewardsRepository: RewardsRepository,
    userSessionManager: UserSessionManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PacksUiState())
    val uiState: StateFlow<PacksUiState> = _uiState.asStateFlow()

    val isLoggedIn: StateFlow<Boolean> = userSessionManager.userToken
        .map { !it.isNullOrBlank() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    fun loadPacks() {
        _uiState.update { it.copy(isLoading = true, message = null) }
        viewModelScope.launch {
            rewardsRepository.getPacks()
                .onSuccess { response ->
                    _uiState.update { current ->
                        current.copy(
                            isLoading = false,
                            packs = response.data,
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update { current ->
                        current.copy(
                            isLoading = false,
                            message = throwable.message ?: "Failed to load packs.",
                        )
                    }
                }
        }
    }

    fun selectFilter(filter: PackFilter) {
        _uiState.update { it.copy(selectedFilter = filter) }
    }

    fun filteredPacks(): List<PackSummaryDto> {
        val state = _uiState.value
        return when (state.selectedFilter) {
            PackFilter.ALL -> state.packs
            PackFilter.OWNED -> state.packs.filter { it.ownsPack }
            PackFilter.BEST_VALUE -> state.packs.sortedBy { pack ->
                val itemCount = if (pack.itemCount <= 0) 1 else pack.itemCount
                pack.pricePoints.toDouble() / itemCount.toDouble()
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
