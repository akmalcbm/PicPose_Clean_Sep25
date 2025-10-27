package com.picpose.bestphotographyapp.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.picpose.bestphotographyapp.data.models.AppSettings
import com.picpose.bestphotographyapp.data.repository.HomeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI State for App Settings
 */
sealed class AppSettingsUiState {
    object Idle : AppSettingsUiState()
    object Loading : AppSettingsUiState()
    data class Success(val settings: AppSettings) : AppSettingsUiState()
    data class Error(val message: String, val cachedSettings: AppSettings? = null) : AppSettingsUiState()
}

@HiltViewModel
class AppSettingsViewModel @Inject constructor(
    private val homeRepository: HomeRepository
) : ViewModel() {

    // StateFlow for better state management
    private val _uiState = MutableStateFlow<AppSettingsUiState>(AppSettingsUiState.Idle)
    val uiState: StateFlow<AppSettingsUiState> = _uiState.asStateFlow()

    // Backward compatibility - expose current settings
    val state: StateFlow<AppSettingsUiState> = _uiState.asStateFlow()
    
    // Cache flag to prevent redundant API calls
    private var hasFetchedSettings = false
    private var cachedSettings: AppSettings? = null

    init {
        // Auto-load settings on initialization
        loadAppSettings()
    }

    /**
     * Load app settings from API (with caching)
     */
    fun loadAppSettings(forceRefresh: Boolean = false) {
        // Skip if already loaded and not forcing refresh
        if (hasFetchedSettings && !forceRefresh && cachedSettings != null) {
            _uiState.value = AppSettingsUiState.Success(cachedSettings!!)
            return
        }
        
        viewModelScope.launch {
            _uiState.value = AppSettingsUiState.Loading

            homeRepository.getAppSettings().collect { result: Result<AppSettings> ->
                result.fold(
                    onSuccess = { settings: AppSettings ->
                        cachedSettings = settings
                        _uiState.value = AppSettingsUiState.Success(settings)
                        hasFetchedSettings = true
                    },
                    onFailure = { err: Throwable ->
                        val errorMsg = err.message ?: "Failed to load app settings"
                        // If we have cached settings, show error with cache
                        _uiState.value = AppSettingsUiState.Error(errorMsg, cachedSettings)
                    }
                )
            }
        }
    }
    
    /**
     * Refresh settings from server
     */
    fun refresh() {
        loadAppSettings(forceRefresh = true)
    }
    
    /**
     * Get Privacy Policy HTML
     */
    fun getPrivacyPolicyHtml(): String {
        return cachedSettings?.policies?.privacyPolicyHtml ?: ""
    }
    
    /**
     * Get Terms & Conditions HTML
     */
    fun getTermsHtml(): String {
        return cachedSettings?.policies?.termsConditionsHtml ?: ""
    }
    
    /**
     * Get About HTML
     */
    fun getAboutHtml(): String {
        return cachedSettings?.about?.html ?: ""
    }
    
    /**
     * Get About text (plain text version)
     */
    fun getAboutText(): String {
        return cachedSettings?.about?.text ?: ""
    }
}
