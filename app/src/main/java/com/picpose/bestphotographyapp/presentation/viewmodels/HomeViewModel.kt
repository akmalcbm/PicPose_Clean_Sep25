package com.picpose.bestphotographyapp.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.picpose.bestphotographyapp.data.models.AIPrompt
import com.picpose.bestphotographyapp.data.models.Category
import com.picpose.bestphotographyapp.data.models.Post
import com.picpose.bestphotographyapp.data.repository.HomeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = false,
    val featuredPosts: List<Post> = emptyList(),
    val recentPosts: List<Post> = emptyList(),
    val categories: List<Category> = emptyList(),
    val aiPrompts: List<AIPrompt> = emptyList(),
    val error: String? = null,
    val isRefreshing: Boolean = false
)

class HomeViewModel : ViewModel() {
    private val repository = HomeRepository()

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val photographyTips = listOf(
        "Use the rule of thirds for better composition",
        "Golden hour provides the most flattering natural light",
        "Focus on the eyes in portrait photography",
        "Use leading lines to guide the viewer's attention",
        "Experiment with different angles and perspectives",
        "Learn to use negative space effectively",
        "Master manual camera settings for creative control",
        "Practice the art of storytelling through images"
    )

    private var currentTipIndex = 0

    init {
        loadHomeData()
        loadAIPrompts()
    }

    fun loadHomeData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                // Load featured posts
                repository.getFeaturedPosts().collect { result ->
                    result.fold(
                        onSuccess = { posts ->
                            _uiState.value = _uiState.value.copy(featuredPosts = posts)
                        },
                        onFailure = {
                            // Use mock data as fallback
                            _uiState.value = _uiState.value.copy(
                                featuredPosts = repository.getMockFeaturedPosts()
                            )
                        }
                    )
                }

                // Load recent posts
                repository.getRecentPosts().collect { result ->
                    result.fold(
                        onSuccess = { posts ->
                            _uiState.value = _uiState.value.copy(recentPosts = posts)
                        },
                        onFailure = {
                            // Use mock data as fallback
                            _uiState.value = _uiState.value.copy(
                                recentPosts = repository.getMockFeaturedPosts()
                            )
                        }
                    )
                }

                // Load categories
                repository.getCategories().collect { result ->
                    result.fold(
                        onSuccess = { categories ->
                            _uiState.value = _uiState.value.copy(categories = categories)
                        },
                        onFailure = {
                            // Use mock data as fallback
                            _uiState.value = _uiState.value.copy(
                                categories = repository.getMockCategories()
                            )
                        }
                    )
                }

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message,
                    featuredPosts = repository.getMockFeaturedPosts(),
                    categories = repository.getMockCategories()
                )
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    private fun loadAIPrompts() {
        viewModelScope.launch {
            try {
                // For now, use mock data. Later connect to your API
                val mockPrompts = repository.getMockAIPrompts()
                _uiState.value = _uiState.value.copy(aiPrompts = mockPrompts)
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    // Add this function for copying prompts
    fun copyPromptToClipboard(context: android.content.Context, prompt: String) {
        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("AI Prompt", prompt)
        clipboard.setPrimaryClip(clip)
    }

    fun refreshData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            loadHomeData()
            _uiState.value = _uiState.value.copy(isRefreshing = false)
        }
    }

    fun getNextTip(): String {
        currentTipIndex = (currentTipIndex + 1) % photographyTips.size
        return photographyTips[currentTipIndex]
    }

    fun getCurrentTip(): String = photographyTips[currentTipIndex]
}
