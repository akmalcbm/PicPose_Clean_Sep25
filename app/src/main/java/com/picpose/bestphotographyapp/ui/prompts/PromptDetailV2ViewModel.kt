package com.picpose.bestphotographyapp.ui.prompts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.picpose.bestphotographyapp.data.datastore.UserSessionManager
import com.picpose.bestphotographyapp.data.models.v2.V2PromptDto
import com.picpose.bestphotographyapp.data.repository.V2ApiException
import com.picpose.bestphotographyapp.data.repository.V2FeatureUnavailableException
import com.picpose.bestphotographyapp.data.repository.V2PromptsRepository
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

data class PromptDetailV2UiState(
    val isLoading: Boolean = false,
    val prompt: V2PromptDto? = null,
    val message: String? = null,
    val isUnlockingWithPoints: Boolean = false,
    val isUnlockingWithToken: Boolean = false,
    val isUnlockingWithAd: Boolean = false,
)

@HiltViewModel
class PromptDetailV2ViewModel @Inject constructor(
    private val promptsRepository: V2PromptsRepository,
    userSessionManager: UserSessionManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PromptDetailV2UiState())
    val uiState: StateFlow<PromptDetailV2UiState> = _uiState.asStateFlow()

    val isLoggedIn: StateFlow<Boolean> = userSessionManager.userToken
        .map { !it.isNullOrBlank() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    fun loadPrompt(promptId: String, forceRefresh: Boolean = false) {
        if (_uiState.value.isLoading && !forceRefresh) return

        _uiState.update {
            it.copy(
                isLoading = true,
                message = null,
            )
        }

        viewModelScope.launch {
            promptsRepository.getPromptDetail(promptId)
                .onSuccess { prompt ->
                    _uiState.update { current ->
                        current.copy(
                            isLoading = false,
                            prompt = prompt,
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update { current ->
                        current.copy(
                            isLoading = false,
                            message = throwable.message ?: "Failed to load prompt.",
                        )
                    }
                }
        }
    }

    fun unlockWithPoints(promptId: String) {
        launchUnlock("points") {
            promptsRepository.unlockPromptWithPoints(promptId)
        }
    }

    fun unlockWithToken(promptId: String) {
        launchUnlock("token") {
            promptsRepository.unlockPromptWithToken(promptId)
        }
    }

    fun unlockWithAd(promptId: String, adRewardId: String) {
        launchUnlock("ad") {
            promptsRepository.unlockPromptWithAd(promptId, adRewardId)
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun setMessage(message: String) {
        _uiState.update { it.copy(message = message) }
    }

    private fun launchUnlock(
        channel: String,
        block: suspend () -> Result<*>,
    ) {
        _uiState.update { current ->
            when (channel) {
                "points" -> current.copy(isUnlockingWithPoints = true, message = null)
                "token" -> current.copy(isUnlockingWithToken = true, message = null)
                else -> current.copy(isUnlockingWithAd = true, message = null)
            }
        }

        viewModelScope.launch {
            block()
                .onSuccess { result ->
                    val promptId = _uiState.value.prompt?.id ?: return@onSuccess
                    loadPrompt(promptId, forceRefresh = true)
                    _uiState.update { current ->
                        current.copy(
                            message = when {
                                channel == "ad" && result is com.picpose.bestphotographyapp.data.models.v2.UnlockResponseDto && result.duplicate ->
                                    "Reward already claimed."
                                result is com.picpose.bestphotographyapp.data.models.v2.UnlockResponseDto && result.unlocked ->
                                    "Prompt unlocked."
                                else -> "Unlock completed."
                            }
                        )
                    }
                }
                .onFailure { throwable ->
                    val message = when (throwable) {
                        is V2FeatureUnavailableException -> throwable.message
                        is V2ApiException -> throwable.message
                        else -> throwable.message ?: "Unlock failed."
                    }
                    _uiState.update { current ->
                        current.copy(message = message)
                    }
                }

            _uiState.update { current ->
                current.copy(
                    isUnlockingWithPoints = false,
                    isUnlockingWithToken = false,
                    isUnlockingWithAd = false,
                )
            }
        }
    }
}
