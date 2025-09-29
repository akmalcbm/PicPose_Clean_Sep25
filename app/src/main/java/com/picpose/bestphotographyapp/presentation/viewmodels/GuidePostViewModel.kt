package com.picpose.bestphotographyapp.presentation.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.picpose.bestphotographyapp.data.models.GuidePost
import com.picpose.bestphotographyapp.data.repository.HomeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "GuidePostViewModel"

data class GuidePostUiState(
    val isLoading: Boolean = false,
    val guidePosts: List<GuidePost> = emptyList(),
    val error: String? = null,
    val isRefreshing: Boolean = false
)

@HiltViewModel
class GuidePostViewModel @Inject constructor(
    private val repository: HomeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GuidePostUiState())
    val uiState: StateFlow<GuidePostUiState> = _uiState.asStateFlow()

    init {
        loadGuidePosts()
    }

    fun loadGuidePosts(forceRefresh: Boolean = false) {
        if (!forceRefresh && _uiState.value.guidePosts.isNotEmpty()) {
            return // Already loaded
        }

        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                
                repository.getGuidePosts().collect { result ->
                    result.fold(
                        onSuccess = { pagination ->
                            Log.d(TAG, "loadGuidePosts: loaded ${pagination.items.size} guide posts")
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                guidePosts = pagination.items,
                                error = null
                            )
                        },
                        onFailure = { exception ->
                            Log.e(TAG, "loadGuidePosts: failed", exception)
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                error = exception.message ?: "Failed to load guide posts"
                            )
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadGuidePosts: unexpected error", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Unexpected error occurred"
                )
            }
        }
    }

    fun refresh() {
        _uiState.value = _uiState.value.copy(isRefreshing = true)
        loadGuidePosts(forceRefresh = true)
        _uiState.value = _uiState.value.copy(isRefreshing = false)
    }

    fun findGuidePostById(id: String): GuidePost? {
        return _uiState.value.guidePosts.find { it.id == id }
    }

    fun toggleFavorite(guidePost: GuidePost) {
        viewModelScope.launch {
            try {
                val updatedGuidePosts = _uiState.value.guidePosts.map { guide ->
                    if (guide.id == guidePost.id) {
                        guide.copy(isFavorited = !guide.isFavorited)
                    } else {
                        guide
                    }
                }
                _uiState.value = _uiState.value.copy(guidePosts = updatedGuidePosts)
            } catch (e: Exception) {
                Log.e(TAG, "toggleFavorite: failed", e)
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun incrementLikes(guidePostId: String) {
        viewModelScope.launch {
            try {
                val updatedGuidePosts = _uiState.value.guidePosts.map { guide ->
                    if (guide.id == guidePostId) {
                        guide.copy(likes = guide.likes + 1, isLiked = true)
                    } else {
                        guide
                    }
                }
                _uiState.value = _uiState.value.copy(guidePosts = updatedGuidePosts)
            } catch (e: Exception) {
                Log.e(TAG, "incrementLikes: failed", e)
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }
}