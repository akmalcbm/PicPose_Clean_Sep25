package com.picpose.bestphotographyapp.presentation.viewmodels

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.picpose.bestphotographyapp.data.datastore.SettingsManager
import com.picpose.bestphotographyapp.data.rembg.BackgroundRemovalRepository
import com.picpose.bestphotographyapp.data.rembg.BackgroundRemovalRequest
import com.picpose.bestphotographyapp.data.rembg.BgBackgroundMode
import com.picpose.bestphotographyapp.data.rembg.BgBackgroundOption
import com.picpose.bestphotographyapp.data.rembg.BgRemovalQualityMode
import com.picpose.bestphotographyapp.util.ImageIO
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CreateUiState(
    val selectedImageUri: Uri? = null,
    val isRemovingBg: Boolean = false,
    val removeBgError: String? = null,
    val removeBgProgress: Float? = null,
    val removeBgPreviewUri: Uri? = null,
    val removeBgCutoutUri: Uri? = null,
    val bgRemovalDisclosureAccepted: Boolean = false,
    val showDisclosureDialog: Boolean = false,
    val showPreviewSheet: Boolean = false,
    val previewShowBefore: Boolean = false,
    val qualityMode: BgRemovalQualityMode = BgRemovalQualityMode.HIGH_QUALITY_ONLINE,
    val backgroundOption: BgBackgroundOption = BgBackgroundOption(mode = BgBackgroundMode.TRANSPARENT)
)

sealed class CreateUiEvent {
    data class ShowMessage(val message: String) : CreateUiEvent()
}

@HiltViewModel
class CreateViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val settingsManager: SettingsManager,
    private val backgroundRemovalRepository: BackgroundRemovalRepository,
    private val imageIO: ImageIO
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateUiState())
    val uiState: StateFlow<CreateUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<CreateUiEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<CreateUiEvent> = _events.asSharedFlow()

    private var removeJob: Job? = null

    init {
        viewModelScope.launch {
            settingsManager.bgRemovalDisclosureAccepted.collectLatest { accepted ->
                _uiState.update { it.copy(bgRemovalDisclosureAccepted = accepted) }
            }
        }
    }

    fun onImageSelected(uri: Uri?) {
        _uiState.update {
            it.copy(
                selectedImageUri = uri,
                removeBgError = null,
                removeBgPreviewUri = null,
                removeBgCutoutUri = null,
                showPreviewSheet = false,
                previewShowBefore = true
            )
        }
    }

    fun onClickRemoveBg() {
        val imageUri = _uiState.value.selectedImageUri
        if (imageUri == null) {
            emitMessage("Please pick an image first.")
            return
        }

        if (!_uiState.value.bgRemovalDisclosureAccepted) {
            _uiState.update { it.copy(showDisclosureDialog = true) }
            return
        }

        _uiState.update { it.copy(showPreviewSheet = true, previewShowBefore = false) }
        onConfirmBgRemoval(_uiState.value.qualityMode)
    }

    fun onConfirmBgRemoval(mode: BgRemovalQualityMode) {
        _uiState.update {
            it.copy(
                qualityMode = mode,
                isRemovingBg = true,
                removeBgError = null,
                removeBgProgress = null,
                showPreviewSheet = true
            )
        }

        runBackgroundRemoval(mode)
    }

    fun onApplyRemovedBg() {
        val appliedUri = _uiState.value.removeBgPreviewUri ?: return
        _uiState.update {
            it.copy(
                selectedImageUri = appliedUri,
                showPreviewSheet = false,
                removeBgError = null
            )
        }
        emitMessage("Background removed and applied.")
    }

    fun onCancelBgRemoval() {
        removeJob?.cancel()
        _uiState.update {
            it.copy(
                isRemovingBg = false,
                removeBgProgress = null,
                showPreviewSheet = false,
                removeBgError = null
            )
        }
    }

    fun onRetry() {
        onConfirmBgRemoval(_uiState.value.qualityMode)
    }

    fun onDisclosureCancelled() {
        _uiState.update { it.copy(showDisclosureDialog = false) }
    }

    fun onDisclosureAccepted() {
        _uiState.update { it.copy(showDisclosureDialog = false) }
        viewModelScope.launch {
            settingsManager.setBgRemovalDisclosureAccepted(true)
        }
        _uiState.update { it.copy(showPreviewSheet = true) }
        onConfirmBgRemoval(_uiState.value.qualityMode)
    }

    fun onSetBackgroundOption(option: BgBackgroundOption) {
        _uiState.update { it.copy(backgroundOption = option) }
        regeneratePreviewWithCurrentCutout()
    }

    fun onPreviewToggle(showBefore: Boolean) {
        _uiState.update { it.copy(previewShowBefore = showBefore) }
    }

    fun onSavePreviewAsPng() {
        val previewUri = _uiState.value.removeBgPreviewUri
        if (previewUri == null) {
            emitMessage("Nothing to save yet.")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val saveResult = backgroundRemovalRepository.savePngToGallery(previewUri)
            if (saveResult.isSuccess) {
                emitMessage("Saved PNG to Pictures/PicPose")
            } else {
                emitMessage(saveResult.exceptionOrNull()?.message ?: "Failed to save PNG")
            }
        }
    }

    private fun runBackgroundRemoval(mode: BgRemovalQualityMode) {
        val sourceUri = _uiState.value.selectedImageUri ?: return
        removeJob?.cancel()
        removeJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = backgroundRemovalRepository.removeBackground(
                    BackgroundRemovalRequest(
                        sourceUri = sourceUri,
                        qualityMode = mode,
                        backgroundOption = _uiState.value.backgroundOption,
                        previewSize = true
                    )
                )

                result.fold(
                    onSuccess = { output ->
                        _uiState.update {
                            it.copy(
                                isRemovingBg = false,
                                removeBgError = null,
                                removeBgProgress = 1f,
                                removeBgCutoutUri = output.cutoutUri,
                                removeBgPreviewUri = output.previewUri,
                                previewShowBefore = false,
                                showPreviewSheet = true
                            )
                        }
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                isRemovingBg = false,
                                removeBgError = error.message ?: "Failed to remove background",
                                removeBgProgress = null
                            )
                        }
                        emitMessage(error.message ?: "Failed to remove background")
                    }
                )
            } catch (cancelled: CancellationException) {
                _uiState.update { it.copy(isRemovingBg = false, removeBgProgress = null) }
                throw cancelled
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        isRemovingBg = false,
                        removeBgError = t.message ?: "Unexpected error",
                        removeBgProgress = null
                    )
                }
                emitMessage(t.message ?: "Unexpected error")
            }
        }
    }

    private fun regeneratePreviewWithCurrentCutout() {
        val sourceUri = _uiState.value.selectedImageUri ?: return
        val cutoutUri = _uiState.value.removeBgCutoutUri ?: return

        viewModelScope.launch(Dispatchers.IO) {
            val preview = imageIO.compositeBackground(
                originalUri = sourceUri,
                cutoutUri = cutoutUri,
                backgroundOption = _uiState.value.backgroundOption
            )

            preview.fold(
                onSuccess = { uri ->
                    _uiState.update { it.copy(removeBgPreviewUri = uri, removeBgError = null) }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(removeBgError = error.message ?: "Preview update failed") }
                }
            )
        }
    }

    private fun emitMessage(message: String) {
        _events.tryEmit(CreateUiEvent.ShowMessage(message))
    }
}
