/**
 * ---
 * File: SettingsViewModel.kt
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

package com.picpose.bestphotographyapp.presentation.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.picpose.bestphotographyapp.R
import com.picpose.bestphotographyapp.data.local.datastore.UserSessionManager
import com.picpose.bestphotographyapp.data.local.datastore.SettingsManager
import com.picpose.bestphotographyapp.data.local.datastore.ThemeMode
import com.picpose.bestphotographyapp.core.locale.AppLocaleManager
import com.picpose.bestphotographyapp.core.notifications.NotificationSettingsCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for app settings
 * Supports: System | Light | Dark theme
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsManager: SettingsManager,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    // THEME MODE: SYSTEM | LIGHT | DARK
    val themeMode: StateFlow<ThemeMode> = settingsManager.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    // LANGUAGE
    val language: StateFlow<String> = settingsManager.language
        .stateIn(viewModelScope, SharingStarted.Eagerly, "en")

    // NOTIFICATIONS
    val notificationsEnabled: StateFlow<Boolean> = settingsManager.notificationsEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    //SKIP GEMINI DIALOG
    val skipGeminiDialog = settingsManager.skipGeminiDialog

    private val _notificationsToggleInProgress = MutableStateFlow(false)
    val notificationsToggleInProgress = _notificationsToggleInProgress.asStateFlow()

    private val _showSystemNotificationSettingsDialog = MutableStateFlow(false)
    val showSystemNotificationSettingsDialog = _showSystemNotificationSettingsDialog.asStateFlow()

    private val _showGeminiConfirmDialog = MutableStateFlow(false)
    val showGeminiConfirmDialog = _showGeminiConfirmDialog.asStateFlow()

    private val _pendingGeminiAction = MutableStateFlow<GeminiConfirmationAction?>(null)
    val pendingGeminiAction = _pendingGeminiAction.asStateFlow()

    private val _events = MutableSharedFlow<SettingsEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

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
    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            settingsManager.setThemeMode(mode)
        }
    }

    // ----------------------------
    // UPDATE LANGUAGE
    // ----------------------------
    fun setLanguage(languageCode: String) {
        val normalized = languageCode.ifBlank { "system" }
        viewModelScope.launch {
            if (language.value != normalized) {
                settingsManager.setLanguage(normalized)
            }

            val currentTags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
            val targetTags = AppLocaleManager.resolveLanguageTags(normalized)
            if (currentTags != targetTags) {
                AppLocaleManager.applyLanguage(normalized)
            }
        }
    }

    // ----------------------------
    // UPDATE NOTIFICATIONS
    // ----------------------------
    fun onNotificationsToggleRequested(enabled: Boolean) {
        if (_notificationsToggleInProgress.value) return

        viewModelScope.launch {
            if (enabled) {
                if (!isSystemNotificationsEnabled()) {
                    settingsManager.setNotificationsEnabled(false)
                    _showSystemNotificationSettingsDialog.value = true
                    return@launch
                }

                _notificationsToggleInProgress.value = true
                settingsManager.setNotificationsEnabled(true)
                val userId = UserSessionManager(appContext).userId.firstOrNull()?.toIntOrNull()

                NotificationSettingsCoordinator.enableNotifications(appContext, userId)
                    .onSuccess {
                        _events.tryEmit(SettingsEvent.ShowMessage(R.string.notifications_enabled_feedback))
                    }
                    .onFailure {
                        settingsManager.setNotificationsEnabled(false)
                        _events.tryEmit(SettingsEvent.ShowMessage(R.string.notifications_enable_failed))
                    }

                _notificationsToggleInProgress.value = false
            } else {
                _notificationsToggleInProgress.value = true
                settingsManager.setNotificationsEnabled(false)
                NotificationSettingsCoordinator.disableNotifications()
                    .onSuccess {
                        _events.tryEmit(SettingsEvent.ShowMessage(R.string.notifications_disabled_feedback))
                    }
                    .onFailure {
                        _events.tryEmit(SettingsEvent.ShowMessage(R.string.notifications_disable_failed))
                    }
                _notificationsToggleInProgress.value = false
            }
        }
    }

    // ----------------------------
    // UPDATE Gemini Dialog Box
    // ----------------------------
    fun onGeminiConfirmationToggleRequested(enableConfirmation: Boolean) {
        _pendingGeminiAction.value = if (enableConfirmation) {
            GeminiConfirmationAction.ENABLE
        } else {
            GeminiConfirmationAction.DISABLE
        }
        _showGeminiConfirmDialog.value = true
    }

    fun confirmGeminiAction() {
        val action = _pendingGeminiAction.value ?: return
        viewModelScope.launch {
            when (action) {
                GeminiConfirmationAction.ENABLE -> settingsManager.setSkipGeminiDialog(false)
                GeminiConfirmationAction.DISABLE -> settingsManager.setSkipGeminiDialog(true)
            }
            clearGeminiDialogState()
        }
    }

    fun dismissGeminiActionDialog() {
        clearGeminiDialogState()
    }

    fun dismissSystemNotificationDialog() {
        _showSystemNotificationSettingsDialog.value = false
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

    private fun isSystemNotificationsEnabled(): Boolean {
        val runtimePermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        return runtimePermissionGranted && NotificationManagerCompat.from(appContext).areNotificationsEnabled()
    }

    private fun clearGeminiDialogState() {
        _showGeminiConfirmDialog.value = false
        _pendingGeminiAction.value = null
    }

    enum class GeminiConfirmationAction {
        ENABLE,
        DISABLE
    }

    sealed class SettingsEvent {
        data class ShowMessage(val messageRes: Int) : SettingsEvent()
    }


}
