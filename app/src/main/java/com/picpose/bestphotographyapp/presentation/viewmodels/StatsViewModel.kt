package com.picpose.bestphotographyapp.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.picpose.bestphotographyapp.data.models.StatsResponse
import com.picpose.bestphotographyapp.data.network.RetrofitClient
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

    fun fetchStats(apiKey: String? = null) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            repository.getQuickStats(apiKey ?: RetrofitClient.defaultApiKey)
                .collect { result ->
                    result.onSuccess {
                        _uiState.value = StatsUiState(stats = it, isLoading = false)
                    }.onFailure { e ->
                        _uiState.value = StatsUiState(error = e.message, isLoading = false)
                    }
                }
        }
    }
}
