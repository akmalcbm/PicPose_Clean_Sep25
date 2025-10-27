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

/**
 * UI State for App Settings
 */
sealed class AppSettingsUiState {
    object Idle : AppSettingsUiState()
    object Loading : AppSettingsUiState()
    data class Success(val settings: AppSettings) : AppSettingsUiState()
    data class Error(val message: String) : AppSettingsUiState()
}

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
    
    // New state flow for better state management
    private val _state = mutableStateOf<AppSettingsUiState>(AppSettingsUiState.Idle)
    val state: State<AppSettingsUiState> = _state
    
    // Cache flag to prevent redundant API calls
    private var hasFetchedSettings = false

    /**
     * Load app settings from API (with caching)
     */
    fun loadAppSettings(forceRefresh: Boolean = false) {
        // Skip if already loaded and not forcing refresh
        if (hasFetchedSettings && !forceRefresh && _uiState.value != null) {
            return
        }
        
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _state.value = AppSettingsUiState.Loading

            homeRepository.getAppSettings().collect { result: Result<AppSettings> ->
                result.fold(
                    onSuccess = { data: AppSettings ->
                        _uiState.value = data
                        _state.value = AppSettingsUiState.Success(data)
                        hasFetchedSettings = true
                    },
                    onFailure = { err: Throwable ->
                        val errorMsg = err.message ?: "Something went wrong"
                        _error.value = errorMsg
                        _state.value = AppSettingsUiState.Error(errorMsg)
                    }
                )
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Get Privacy Policy text
     */
    fun getPrivacyPolicyText(): String {
        return _uiState.value?.privacyPolicy ?: ""
    }
    
    /**
     * Get Terms & Conditions text
     */
    fun getTermsText(): String {
        return _uiState.value?.termsConditions ?: ""
    }
    
    /**
     * Get About App text
     */
    fun getAboutText(): String {
        return _uiState.value?.about ?: ""
    }
}
