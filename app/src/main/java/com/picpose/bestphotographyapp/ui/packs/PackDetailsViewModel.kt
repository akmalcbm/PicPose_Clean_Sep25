package com.picpose.bestphotographyapp.ui.packs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.picpose.bestphotographyapp.data.datastore.UserSessionManager
import com.picpose.bestphotographyapp.data.models.v2.PackSummaryDto
import com.picpose.bestphotographyapp.data.models.v2.V2PromptDto
import com.picpose.bestphotographyapp.data.repository.RewardsRepository
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

data class PackDetailsUiState(
    val isLoading: Boolean = false,
    val isUnlocking: Boolean = false,
    val pack: PackSummaryDto? = null,
    val items: List<V2PromptDto> = emptyList(),
    val message: String? = null,
)

@HiltViewModel
class PackDetailsViewModel @Inject constructor(
    private val rewardsRepository: RewardsRepository,
    userSessionManager: UserSessionManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PackDetailsUiState())
    val uiState: StateFlow<PackDetailsUiState> = _uiState.asStateFlow()

    val isLoggedIn: StateFlow<Boolean> = userSessionManager.userToken
        .map { !it.isNullOrBlank() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    fun loadPack(packId: Int) {
        _uiState.update { it.copy(isLoading = true, message = null) }
        viewModelScope.launch {
            rewardsRepository.getPackDetails(packId)
                .onSuccess { response ->
                    _uiState.update { current ->
                        current.copy(
                            isLoading = false,
                            pack = response.pack,
                            items = response.items,
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update { current ->
                        current.copy(
                            isLoading = false,
                            message = throwable.message ?: "Failed to load pack details.",
                        )
                    }
                }
        }
    }

    fun unlockPack(packId: Int) {
        _uiState.update { it.copy(isUnlocking = true, message = null) }
        viewModelScope.launch {
            rewardsRepository.unlockPack(packId)
                .onSuccess { response ->
                    _uiState.update { current ->
                        current.copy(
                            isUnlocking = false,
                            message = when {
                                response.unlocked == true -> "Pack unlocked."
                                else -> response.message ?: "Pack updated."
                            }
                        )
                    }
                    loadPack(packId)
                }
                .onFailure { throwable ->
                    _uiState.update { current ->
                        current.copy(
                            isUnlocking = false,
                            message = throwable.message ?: "Failed to unlock pack.",
                        )
                    }
                }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
