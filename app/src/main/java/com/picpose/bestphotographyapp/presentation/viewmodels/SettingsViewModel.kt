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

    //SKIP GEMINI DIALOG
    val skipGeminiDialog = settingsManager.skipGeminiDialog

    // NOTIFICATION PERMISSION (OS)
    val notificationPermissionRequested: StateFlow<Boolean> =
        settingsManager.notificationPermissionRequested
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val notificationPermissionDeniedAtOpen: StateFlow<Int> =
        settingsManager.notificationPermissionDeniedAtOpen
            .stateIn(viewModelScope, SharingStarted.Eagerly, -1)

    val notificationPermissionLastPromptOpen: StateFlow<Int> =
        settingsManager.notificationPermissionLastPromptOpen
            .stateIn(viewModelScope, SharingStarted.Eagerly, -1)

    val appOpenCount: StateFlow<Int> =
        settingsManager.appOpenCount
            .stateIn(viewModelScope, SharingStarted.Eagerly, 0)


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

    // ----------------------------
    // UPDATE Gemini Dialog Box
    // ----------------------------
    fun resetGeminiDialog() {
        viewModelScope.launch {
            settingsManager.setSkipGeminiDialog(false)
        }
    }

    // ----------------------------
    // NOTIFICATION PERMISSION TRACKING
    // ----------------------------
    fun incrementAppOpenCount() {
        viewModelScope.launch {
            settingsManager.incrementAppOpenCount()
        }
    }

    fun setNotificationPermissionRequested(requested: Boolean) {
        viewModelScope.launch {
            settingsManager.setNotificationPermissionRequested(requested)
        }
    }

    fun setNotificationPermissionDeniedAtOpen(openCount: Int) {
        viewModelScope.launch {
            settingsManager.setNotificationPermissionDeniedAtOpen(openCount)
        }
    }

    fun setNotificationPermissionLastPromptOpen(openCount: Int) {
        viewModelScope.launch {
            settingsManager.setNotificationPermissionLastPromptOpen(openCount)
        }
    }



}
