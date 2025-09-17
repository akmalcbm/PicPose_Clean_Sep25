package com.picpose.bestphotographyapp.presentation.viewmodels

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.picpose.bestphotographyapp.data.models.AIPrompt
import com.picpose.bestphotographyapp.data.models.Category
import com.picpose.bestphotographyapp.data.models.DailyTip
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
    val favoritePromptsCount: Int = 0, // Added for favorites count
    val dailyTips: List<DailyTip> = emptyList(), // <-- server tips here
    val error: String? = null,
    val isRefreshing: Boolean = false
)

class HomeViewModel(private val repository: HomeRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // Local photography tips used as fallback (if API fails)
    private val photographyTips = listOf(
        "🎯 Use the rule of thirds for better composition",
        "🌅 Golden hour provides the most flattering natural light",
        "👁️ Focus on the eyes in portrait photography",
        "🛤️ Use leading lines to guide the viewer's attention",
        "📐 Experiment with different angles and perspectives",
        "⚪ Learn to use negative space effectively",
        "⚙️ Master manual camera settings for creative control",
        "📖 Practice the art of storytelling through images",
        "🤖 Try AI prompts: 'Professional headshot, soft lighting, clean background'",
        "✨ AI Prompt: 'Cinematic portrait, golden hour, bokeh background'",
        "🎨 Use AI: 'Street photography style, black and white, urban setting'",
        "🌟 AI Magic: 'Fashion photography, dramatic lighting, studio setup'"
    )

    // Helper to convert fallback tips into DailyTip objects (order preserved)
    private fun fallbackDailyTips(): List<DailyTip> =
        photographyTips.mapIndexed { idx, tip ->
            // adapt fields to match your DailyTip model
            DailyTip(
                id = "fallback_$idx",
                tip = tip,
                isActive = true,
                order = idx,
                createdAt = null,
                updatedAt = null
            )
        }

    private var currentTipIndex = 0

    init {
        loadHomeData()
        loadAIPrompts()
        loadFavoriteCount()
        fetchDailyTips() // fetch tips on init
    }

    /**
     * Fetch home sections (featured, recent, categories).
     * This function tries to load data from repository flows and uses fallbacks on failure.
     */
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

    /**
     * Public method to fetch daily tips from API via repository.
     * Exposed so UI can trigger it explicitly if needed.
     */
    fun fetchDailyTips() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                repository.getDailyTips().collect { result: Result<List<com.picpose.bestphotographyapp.data.models.DailyTip>> ->
                    result.fold(
                        onSuccess = { tips ->
                            if (tips.isNotEmpty()) {
                                _uiState.value = _uiState.value.copy(dailyTips = tips)
                            } else {
                                _uiState.value = _uiState.value.copy(dailyTips = fallbackDailyTips())
                            }
                        },
                        onFailure = { throwable ->
                            _uiState.value = _uiState.value.copy(
                                dailyTips = fallbackDailyTips(),
                                error = throwable.message
                            )
                        }
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    dailyTips = fallbackDailyTips(),
                    error = e.message
                )
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }


    fun refreshAllData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                // Load all data concurrently where possible
                loadFeaturedPosts()
                loadRecentPosts()
                loadCategories()
                fetchDailyTips()

                _uiState.value = _uiState.value.copy(isLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Unknown error occurred",
                    isLoading = false
                )
            }
        }
    }

    // Optional convenience for UI if needed
    fun getNextTip(): String {
        val tips = _uiState.value.dailyTips
        return if (tips.isNotEmpty()) {
            currentTipIndex = (currentTipIndex + 1) % tips.size
            tips[currentTipIndex].tip
        } else {
            currentTipIndex = (currentTipIndex + 1) % photographyTips.size
            photographyTips[currentTipIndex]
        }
    }

    private fun loadAIPrompts() {
        viewModelScope.launch {
            try {
                repository.getAllAIPromptsSimple(limit = 12).collect { result: Result<List<com.picpose.bestphotographyapp.data.models.AIPrompt>> ->
                    result.fold(
                        onSuccess = { prompts ->
                            _uiState.value = _uiState.value.copy(aiPrompts = prompts)
                        },
                        onFailure = { throwable ->
                            // fallback to your mock prompts already available
                            val mockPrompts = repository.getMockAIPrompts()
                            _uiState.value = _uiState.value.copy(aiPrompts = mockPrompts, error = throwable.message)
                        }
                    )
                }
            } catch (e: Exception) {
                val mockPrompts = repository.getMockAIPrompts()
                _uiState.value = _uiState.value.copy(aiPrompts = mockPrompts, error = e.message)
            }
        }
    }

    private fun loadFavoriteCount() {
        viewModelScope.launch {
            try {
                val count = repository.getFavoriteCount()
                _uiState.value = _uiState.value.copy(favoritePromptsCount = count)
            } catch (e: Exception) {
                // Handle error silently
            }
        }
    }

    // Copy prompt to clipboard
    fun copyPromptToClipboard(context: Context, prompt: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("AI Prompt", prompt)
        clipboard.setPrimaryClip(clip)
    }

    // Toggle favorite for a prompt
    fun togglePromptFavorite(prompt: AIPrompt) {
        viewModelScope.launch {
            try {
                repository.toggleFavorite(prompt).collect { result ->
                    result.fold(
                        onSuccess = { isFavorite ->
                            // Update the prompt in the current list
                            val updatedPrompts = _uiState.value.aiPrompts.map {
                                if (it.id == prompt.id) {
                                    it.copy(isFavorite = isFavorite)
                                } else it
                            }
                            _uiState.value = _uiState.value.copy(aiPrompts = updatedPrompts)

                            // Reload favorite count
                            loadFavoriteCount()
                        },
                        onFailure = { exception ->
                            _uiState.value = _uiState.value.copy(error = exception.message)
                        }
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    // Toggle like for a post
    fun togglePostLike(postId: String) {
        viewModelScope.launch {
            try {
                // Update the post in featured posts
                val updatedFeaturedPosts = _uiState.value.featuredPosts.map { post ->
                    if (post.id == postId) {
                        post.copy(
                            likes = post.likes + 1 // Just increment likes (your Post model has 'likes')
                        )
                    } else post
                }

                // Update the post in recent posts
                val updatedRecentPosts = _uiState.value.recentPosts.map { post ->
                    if (post.id == postId) {
                        post.copy(
                            likes = post.likes + 1
                        )
                    } else post
                }

                _uiState.value = _uiState.value.copy(
                    featuredPosts = updatedFeaturedPosts,
                    recentPosts = updatedRecentPosts
                )

                // TODO: Send like to server
                // repository.togglePostLike(postId)

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    // Fixed sharePost method - using your Post model properties
    fun sharePost(context: Context, post: Post) {
        try {
            // Use 'title' and 'description' from your Post model
            val shareText = "Check out this amazing photo: ${post.title}\n\n${post.description}\n\n#PicPose #Photography"

            // Copy to clipboard as a simple share mechanism
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Shared Post", shareText)
            clipboard.setPrimaryClip(clip)

            android.widget.Toast.makeText(context, "Post details copied to clipboard!", android.widget.Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(error = "Failed to share post")
        }
    }

    // Add these if you don't have them already
    private fun loadFeaturedPosts() {
        viewModelScope.launch {
            try {
                // If you already have repository flows, you may use them directly.
                repository.getFeaturedPosts().collect { result ->
                    result.fold(
                        onSuccess = { posts ->
                            _uiState.value = _uiState.value.copy(featuredPosts = posts)
                        },
                        onFailure = {
                            _uiState.value = _uiState.value.copy(featuredPosts = repository.getMockFeaturedPosts())
                        }
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    private fun loadRecentPosts() {
        viewModelScope.launch {
            try {
                repository.getRecentPosts().collect { result ->
                    result.fold(
                        onSuccess = { posts ->
                            _uiState.value = _uiState.value.copy(recentPosts = posts)
                        },
                        onFailure = {
                            _uiState.value = _uiState.value.copy(recentPosts = repository.getMockFeaturedPosts())
                        }
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    private fun loadCategories() {
        viewModelScope.launch {
            try {
                repository.getCategories().collect { result ->
                    result.fold(
                        onSuccess = { categories ->
                            _uiState.value = _uiState.value.copy(categories = categories)
                        },
                        onFailure = {
                            _uiState.value = _uiState.value.copy(categories = repository.getMockCategories())
                        }
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    // Photography tip functions (keeps original behaviour if needed)
    fun getCurrentTip(): String {
        val tips = _uiState.value.dailyTips
        return if (tips.isNotEmpty()) {
            tips.getOrNull(currentTipIndex)?.tip ?: photographyTips.getOrNull(currentTipIndex) ?: photographyTips.first()
        } else {
            photographyTips.getOrNull(currentTipIndex) ?: photographyTips.first()
        }
    }

    fun getPreviousTip(): String {
        val tips = _uiState.value.dailyTips
        if (tips.isNotEmpty()) {
            currentTipIndex = if (currentTipIndex > 0) currentTipIndex - 1 else tips.size - 1
            return tips[currentTipIndex].tip
        } else {
            currentTipIndex = if (currentTipIndex > 0) currentTipIndex - 1 else photographyTips.size - 1
            return photographyTips[currentTipIndex]
        }
    }

    // Refresh all data (used by UI)
    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)

            loadHomeData()
            loadAIPrompts()
            loadFavoriteCount()
            fetchDailyTips()

            _uiState.value = _uiState.value.copy(isRefreshing = false)
        }
    }

    // Clear error
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    // Search prompts (for future use)
    fun searchPrompts(query: String) {
        viewModelScope.launch {
            try {
                if (query.isBlank()) {
                    loadAIPrompts() // Reload all prompts
                } else {
                    val filteredPrompts = _uiState.value.aiPrompts.filter { prompt ->
                        prompt.title.contains(query, ignoreCase = true) ||
                                prompt.shortPrompt.contains(query, ignoreCase = true) ||
                                prompt.category.contains(query, ignoreCase = true)
                    }
                    _uiState.value = _uiState.value.copy(aiPrompts = filteredPrompts)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    // Get prompt by ID (for navigation)
    fun getPromptById(promptId: String): AIPrompt? {
        return _uiState.value.aiPrompts.find { it.id == promptId }
    }

    // Analytics functions (for future use)
    fun logPromptView(promptId: String) {
        // TODO: Send analytics event
    }

    fun logPromptCopy(promptId: String) {
        // TODO: Send analytics event
    }

    override fun onCleared() {
        super.onCleared()
        // Clean up any resources if needed
    }
}
