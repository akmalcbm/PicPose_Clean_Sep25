/**
 * ---
 * File: StatsViewModel.kt
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

package com.picpose.bestphotographyapp.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.picpose.bestphotographyapp.data.remote.dto.StatsResponse
import com.picpose.bestphotographyapp.core.network.RetrofitClient
import com.picpose.bestphotographyapp.data.repository.StatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StatsUiState(
    val stats: StatsResponse? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val repository: StatsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeCachedStats().collect { cached ->
                if (cached != null) {
                    _uiState.update { state ->
                        state.copy(stats = cached)
                    }
                }
            }
        }
    }

    fun fetchStats(apiKey: String? = null) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.getQuickStats(apiKey ?: RetrofitClient.defaultApiKey)
                .collect { result ->
                    result.onSuccess {
                        _uiState.value = _uiState.value.copy(stats = it, isLoading = false, error = null)
                    }.onFailure { e ->
                        _uiState.value = _uiState.value.copy(error = e.message, isLoading = false)
                    }
                }
        }
    }
}
