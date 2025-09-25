package com.picpose.bestphotographyapp.presentation.viewmodels

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.picpose.bestphotographyapp.data.models.AIPrompt
import com.picpose.bestphotographyapp.data.models.Category
import com.picpose.bestphotographyapp.data.models.DailyTip
import com.picpose.bestphotographyapp.data.models.Post
import com.picpose.bestphotographyapp.data.repository.HomeRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.system.measureTimeMillis

private const val TAG = "HomeViewModel"

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

    // concurrency guards & throttles
    @Volatile
    private var isLoadingAIPrompts: Boolean = false
    @Volatile
    private var isLoadingDailyTips: Boolean = false

    private var lastRefreshTimestamp = 0L
    private val MIN_REFRESH_INTERVAL_MS = 3_000L // 3 seconds throttle for refresh

    // search debounce flow
    private val _searchQuery = MutableStateFlow("")
    // expose if UI needs to read it; else internal only
    val searchQuery = _searchQuery.asStateFlow()

    init {
        // Debounce search queries and trigger loadAIPrompts
        viewModelScope.launch {
            _searchQuery
                .debounce(400) // 400ms idle before search executes
                .collectLatest { q ->
                    Log.d(TAG, "Debounced search query: '$q'")
                    // If blank, call without search param (loads default list)
                    loadAIPrompts(page = 1, limit = 12, category = null, search = q.ifBlank { null })
                }
        }

        // Initial minimal load
        fetchDailyTips()
        loadAIPrompts()
        loadFavoriteCount()
    }

    /**
     * Fetch daily tips from server via repository.
     * Uses fallback tips if server call fails or returns empty.
     * Includes proper cancellation handling and concurrency guards.
     */
    private var lastFetchTime = 0L
    private val CACHE_DURATION = 5 * 60 * 1000L // 5 minutes cache
    private var cachedDailyTips: List<DailyTip>? = null
    private var cachedAIPrompts: List<AIPrompt>? = null
    fun fetchDailyTips() {
        // Check cache first
        val now = System.currentTimeMillis()
        if (cachedDailyTips != null && (now - lastFetchTime) < CACHE_DURATION) {
            _uiState.value = _uiState.value.copy(dailyTips = cachedDailyTips!!)
            return
        }

        dailyTipsJob?.cancel()
        dailyTipsJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                Log.d(TAG, "fetchDailyTips: starting")
                repository.getDailyTips().collect { result ->
                    result.fold(
                        onSuccess = { tips ->
                            if (!tips.isNullOrEmpty()) {
                                Log.d(TAG, "fetchDailyTips: received ${tips.size} tips")
                                cachedDailyTips = tips // ✅ Cache the data
                                lastFetchTime = now // ✅ Update cache time
                                _uiState.value = _uiState.value.copy(dailyTips = tips)
                            } else {
                                Log.w(TAG, "fetchDailyTips: empty tips from server, using fallback")
                                _uiState.value = _uiState.value.copy(dailyTips = fallbackDailyTips())
                            }
                        },
                        onFailure = { throwable ->
                            Log.w(TAG, "fetchDailyTips failed: ${throwable.message}")
                            _uiState.value = _uiState.value.copy(
                                dailyTips = cachedDailyTips ?: fallbackDailyTips(), // ✅ Use cache if available
                                error = throwable.message
                            )
                        }
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) {
                    Log.d(TAG, "fetchDailyTips cancelled") // ✅ Don't treat cancellation as error
                    return@launch
                }
                Log.e(TAG, "fetchDailyTips exception: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    dailyTips = cachedDailyTips ?: fallbackDailyTips(), // ✅ Use cache if available
                    error = e.message
                )
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    /**
     * Load AI prompts (simple list). Uses repository.getAiPostsSimple which returns Flow<Result<List<AIPrompt>>>
     * Includes proper cancellation handling and concurrency guards.
     */
    fun loadAIPrompts(page: Int = 1, limit: Int = 12, category: String? = null, search: String? = null) {
        // prevent duplicate concurrent loads
        if (isLoadingAIPrompts) {
            Log.w(TAG, "loadAIPrompts skipped - already loading")
            return
        }

        // Only cancel if job is running and not completing
        aiPromptsJob?.let { job ->
            if (job.isActive && !job.isCompleted) {
                Log.d(TAG, "loadAIPrompts: cancelling previous job")
                job.cancel()
            }
        }

        aiPromptsJob = viewModelScope.launch {
            isLoadingAIPrompts = true
            val elapsed = measureTimeMillis {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                try {
                    Log.d(TAG, "loadAIPrompts: page=$page limit=$limit category=$category search=$search")
                    val flow = repository.getAiPostsSimple(page = page, limit = limit, category = category, search = search)
                    flow.collect { result ->
                        result.fold(
                            onSuccess = { prompts ->
                                Log.d(TAG, "loadAIPrompts: received ${prompts.size} prompts")
                                _uiState.value = _uiState.value.copy(aiPrompts = prompts)
                            },
                            onFailure = { throwable ->
                                // Don't treat cancellation as an error
                                if (throwable is CancellationException) {
                                    Log.d(TAG, "loadAIPrompts cancelled: ${throwable.message}")
                                    return@fold // Exit early, don't update UI with error
                                }
                                Log.w(TAG, "loadAIPrompts failed: ${throwable.message}")
                                _uiState.value = _uiState.value.copy(aiPrompts = emptyList(), error = throwable.message)
                            }
                        )
                    }
                } catch (e: CancellationException) {
                    // Proper cancellation handling - don't treat as error
                    Log.d(TAG, "loadAIPrompts cancelled: ${e.message}")
                    throw e // Re-throw to let coroutine handle it properly
                } catch (e: Exception) {
                    Log.e(TAG, "loadAIPrompts exception: ${e.message}")
                    _uiState.value = _uiState.value.copy(aiPrompts = emptyList(), error = e.message)
                } finally {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            }
            isLoadingAIPrompts = false
            Log.d(TAG, "loadAIPrompts finished in ${elapsed}ms")
        }
    }

    /**
     * Load favorite count from repository (Room).
     * Includes proper cancellation handling.
     */
    fun loadFavoriteCount() {
        // Only cancel if job is running and not completing
        favoriteCountJob?.let { job ->
            if (job.isActive && !job.isCompleted) {
                Log.d(TAG, "loadFavoriteCount: cancelling previous job")
                job.cancel()
            }
        }

        favoriteCountJob = viewModelScope.launch {
            try {
                Log.d(TAG, "loadFavoriteCount: querying room")
                val count = repository.getFavoriteCount()
                _uiState.value = _uiState.value.copy(favoritePromptsCount = count)
            } catch (e: CancellationException) {
                // Proper cancellation handling - don't treat as error
                Log.d(TAG, "loadFavoriteCount cancelled: ${e.message}")
                throw e // Re-throw to let coroutine handle it properly
            } catch (e: Exception) {
                Log.w(TAG, "loadFavoriteCount failed: ${e.message}")
                // keep previous value if failure
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
            val clip = ClipData.newPlainText("AI Prompt", prompt ?: "")
            clipboard.setPrimaryClip(clip)
            // Optionally reflect a short UI hint via state (not implemented here)
        } catch (e: Exception) {
            Log.w(TAG, "copyPromptToClipboard failed: ${e.message}")
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
                            Log.d(TAG, "togglePromptFavorite: ${prompt.id} -> $isNowFavorite")
                            // update prompt entry in uiState.aiPrompts
                            val updated = _uiState.value.aiPrompts.map {
                                if (it.id == prompt.id) it.copy(isFavorite = isNowFavorite) else it
                            }
                            _uiState.value = _uiState.value.copy(aiPrompts = updated)
                            // reload favorites count
                            loadFavoriteCount()
                        },
                        onFailure = { throwable ->
                            // Don't treat cancellation as an error
                            if (throwable is CancellationException) {
                                Log.d(TAG, "togglePromptFavorite cancelled: ${throwable.message}")
                                return@fold
                            }
                            Log.w(TAG, "togglePromptFavorite failed: ${throwable.message}")
                            _uiState.value = _uiState.value.copy(error = throwable.message)
                        }
                    )
                }
            } catch (e: CancellationException) {
                // Proper cancellation handling - don't treat as error
                Log.d(TAG, "togglePromptFavorite cancelled: ${e.message}")
                throw e // Re-throw to let coroutine handle it properly
            } catch (e: Exception) {
                Log.e(TAG, "togglePromptFavorite exception: ${e.message}")
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
     * This method is throttled to avoid rapid repeated calls.
     * Includes proper cancellation handling.
     */
    fun refresh() {
        val now = System.currentTimeMillis()
        if (now - lastRefreshTimestamp < MIN_REFRESH_INTERVAL_MS) {
            val diff = now - lastRefreshTimestamp
            Log.w(TAG, "refresh throttled: only ${diff}ms since last refresh")
            // update UI with friendly message
            _uiState.value = _uiState.value.copy(error = "Please wait a moment before refreshing again.")
            return
        }
        lastRefreshTimestamp = now

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true, error = null)
            try {
                // Run refresh tasks concurrently but keep it simple
                val jobs = listOf(
                    launch { fetchDailyTips() },
                    launch { loadAIPrompts() },
                    launch { loadFavoriteCount() }
                )

                // wait for children to finish
                jobs.forEach { it.join() }
            } catch (e: CancellationException) {
                // Proper cancellation handling - don't treat as error
                Log.d(TAG, "refresh cancelled: ${e.message}")
                throw e // Re-throw to let coroutine handle it properly
            } catch (e: Exception) {
                Log.e(TAG, "refresh exception: ${e.message}")
                _uiState.value = _uiState.value.copy(error = e.message)
            } finally {
                _uiState.value = _uiState.value.copy(isRefreshing = false)
            }
        }
    }

    /**
     * Expose a helper for search input changes (UI should call this).
     * Debounce is handled in init.
     */
    fun onSearchChanged(query: String) {
        _searchQuery.value = query
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    override fun onCleared() {
        super.onCleared()
        // cancel jobs if needed
        dailyTipsJob?.cancel()
        aiPromptsJob?.cancel()
        favoriteCountJob?.cancel()
    }

    /**
     * Called when a prompt is viewed. Currently a no-op placeholder.
     */
    fun logPromptView(promptId: String) {
        viewModelScope.launch {
            try {
                // Optional: if you implement in repository, call it here:
                // repository.incrementView(promptId)
            } catch (e: Exception) {
                // ignore for now
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
                Log.w(TAG, "togglePostLike failed: ${e.message}")
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    /**
     * Share a post: copy text to clipboard and show a toast.
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
            Log.w(TAG, "sharePost failed: ${e.message}")
            _uiState.value = _uiState.value.copy(error = e.message)
        }
    }

}
