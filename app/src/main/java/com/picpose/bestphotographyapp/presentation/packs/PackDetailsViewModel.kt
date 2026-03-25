/**
 * ---
 * File: PackDetailsViewModel.kt
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

package com.picpose.bestphotographyapp.presentation.packs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.picpose.bestphotographyapp.data.local.datastore.UserSessionManager
import com.picpose.bestphotographyapp.data.remote.dto.v2.PackSummaryDto
import com.picpose.bestphotographyapp.data.remote.dto.v2.V2PromptDto
import com.picpose.bestphotographyapp.data.repository.RewardsRepository
import com.picpose.bestphotographyapp.data.repository.V2ApiException
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
    val pointsBalance: Int? = null,
    val unlockDialogError: String? = null,
    val unlockDialogInsufficientCredits: Boolean = false,
    val message: String? = null,
)

enum class PackUnlockSource {
    HeaderCard,
    LockedPromptSheet,
}

@HiltViewModel
class PackDetailsViewModel @Inject constructor(
    private val rewardsRepository: RewardsRepository,
    userSessionManager: UserSessionManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PackDetailsUiState())
    val uiState: StateFlow<PackDetailsUiState> = _uiState.asStateFlow()

    val isLoggedIn: StateFlow<Boolean> = userSessionManager.authenticatedSession
        .map { it != null }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    fun loadPack(packId: Int) {
        _uiState.update {
            it.copy(
                isLoading = true,
                message = null,
                unlockDialogError = null,
                unlockDialogInsufficientCredits = false,
            )
        }
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
                    refreshPointsBalance()
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

    fun unlockPack(
        packId: Int,
        source: PackUnlockSource = PackUnlockSource.HeaderCard,
    ) {
        if (_uiState.value.isUnlocking) return

        val snapshot = _uiState.value
        val requiredCredits = snapshot.pack?.pricePoints?.coerceAtLeast(0) ?: 0
        val balance = snapshot.pointsBalance
        if (requiredCredits > 0 && balance != null && balance < requiredCredits) {
            handleInsufficientCredits(
                requiredCredits = requiredCredits,
                currentBalance = balance,
                source = source,
            )
            return
        }

        _uiState.update {
            it.copy(
                isUnlocking = true,
                message = null,
                unlockDialogError = null,
                unlockDialogInsufficientCredits = false,
            )
        }
        viewModelScope.launch {
            rewardsRepository.unlockPack(packId)
                .onSuccess { response ->
                    _uiState.update { current ->
                        current.copy(
                            isUnlocking = false,
                            pack = current.pack?.copy(ownsPack = true),
                            items = current.items.map { it.toUnlockedItem() },
                            pointsBalance = response.pointsBalance ?: current.pointsBalance,
                            unlockDialogError = null,
                            unlockDialogInsufficientCredits = false,
                            message = when {
                                response.unlocked == true -> "Pack unlocked."
                                else -> response.message ?: "Pack updated."
                            }
                        )
                    }
                    loadPack(packId)
                }
                .onFailure { throwable ->
                    val failedMessage = throwable.message ?: "Failed to unlock pack."
                    val insufficientCredits = throwable.isInsufficientCreditsFailure()
                    if (insufficientCredits) {
                        handleInsufficientCredits(
                            requiredCredits = requiredCredits,
                            currentBalance = _uiState.value.pointsBalance,
                            source = source,
                        )
                        refreshPointsBalance()
                        return@onFailure
                    }
                    _uiState.update { current ->
                        current.copy(
                            isUnlocking = false,
                            message = if (source == PackUnlockSource.LockedPromptSheet) null else failedMessage,
                            unlockDialogError = if (source == PackUnlockSource.LockedPromptSheet) failedMessage else null,
                            unlockDialogInsufficientCredits = false,
                        )
                    }
                }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun clearUnlockDialogFeedback() {
        _uiState.update {
            it.copy(
                unlockDialogError = null,
                unlockDialogInsufficientCredits = false,
            )
        }
    }

    private fun refreshPointsBalance() {
        viewModelScope.launch {
            rewardsRepository.getProgress()
                .onSuccess { progress ->
                    _uiState.update { current -> current.copy(pointsBalance = progress.pointsBalance) }
                }
        }
    }

    private fun handleInsufficientCredits(
        requiredCredits: Int,
        currentBalance: Int?,
        source: PackUnlockSource,
    ) {
        val message = buildString {
            append("You don't have enough credits to unlock this prompt.")
            if (currentBalance != null) {
                append(" Required: ")
                append(requiredCredits)
                append(", balance: ")
                append(currentBalance)
                append(".")
            }
            append(" Visit Rewards to earn more.")
        }
        _uiState.update { current ->
            current.copy(
                isUnlocking = false,
                message = if (source == PackUnlockSource.LockedPromptSheet) null else message,
                unlockDialogError = if (source == PackUnlockSource.LockedPromptSheet) message else null,
                unlockDialogInsufficientCredits = source == PackUnlockSource.LockedPromptSheet,
            )
        }
    }

    private fun V2PromptDto.toUnlockedItem(): V2PromptDto {
        val safeTags = (tags as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        val safeUnlockMethods = (availableUnlockMethods as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        val safePackIds = (premiumPackIds as? List<*>)?.mapNotNull { value ->
            when (value) {
                is Number -> value.toInt()
                is String -> value.toIntOrNull()
                else -> null
            }
        } ?: emptyList()

        return runCatching {
            copy(
                isLocked = false,
                teaserText = null,
                tags = safeTags,
                availableUnlockMethods = safeUnlockMethods,
                premiumPackIds = safePackIds,
            )
        }.getOrElse { this }
    }

    private fun Throwable.isInsufficientCreditsFailure(): Boolean {
        if (this is V2ApiException && code == 402) return true
        val raw = message.orEmpty()
        return raw.contains("insufficient", ignoreCase = true) ||
            raw.contains("not enough", ignoreCase = true) ||
            (raw.contains("credits", ignoreCase = true) && raw.contains("low", ignoreCase = true))
    }
}
