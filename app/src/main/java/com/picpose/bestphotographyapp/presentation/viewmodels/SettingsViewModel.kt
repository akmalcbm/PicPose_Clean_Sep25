package com.picpose.bestphotographyapp.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.picpose.bestphotographyapp.data.datastore.SettingsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for app settings
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsManager: SettingsManager
) : ViewModel() {

    val isDarkMode: StateFlow<Boolean> = settingsManager.isDarkMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val language: StateFlow<String> = settingsManager.language
        .stateIn(viewModelScope, SharingStarted.Eagerly, "en")

    val notificationsEnabled: StateFlow<Boolean> = settingsManager.notificationsEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /**
     * Toggle dark mode
     */
    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setDarkMode(enabled)
        }
    }

    /**
     * Set language
     */
    fun setLanguage(languageCode: String) {
        viewModelScope.launch {
            settingsManager.setLanguage(languageCode)
        }
    }

    /**
     * Toggle notifications
     */
    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setNotificationsEnabled(enabled)
        }
    }
}
