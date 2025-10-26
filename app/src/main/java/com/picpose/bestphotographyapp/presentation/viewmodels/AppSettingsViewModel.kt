package com.picpose.bestphotographyapp.presentation.viewmodels

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.picpose.bestphotographyapp.data.models.AppSettings
import com.picpose.bestphotographyapp.data.repository.HomeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppSettingsViewModel @Inject constructor(
    private val homeRepository: HomeRepository
) : ViewModel() {

    private val _uiState = mutableStateOf<AppSettings?>(null)
    val uiState: State<AppSettings?> = _uiState

    private val _isLoading = mutableStateOf(false)
    val isLoading: State<Boolean> = _isLoading

    private val _error = mutableStateOf<String?>(null)
    val error: State<String?> = _error

    fun loadAppSettings() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            homeRepository.getAppSettings().collect { result: Result<AppSettings> ->
                result.fold(
                    onSuccess = { data: AppSettings ->
                        _uiState.value = data
                    },
                    onFailure = { err: Throwable ->
                        _error.value = err.message ?: "Something went wrong"
                    }
                )
                _isLoading.value = false
            }
        }
    }
}
