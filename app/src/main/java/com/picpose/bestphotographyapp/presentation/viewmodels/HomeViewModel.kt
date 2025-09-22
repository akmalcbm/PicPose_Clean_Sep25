package com.picpose.bestphotographyapp.presentation.viewmodels

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.picpose.bestphotographyapp.data.models.AIPrompt
import com.picpose.bestphotographyapp.data.models.Category
import com.picpose.bestphotographyapp.data.models.DailyTip
import com.picpose.bestphotographyapp.data.models.Post
import com.picpose.bestphotographyapp.data.repository.HomeRepository
import kotlinx.coroutines.Job
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
    val favoritePromptsCount: Int = 0,
    val dailyTips: List<DailyTip> = emptyList(),
    val error: String? = null,
    val isRefreshing: Boolean = false
)

class HomeViewModel(private val repository: HomeRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // Local photography tips used as fallback (keeps UX stable)
    private val photographyTips = listOf(
        "🎯 Use specific descriptive words in your prompts for better AI results!",
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

    private var currentTipIndex = 0

    // Keep reference to running jobs (optional)
    private var dailyTipsJob: Job? = null
    private var aiPromptsJob: Job? = null
    private var favoriteCountJob: Job? = null

    init {
        // Fetch minimal home data on init (only daily tips + AI prompts + favorite count)
        fetchDailyTips()
        loadAIPrompts()
        loadFavoriteCount()
    }

    /**
     * Fetch daily tips from server via repository.
     * Uses fallback tips if server call fails or returns empty.
     */
    fun fetchDailyTips() {
        dailyTipsJob?.cancel()
        dailyTipsJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                repository.getDailyTips().collect { result ->
                    result.fold(
                        onSuccess = { tips ->
                            if (!tips.isNullOrEmpty()) {
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
                _uiState.value = _uiState.value.copy(dailyTips = fallbackDailyTips(), error = e.message)
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    /**
     * Load AI prompts (simple list). Uses repository.getAiPostsSimple which returns Flow<Result<List<AIPrompt>>>
     * If repository provides paginated API later, you can switch to getAiPosts(...) and map PaginatedResult -> list.
     */
    fun loadAIPrompts(page: Int = 1, limit: Int = 12, category: String? = null, search: String? = null) {
        aiPromptsJob?.cancel()
        aiPromptsJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                // Use simple list version for now
                val flow = repository.getAiPostsSimple(page = page, limit = limit, category = category, search = search)
                flow.collect { result ->
                    result.fold(
                        onSuccess = { prompts ->
                            // Enrich favorites already done by repository; just update state
                            _uiState.value = _uiState.value.copy(aiPrompts = prompts)
                        },
                        onFailure = { throwable ->
                            // Fallback: if repository supports mocks it will provide; otherwise use an empty list
                            _uiState.value = _uiState.value.copy(aiPrompts = emptyList(), error = throwable.message)
                        }
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(aiPrompts = emptyList(), error = e.message)
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    /**
     * Load favorite count from repository (Room).
     */
    fun loadFavoriteCount() {
        favoriteCountJob?.cancel()
        favoriteCountJob = viewModelScope.launch {
            try {
                val count = repository.getFavoriteCount()
                _uiState.value = _uiState.value.copy(favoritePromptsCount = count)
            } catch (e: Exception) {
                // ignore or set to 0
            }
        }
    }

    // Convert local fallback tips into DailyTip objects
    private fun fallbackDailyTips(): List<DailyTip> =
        photographyTips.mapIndexed { idx, tip ->
            DailyTip(
                id = "fallback_$idx",
                tip = tip,
                isActive = true,
                order = idx,
                createdAt = null,
                updatedAt = null
            )
        }

    // Clipboard helper
    fun copyPromptToClipboard(context: Context, prompt: String?) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("AI Prompt", prompt)
            clipboard.setPrimaryClip(clip)
            // Optionally reflect a short UI hint via state (not implemented here)
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(error = e.message)
        }
    }

    // Toggle favorite and update UI list + count
    fun togglePromptFavorite(prompt: AIPrompt) {
        viewModelScope.launch {
            try {
                repository.toggleFavorite(prompt).collect { result ->
                    result.fold(
                        onSuccess = { isNowFavorite ->
                            // update prompt entry in uiState.aiPrompts
                            val updated = _uiState.value.aiPrompts.map {
                                if (it.id == prompt.id) it.copy(isFavorite = isNowFavorite) else it
                            }
                            _uiState.value = _uiState.value.copy(aiPrompts = updated)

                            // reload favorites count
                            loadFavoriteCount()
                        },
                        onFailure = { throwable ->
                            _uiState.value = _uiState.value.copy(error = throwable.message)
                        }
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    // Simple helper to get a prompt by id from current cache
    fun getPromptById(promptId: String): AIPrompt? {
        return _uiState.value.aiPrompts.find { it.id == promptId }
    }

    fun getCurrentTip(): String {
        val tips = _uiState.value.dailyTips
        return if (tips.isNotEmpty()) {
            tips.getOrNull(currentTipIndex)?.tip ?: photographyTips.getOrNull(currentTipIndex) ?: photographyTips.first()
        } else {
            photographyTips.getOrNull(currentTipIndex) ?: photographyTips.first()
        }
    }

    fun getNextTip(): String {
        val tips = _uiState.value.dailyTips
        currentTipIndex = if (tips.isNotEmpty()) {
            (currentTipIndex + 1) % tips.size
        } else {
            (currentTipIndex + 1) % photographyTips.size
        }
        return getCurrentTip()
    }

    fun getPreviousTip(): String {
        val tips = _uiState.value.dailyTips
        currentTipIndex = if (tips.isNotEmpty()) {
            if (currentTipIndex > 0) currentTipIndex - 1 else tips.size - 1
        } else {
            if (currentTipIndex > 0) currentTipIndex - 1 else photographyTips.size - 1
        }
        return getCurrentTip()
    }

    /**
     * Refresh minimal home data (AI prompts, daily tips, favorites)
     */
    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true, error = null)

            // Run refresh tasks concurrently but keep it simple
            val jobs = listOf(
                launch { fetchDailyTips() },
                launch { loadAIPrompts() },
                launch { loadFavoriteCount() }
            )

            // wait for children to finish
            jobs.forEach { it.join() }

            _uiState.value = _uiState.value.copy(isRefreshing = false)
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    override fun onCleared() {
        super.onCleared()
        // cancel jobs if needed (coroutineScope will handle but keeping explicit)
        dailyTipsJob?.cancel()
        aiPromptsJob?.cancel()
        favoriteCountJob?.cancel()
    }

    /**
     * Called when a prompt is viewed. Currently a no-op placeholder.
     * You can implement server-side view counting later by adding repository.incrementView(promptId).
     */
    fun logPromptView(promptId: String) {
        viewModelScope.launch {
            try {
                // Optional: if you implement in repository, call it here:
                // repository.incrementView(promptId)
            } catch (e: Exception) {
                // ignore for now; keep analytics non-blocking
            }
        }
    }

    /**
     * Called when a prompt is copied. Placeholder for analytics or server-side count.
     */
    fun logPromptCopy(promptId: String) {
        viewModelScope.launch {
            try {
                // Optional: repository.incrementCopy(promptId)
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    /**
     * Toggle like for a post (local UI update). If you add server API later, call it here.
     */
    fun togglePostLike(postId: String) {
        viewModelScope.launch {
            try {
                // Update featured posts list
                val updatedFeatured = _uiState.value.featuredPosts.map { post ->
                    if (post.id == postId) post.copy(likes = post.likes + 1) else post
                }

                // Update recent posts list
                val updatedRecent = _uiState.value.recentPosts.map { post ->
                    if (post.id == postId) post.copy(likes = post.likes + 1) else post
                }

                _uiState.value = _uiState.value.copy(
                    featuredPosts = updatedFeatured,
                    recentPosts = updatedRecent
                )

                // Optional: send to server
                // repository.incrementLike(postId)

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    /**
     * Share a post: copy text to clipboard and show a toast.
     * Keep this in ViewModel for simplicity (it needs Context).
     */
    fun sharePost(context: Context, post: Post) {
        try {
            val shareText = buildString {
                append("Check out this amazing photo: ${post.title}\n\n")
                append(post.description ?: "")
                append("\n\n#PicPose #Photography")
            }

            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Shared Post", shareText)
            clipboard.setPrimaryClip(clip)

            Toast.makeText(context, "Post details copied to clipboard!", Toast.LENGTH_SHORT).show()

            // Optional: analytics or server call to record share
            viewModelScope.launch {
                try {
                    // repository.recordShare(post.id)
                } catch (_: Exception) { /* ignore */ }
            }
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(error = e.message)
        }
    }

}