package com.picpose.bestphotographyapp.ui.details

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.picpose.bestphotographyapp.data.AdManager
import com.picpose.bestphotographyapp.data.PromptRepository
import com.picpose.bestphotographyapp.data.models.AIPrompt
import com.picpose.bestphotographyapp.data.models.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI State for AI Prompt Details Screen
 */
data class PromptUiState(
    val isLoading: Boolean = false,
    val currentPrompt: AIPrompt? = null,
    val similarPrompts: List<AIPrompt> = emptyList(),
    val error: String? = null,
    val isLoadingMore: Boolean = false,
    val appSettings: AppSettings? = null,
    val showAdLoader: Boolean = false
)

/**
 * AiPromptDetailsViewModel - ViewModel for AI Prompt Details Screen
 * 
 * Responsibilities:
 * - Load prompt details by ID
 * - Load similar prompts
 * - Manage ad click counter
 * - Coordinate with AdManager for interstitial ads
 * - Provide state for UI
 */
class AiPromptDetailsViewModel(
    private val repository: PromptRepository = PromptRepository(),
    private val adManager: AdManager = AdManager.getInstance()
) : ViewModel() {
    
    companion object {
        private const val TAG = "AiPromptDetailsVM"
        private const val SIMILAR_PROMPTS_PAGE_SIZE = 5
    }
    
    private val _uiState = MutableStateFlow(PromptUiState())
    val uiState: StateFlow<PromptUiState> = _uiState.asStateFlow()
    
    private var currentPage = 1
    private var hasMoreSimilarPrompts = true
    
    init {
        // Load app settings on init
        loadAppSettings()
    }
    
    /**
     * Load app settings for AdMob configuration
     */
    private fun loadAppSettings() {
        viewModelScope.launch {
            try {
                repository.getAppSettings().collect { result ->
                    result.fold(
                        onSuccess = { settings ->
                            Log.d(TAG, "App settings loaded successfully")
                            _uiState.value = _uiState.value.copy(appSettings = settings)
                            // Initialize AdManager with settings
                            adManager.initialize(settings, clickFrequency = 3)
                            // Note: Ads will be preloaded from the screen with context
                        },
                        onFailure = { error ->
                            Log.w(TAG, "Failed to load app settings: ${error.message}")
                            // AdManager will use fallback test IDs
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception loading app settings: ${e.message}")
            }
        }
    }
    
    /**
     * Preload ads with context from Activity
     */
    fun preloadAds(context: Context) {
        adManager.preloadAds(context)
    }
    
    /**
     * Load prompt by ID
     */
    fun loadPromptById(promptId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            try {
                repository.getPromptById(promptId).collect { result ->
                    result.fold(
                        onSuccess = { prompt ->
                            Log.d(TAG, "Prompt loaded successfully: ${prompt.title}")
                            _uiState.value = _uiState.value.copy(
                                currentPrompt = prompt,
                                isLoading = false,
                                error = null
                            )
                            
                            // Load similar prompts
                            prompt.category?.let { category ->
                                loadSimilarPrompts(category, promptId)
                            }
                        },
                        onFailure = { error ->
                            Log.e(TAG, "Failed to load prompt: ${error.message}")
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                error = error.message ?: "Failed to load prompt"
                            )
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception loading prompt: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "An error occurred"
                )
            }
        }
    }
    
    /**
     * Load similar prompts by category
     */
    private fun loadSimilarPrompts(category: String, excludeId: String) {
        viewModelScope.launch {
            try {
                val limit = currentPage * SIMILAR_PROMPTS_PAGE_SIZE
                repository.getSimilarPrompts(category, excludeId, limit).collect { result ->
                    result.fold(
                        onSuccess = { prompts ->
                            Log.d(TAG, "Loaded ${prompts.size} similar prompts")
                            _uiState.value = _uiState.value.copy(
                                similarPrompts = prompts,
                                isLoadingMore = false
                            )
                            hasMoreSimilarPrompts = prompts.size >= limit
                        },
                        onFailure = { error ->
                            Log.w(TAG, "Failed to load similar prompts: ${error.message}")
                            _uiState.value = _uiState.value.copy(isLoadingMore = false)
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception loading similar prompts: ${e.message}")
                _uiState.value = _uiState.value.copy(isLoadingMore = false)
            }
        }
    }
    
    /**
     * Load more similar prompts (pagination)
     */
    fun loadMoreSimilarPrompts() {
        if (!hasMoreSimilarPrompts || _uiState.value.isLoadingMore) {
            return
        }
        
        val prompt = _uiState.value.currentPrompt ?: return
        val category = prompt.category ?: return
        
        _uiState.value = _uiState.value.copy(isLoadingMore = true)
        currentPage++
        
        loadSimilarPrompts(category, prompt.id)
    }
    
    /**
     * Handle similar prompt click
     * Increments ad counter and checks if ad should be shown
     */
    fun onSimilarPromptClicked() {
        adManager.incrementClickCount()
        Log.d(TAG, "Similar prompt clicked, should show ad: ${adManager.shouldShowInterstitial()}")
    }
    
    /**
     * Check if interstitial ad should be shown
     */
    fun shouldShowInterstitial(): Boolean {
        return adManager.shouldShowInterstitial()
    }
    
    /**
     * Set ad loader visibility
     */
    fun setShowAdLoader(show: Boolean) {
        _uiState.value = _uiState.value.copy(showAdLoader = show)
    }
    
    /**
     * Clear error message
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    /**
     * Reset state for new prompt (called when navigating to new prompt)
     */
    fun resetForNewPrompt() {
        currentPage = 1
        hasMoreSimilarPrompts = true
        _uiState.value = _uiState.value.copy(
            similarPrompts = emptyList(),
            isLoadingMore = false
        )
    }
    
    /**
     * Toggle favorite status of current prompt
     */
    fun toggleFavorite(prompt: AIPrompt) {
        viewModelScope.launch {
            try {
                repository.toggleFavorite(prompt).collect { result ->
                    result.fold(
                        onSuccess = { isNowFavorite ->
                            Log.d(TAG, "Favorite toggled: $isNowFavorite")
                            // Update current prompt's favorite status
                            _uiState.value = _uiState.value.copy(
                                currentPrompt = prompt.copy(isFavouriteBookmarked = isNowFavorite)
                            )
                        },
                        onFailure = { error ->
                            Log.e(TAG, "Failed to toggle favorite: ${error.message}")
                            _uiState.value = _uiState.value.copy(
                                error = error.message ?: "Failed to toggle favorite"
                            )
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception toggling favorite: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "An error occurred"
                )
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ViewModel cleared")
    }
}
