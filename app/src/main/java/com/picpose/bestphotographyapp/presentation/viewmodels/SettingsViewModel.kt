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
 * Supports: System | Light | Dark theme
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsManager: SettingsManager
) : ViewModel() {

    // THEME MODE: "system", "light", "dark"
    val themeMode: StateFlow<String> = settingsManager.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, "system")

    // LANGUAGE
    val language: StateFlow<String> = settingsManager.language
        .stateIn(viewModelScope, SharingStarted.Eagerly, "en")

    // NOTIFICATIONS
    val notificationsEnabled: StateFlow<Boolean> = settingsManager.notificationsEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    // ----------------------------
    // UPDATE THEME MODE
    // ----------------------------
    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            settingsManager.setThemeMode(mode) // system | light | dark
        }
    }

    // ----------------------------
    // UPDATE LANGUAGE
    // ----------------------------
    fun setLanguage(languageCode: String) {
        viewModelScope.launch {
            settingsManager.setLanguage(languageCode)
        }
    }

    // ----------------------------
    // UPDATE NOTIFICATIONS
    // ----------------------------
    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setNotificationsEnabled(enabled)
        }
    }
}
