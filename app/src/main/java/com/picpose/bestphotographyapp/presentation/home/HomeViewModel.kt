/**
 * ---
 * File: HomeViewModel.kt
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

package com.picpose.bestphotographyapp.presentation.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.picpose.bestphotographyapp.core.analytics.AnalyticsLogger
import com.picpose.bestphotographyapp.core.crash.CrashReporter
import com.picpose.bestphotographyapp.R
import com.picpose.bestphotographyapp.data.local.database.entity.EngagementEntity
import com.picpose.bestphotographyapp.data.remote.dto.AIPrompt
import com.picpose.bestphotographyapp.data.remote.dto.Category
import com.picpose.bestphotographyapp.data.remote.dto.DailyTip
import com.picpose.bestphotographyapp.data.remote.dto.GuidePost
import com.picpose.bestphotographyapp.data.remote.dto.Post
import com.picpose.bestphotographyapp.data.repository.EngagementRepository
import com.picpose.bestphotographyapp.data.repository.HomeRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.system.measureTimeMillis

private const val TAG = "HomeViewModel"

data class HomeUiState(
    val isLoading: Boolean = false,
    val trendingPosts: List<Post> = emptyList(),
    val featuredPosts: List<Post> = emptyList(),
    val popularPosts: List<Post> = emptyList(), // ✅ NEW
    val isTrendingLoading: Boolean = true,
    val isFeaturedLoading: Boolean = true,
    val isPopularLoading: Boolean = true,
    val trendingError: String? = null,
    val featuredError: String? = null,
    val popularError: String? = null,
    val trendingLoadedOnce: Boolean = false,
    val featuredLoadedOnce: Boolean = false,
    val popularLoadedOnce: Boolean = false,
    val selectedTab: HomeTab = HomeTab.Trending,
    val recentPosts: List<Post> = emptyList(),
    val isRecentPostsLoading: Boolean = true,
    val recentPostsError: String? = null,
    val recentPostsLoadedOnce: Boolean = false,
    val categories: List<Category> = emptyList(),
    val isCategoriesLoading: Boolean = true,
    val categoriesError: String? = null,
    val categoriesLoadedOnce: Boolean = false,
    val aiPrompts: List<AIPrompt> = emptyList(),
    val guidePosts: List<GuidePost> = emptyList(),
    val isGuideLoading: Boolean = true,
    val guideError: String? = null,
    val guideLoadedOnce: Boolean = false,
    val favoritePromptsCount: Int = 0,
    val dailyTips: List<DailyTip> = emptyList(),
    val error: String? = null,
    val isRefreshing: Boolean = false
) {
    // Pull-to-refresh waits for these sections because they represent the core landing content.
    val isAnyCriticalLoading: Boolean
        get() = isTrendingLoading ||
            isFeaturedLoading ||
            isPopularLoading ||
            isCategoriesLoading ||
            isRecentPostsLoading ||
            isGuideLoading

    val hasAnyCriticalError: Boolean
        get() = !trendingError.isNullOrBlank() ||
            !featuredError.isNullOrBlank() ||
            !popularError.isNullOrBlank() ||
            !categoriesError.isNullOrBlank() ||
            !recentPostsError.isNullOrBlank() ||
            !guideError.isNullOrBlank()
}

enum class HomeTab { Trending, Featured, Popular } // ✅ UPDATED

@OptIn(FlowPreview::class)
@HiltViewModel
/**
 * State owner for the Home screen.
 *
 * Compose collects [uiState] and [searchQuery] to render the landing feed. This
 * ViewModel coordinates section loading, search debouncing, lightweight caching,
 * and engagement updates while keeping the screen composable declarative.
 *
 * MVVM flow:
 * HomeScreen -> HomeViewModel -> HomeRepository/EngagementRepository -> Room/API
 */
class HomeViewModel @Inject constructor (
    @ApplicationContext private val appContext: Context,
    private val repository: HomeRepository,
    private val engagementRepository: EngagementRepository,
    private val analyticsLogger: AnalyticsLogger,
    private val crashReporter: CrashReporter
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    /**
     * Single observable state object for the whole Home screen.
     * Updating one section here causes Compose to recompose only readers of that state.
     */
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // Local photography tips used as fallback (keeps UX stable)
    private val photographyTips =
        appContext.resources.getStringArray(R.array.photography_tips_fallback).toList()

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
        // Debounce search input so typing does not trigger a request for every character.
        viewModelScope.launch {
            _searchQuery
                .debounce(400) // 400ms idle before search executes
                .collectLatest { q ->
                    Log.d(TAG, "Debounced search query: '$q'")
                    // If blank, call without search param (loads default list)
                    loadAIPrompts(page = 1, limit = 12, category = null, search = q.ifBlank { null })
                }
        }

        // Startup work is split so the debounced search collector and initial prompt
        // request do not race each other.
        fetchDailyTips()
        loadGuidePosts()
        loadFavoriteCount()

        // ✅ NEW
        loadCategories()
        loadRecentPosts()

        loadTrendingFeaturedAndPopularPosts()
        
        // Load AI prompts only after a brief delay to avoid conflict with search debouncing
        viewModelScope.launch {
            kotlinx.coroutines.delay(100) // Brief delay to let search flow initialize
            if (_searchQuery.value.isBlank()) {
                loadAIPrompts()
            }
        }
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

    private fun finishRefreshingWhenDone() {
        viewModelScope.launch {
            while (_uiState.value.isAnyCriticalLoading) {
                kotlinx.coroutines.delay(120)
            }
            // Refresh ends only after the primary content blocks finish loading.
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }
    fun fetchDailyTips() {
        // A short in-memory cache avoids repeatedly reloading the same tip payload
        // when users bounce around the app.
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
        // This method can be called by initial load and by debounced search updates,
        // so we guard against overlapping requests.
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
            val clip = ClipData.newPlainText(context.getString(R.string.ai_prompt_label), prompt ?: "")
            clipboard.setPrimaryClip(clip)
            // Optionally reflect a short UI hint via state (not implemented here)
        } catch (e: Exception) {
            Log.w(TAG, "copyPromptToClipboard failed: ${e.message}")
            _uiState.value = _uiState.value.copy(error = e.message)
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
    fun refreshHome() {
        val now = System.currentTimeMillis()
        if (now - lastRefreshTimestamp < MIN_REFRESH_INTERVAL_MS) {
            val diff = now - lastRefreshTimestamp
            Log.w(TAG, "refresh throttled: only ${diff}ms since last refresh")
            _uiState.value = _uiState.value.copy(error = appContext.getString(R.string.please_wait_before_refreshing_again))
            return
        }
        lastRefreshTimestamp = now

        _uiState.value = _uiState.value.copy(isRefreshing = true, error = null)
        fetchDailyTips()
        loadAIPrompts()
        loadGuidePosts()
        loadFavoriteCount()
        loadCategories()
        loadRecentPosts()
        loadTrendingFeaturedAndPopularPosts()
        finishRefreshingWhenDone()
    }

    fun refresh() {
        refreshHome()
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

    fun onPostLikeClicked(postId: String) {
        viewModelScope.launch {

            val isNowLiked = engagementRepository.toggleLike(postId)
            if (isNowLiked) {
                analyticsLogger.logPromptLike(postId)
            }

            _uiState.update { state ->
                state.copy(
                    recentPosts = state.recentPosts.map { post ->
                        if (post.id == postId) {
                            post.copy(
                                likes = if (isNowLiked)
                                    post.likes + 1
                                else
                                    (post.likes - 1).coerceAtLeast(0)
                            )
                        } else post
                    }
                )
            }

            // fire & forget
            repository.incrementLike(postId.toInt())
        }
    }

    /**
     * Share a post: copy text to clipboard and show a toast.
     */
    fun sharePost(context: Context, post: Post) {
        try {
            val shareText = buildString {
                append(context.getString(R.string.share_post_intro, post.title))
                append("\n\n")
                append(post.description ?: "")
                append("\n\n")
                append(context.getString(R.string.share_hashtag_photography))
            }

            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(context.getString(R.string.shared_post_label), shareText)
            clipboard.setPrimaryClip(clip)

            Toast.makeText(context, context.getString(R.string.post_details_copied_to_clipboard), Toast.LENGTH_SHORT).show()

            // Optional: analytics or server call to record share
            viewModelScope.launch {
                try {
                    // repository.recordShare(post.id)
                    analyticsLogger.logSharePrompt(post.id)
                } catch (_: Exception) { /* ignore */ }
            }
        } catch (e: Exception) {
            Log.w(TAG, "sharePost failed: ${e.message}")
            crashReporter.recordUnexpectedNetworkFailure("home_share_prompt", e)
            _uiState.value = _uiState.value.copy(error = e.message)
        }
    }

    // -------------------------
    // GUIDE POSTS
    // -------------------------
    
    private var guidePostsJob: Job? = null
    private var cachedGuidePosts: List<GuidePost>? = null
    
    /**
     * Load guide posts from repository
     */
    fun loadGuidePosts(page: Int = 1, limit: Int = 10, featured: Boolean? = null, status: String? = "published") {
        Log.d(TAG, "loadGuidePosts: starting with page=$page, limit=$limit, featured=$featured, status=$status")
        guidePostsJob?.cancel()
        guidePostsJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isGuideLoading = true,
                guideError = null
            )
            try {
                repository.getGuidePosts(page = page, limit = limit, featured = featured, status = status)
                    .collect { result ->
                        result.fold(
                            onSuccess = { pag ->
                                cachedGuidePosts = pag.items
                                _uiState.value = _uiState.value.copy(
                                    guidePosts = pag.items,
                                    isGuideLoading = false,
                                    guideError = null,
                                    guideLoadedOnce = true
                                )
                            },
                            onFailure = { err ->
                                Log.e(TAG, "loadGuidePosts: failed with error: ${err.message}")
                                _uiState.value = _uiState.value.copy(
                                    guidePosts = cachedGuidePosts ?: emptyList(),
                                    isGuideLoading = false,
                                    guideError = err.message ?: appContext.getString(R.string.failed_to_load_guide_posts),
                                    guideLoadedOnce = true
                                )
                            }
                        )
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "loadGuidePosts: exception: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    guidePosts = cachedGuidePosts ?: emptyList(),
                    isGuideLoading = false,
                    guideError = e.message ?: appContext.getString(R.string.failed_to_load_guide_posts),
                    guideLoadedOnce = true
                )
            }
        }
    }



    // small helper to toggle like for guide posts (optimistic)
    fun toggleGuidePostLike(guidePostId: String) {
        viewModelScope.launch {
            try {
                val updated = _uiState.value.guidePosts.map { gp ->
                    if (gp.id == guidePostId) {
                        val nowLiked = !gp.isLiked
                        gp.copy(
                            likes = if (nowLiked) gp.likes + 1 else (gp.likes - 1).coerceAtLeast(0),
                            isLiked = nowLiked
                        )
                    } else gp
                }
                _uiState.value = _uiState.value.copy(guidePosts = updated)
                // Optional: call repository to persist server-side
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(guideError = e.message)
            }
        }
    }

    /**
     * Share a guide post: copy text to clipboard and show a toast
     */
    fun shareGuidePost(context: Context, guidePost: GuidePost) {
        try {
            val guideLink = guidePost.imageUrl.takeIf { it.startsWith("http://") || it.startsWith("https://") }
            val shareText = buildString {
                append(guidePost.title.ifBlank { context.getString(R.string.guide_posts) })
                val body = guidePost.excerpt.ifBlank { guidePost.description.ifBlank { guidePost.content.take(180) } }
                if (body.isNotBlank()) append("\n\n$body")
                if (!guideLink.isNullOrBlank()) append("\n\n$guideLink")
                append("\n\n#PicPose #Guide")
            }

            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, guidePost.title.ifBlank { context.getString(R.string.guide_posts) })
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            context.startActivity(Intent.createChooser(sendIntent, context.getString(R.string.share)))

            // Optional: analytics or server call to record share
            viewModelScope.launch {
                try {
                    // repository.recordGuidePostShare(guidePost.id)
                    analyticsLogger.logShareGuide(guidePost.id)
                } catch (_: Exception) { /* ignore */ }
            }
        } catch (e: Exception) {
            Log.w(TAG, "shareGuidePost failed: ${e.message}")
            crashReporter.recordUnexpectedNetworkFailure("home_share_guide", e)
            _uiState.value = _uiState.value.copy(guideError = e.message)
        }
    }

    // --------------------------------------------
// 🔥 NEW CODE for Categories & Recent Posts
// --------------------------------------------
    fun loadCategories() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isCategoriesLoading = true,
                categoriesError = null
            )
            try {
                Log.d(TAG, "loadCategories: fetching categories...")
                repository.getCategories().collect { result ->
                    result.fold(
                        onSuccess = { categories ->
                            Log.d(TAG, "loadCategories: received ${categories.size} categories")
                            _uiState.value = _uiState.value.copy(
                                categories = categories,
                                isCategoriesLoading = false,
                                categoriesError = null,
                                categoriesLoadedOnce = true
                            )
                        },
                        onFailure = { err ->
                            Log.e(TAG, "loadCategories failed: ${err.message}")
                            _uiState.value = _uiState.value.copy(
                                isCategoriesLoading = false,
                                categoriesError = err.message ?: appContext.getString(R.string.failed_to_load_categories),
                                categoriesLoadedOnce = true
                            )
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadCategories exception: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    isCategoriesLoading = false,
                    categoriesError = e.message ?: appContext.getString(R.string.failed_to_load_categories),
                    categoriesLoadedOnce = true
                )
            }
        }
    }

    fun loadRecentPosts(limit: Int = 5) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isRecentPostsLoading = true,
                recentPostsError = null
            )

            repository.getLatestRecent5AiPosts(limit).collect { result ->
                result.fold(
                    onSuccess = { aiPrompts ->
                        val posts = aiPrompts.map { aiPrompt ->
                            Post(
                                id = aiPrompt.id ?: "",
                                title = aiPrompt.title ?: appContext.getString(R.string.untitled),
                                description = aiPrompt.shortPrompt
                                    ?: aiPrompt.fullPrompt
                                    ?: "",
                                image = aiPrompt.imageUrl ?: "",
                                category = aiPrompt.category ?: "",
                                createdAt = aiPrompt.createdAt ?: "",
                                likes = aiPrompt.likes ?: 0,
                                favorites = aiPrompt.favorites ?: 0,
                                views = aiPrompt.views ?: 0,
                                isPopular = aiPrompt.isPopular ?: false,
                                isFeatured = aiPrompt.isFeatured ?: false
                            )
                        }

                        _uiState.update {
                            it.copy(
                                recentPosts = posts,
                                isRecentPostsLoading = false,
                                recentPostsError = null,
                                recentPostsLoadedOnce = true
                            )
                        }
                    },
                    onFailure = { err ->
                        _uiState.update {
                            it.copy(
                                isRecentPostsLoading = false,
                                recentPostsError = err.message ?: appContext.getString(R.string.failed_to_load_prompts),
                                recentPostsLoadedOnce = true
                            )
                        }
                    }
                )
            }
        }
    }



    /**
     * Load Trending, Featured, and Popular posts for Home Screen
     * Optimized for parallel safe fetching and clean UI updates
     */
    fun loadTrendingFeaturedAndPopularPosts(limit: Int = 10) {
        viewModelScope.launch {
            Log.d(TAG, "🔄 Starting loadTrendingFeaturedAndPopularPosts (limit=$limit)...")
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
                isTrendingLoading = true,
                isFeaturedLoading = true,
                isPopularLoading = true,
                trendingError = null,
                featuredError = null,
                popularError = null
            )

            try {
                // Launch all three fetches concurrently
                val trendingJob = launch {
                    repository.getTrendingAiPosts(limit = limit).collect { result ->
                        result.fold(
                            onSuccess = { aiPrompts ->
                                val trending = aiPrompts.map { aiPrompt ->
                                    Post(
                                        id = aiPrompt.id ?: "",
                                        title = aiPrompt.title ?: "Untitled",
                                        description = aiPrompt.shortPrompt ?: aiPrompt.fullPrompt ?: "",
                                        image = aiPrompt.imageUrl ?: "",
                                        category = aiPrompt.category ?: "",
                                        createdAt = aiPrompt.createdAt ?: "",
                                        likes = aiPrompt.likes ?: 0,
                                        favorites = aiPrompt.favorites ?: 0,
                                        views = aiPrompt.views ?: 0,
                                        isPopular = aiPrompt.isPopular ?: false,
                                        isFeatured = aiPrompt.isFeatured ?: false
                                    )
                                }.sortedByDescending { (it.likes + it.favorites) }
                                _uiState.value = _uiState.value.copy(
                                    trendingPosts = trending,
                                    isTrendingLoading = false,
                                    trendingError = null,
                                    trendingLoadedOnce = true
                                )
                                Log.d(TAG, "✅ Loaded ${trending.size} trending posts")
                            },
                            onFailure = { err ->
                                Log.e(TAG, "❌ Failed to load trending posts: ${err.message}")
                                _uiState.value = _uiState.value.copy(
                                    isTrendingLoading = false,
                                    trendingError = err.message ?: appContext.getString(R.string.no_posts_available),
                                    trendingLoadedOnce = true
                                )
                            }
                        )
                    }
                }

                val featuredJob = launch {
                    repository.getFeaturedAiPosts(limit = limit).collect { result ->
                        result.fold(
                            onSuccess = { aiPrompts ->
                                val featured = aiPrompts.map { aiPrompt ->
                                    Post(
                                        id = aiPrompt.id ?: "",
                                        title = aiPrompt.title ?: "Untitled",
                                        description = aiPrompt.shortPrompt ?: aiPrompt.fullPrompt ?: "",
                                        image = aiPrompt.imageUrl ?: "",
                                        category = aiPrompt.category ?: "",
                                        createdAt = aiPrompt.createdAt ?: "",
                                        likes = aiPrompt.likes ?: 0,
                                        favorites = aiPrompt.favorites ?: 0,
                                        views = aiPrompt.views ?: 0,
                                        isPopular = aiPrompt.isPopular ?: false,
                                        isFeatured = true
                                    )
                                }.sortedByDescending { it.createdAt } // sort newest featured first
                                _uiState.value = _uiState.value.copy(
                                    featuredPosts = featured,
                                    isFeaturedLoading = false,
                                    featuredError = null,
                                    featuredLoadedOnce = true
                                )
                                Log.d(TAG, "✅ Loaded ${featured.size} featured posts")
                            },
                            onFailure = { err ->
                                Log.e(TAG, "❌ Failed to load featured posts: ${err.message}")
                                _uiState.value = _uiState.value.copy(
                                    isFeaturedLoading = false,
                                    featuredError = err.message ?: appContext.getString(R.string.no_posts_available),
                                    featuredLoadedOnce = true
                                )
                            }
                        )
                    }
                }

                val popularJob = launch {
                    repository.getPopularAiPosts(limit = limit).collect { result ->
                        result.fold(
                            onSuccess = { aiPrompts ->
                                val popular = aiPrompts.map { aiPrompt ->
                                    Post(
                                        id = aiPrompt.id ?: "",
                                        title = aiPrompt.title ?: "Untitled",
                                        description = aiPrompt.shortPrompt ?: aiPrompt.fullPrompt ?: "",
                                        image = aiPrompt.imageUrl ?: "",
                                        category = aiPrompt.category ?: "",
                                        createdAt = aiPrompt.createdAt ?: "",
                                        likes = aiPrompt.likes ?: 0,
                                        favorites = aiPrompt.favorites ?: 0,
                                        views = aiPrompt.views ?: 0,
                                        isPopular = true,
                                        isFeatured = aiPrompt.isFeatured ?: false
                                    )
                                }.sortedByDescending { it.views } // sort most viewed first
                                _uiState.value = _uiState.value.copy(
                                    popularPosts = popular,
                                    isPopularLoading = false,
                                    popularError = null,
                                    popularLoadedOnce = true
                                )
                                Log.d(TAG, "✅ Loaded ${popular.size} popular posts")
                            },
                            onFailure = { err ->
                                Log.e(TAG, "❌ Failed to load popular posts: ${err.message}")
                                _uiState.value = _uiState.value.copy(
                                    isPopularLoading = false,
                                    popularError = err.message ?: appContext.getString(R.string.no_posts_available),
                                    popularLoadedOnce = true
                                )
                            }
                        )
                    }
                }

                // Wait for all to complete
                listOf(trendingJob, featuredJob, popularJob).forEach { it.join() }

            } catch (e: Exception) {
                Log.e(TAG, "🔥 loadTrendingFeaturedAndPopularPosts exception: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    isTrendingLoading = false,
                    isFeaturedLoading = false,
                    isPopularLoading = false,
                    trendingError = e.message ?: appContext.getString(R.string.no_posts_available),
                    featuredError = e.message ?: appContext.getString(R.string.no_posts_available),
                    popularError = e.message ?: appContext.getString(R.string.no_posts_available),
                    trendingLoadedOnce = true,
                    featuredLoadedOnce = true,
                    popularLoadedOnce = true
                )
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun refreshTrending() {
        viewModelScope.launch {
            _uiState.update { it.copy(isTrendingLoading = true, trendingError = null) }
            repository.getTrendingAiPosts(limit = 10).collect { result ->
                result.fold(
                    onSuccess = { list ->
                        _uiState.update {
                            it.copy(
                                trendingPosts = list.map { ai -> ai.toPost() },
                                isTrendingLoading = false,
                                trendingError = null,
                                trendingLoadedOnce = true
                            )
                        }
                    },
                    onFailure = { err ->
                        _uiState.update {
                            it.copy(
                                isTrendingLoading = false,
                                trendingError = err.message ?: appContext.getString(R.string.no_posts_available),
                                trendingLoadedOnce = true
                            )
                        }
                    }
                )
            }
        }
    }

    fun refreshFeatured() {
        viewModelScope.launch {
            _uiState.update { it.copy(isFeaturedLoading = true, featuredError = null) }
            repository.getFeaturedAiPosts(limit = 10).collect { result ->
                result.fold(
                    onSuccess = { list ->
                        _uiState.update {
                            it.copy(
                                featuredPosts = list.map { ai -> ai.toPost().copy(isFeatured = true) },
                                isFeaturedLoading = false,
                                featuredError = null,
                                featuredLoadedOnce = true
                            )
                        }
                    },
                    onFailure = { err ->
                        _uiState.update {
                            it.copy(
                                isFeaturedLoading = false,
                                featuredError = err.message ?: appContext.getString(R.string.no_posts_available),
                                featuredLoadedOnce = true
                            )
                        }
                    }
                )
            }
        }
    }

    fun refreshPopular() {
        viewModelScope.launch {
            _uiState.update { it.copy(isPopularLoading = true, popularError = null) }
            repository.getPopularAiPosts(limit = 10).collect { result ->
                result.fold(
                    onSuccess = { list ->
                        _uiState.update {
                            it.copy(
                                popularPosts = list.map { ai -> ai.toPost().copy(isPopular = true) },
                                isPopularLoading = false,
                                popularError = null,
                                popularLoadedOnce = true
                            )
                        }
                    },
                    onFailure = { err ->
                        _uiState.update {
                            it.copy(
                                isPopularLoading = false,
                                popularError = err.message ?: appContext.getString(R.string.no_posts_available),
                                popularLoadedOnce = true
                            )
                        }
                    }
                )
            }
        }
    }

    fun refreshGuides() = loadGuidePosts()
    fun refreshCategories() = loadCategories()
    fun refreshRecentPosts() = loadRecentPosts()

    val localEngagementStates: StateFlow<Map<String, EngagementEntity>> =
        engagementRepository
            .observeAllStates()              // 🔥 LIVE FLOW
            .map { list ->
                list.associateBy { it.promptId }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyMap()
            )




    /**
     * 🔹 Updates the selected tab (Trending / Featured / Popular)
     */
    fun updateSelectedTab(tab: HomeTab) {
        _uiState.value = _uiState.value.copy(selectedTab = tab)
    }


    fun loadMorePostsForCategory(category: String, currentSize: Int) {
        val nextOffset = currentSize
        viewModelScope.launch {
            val flow = when (category) {
                "Trending" -> repository.getTrendingAiPosts(limit = 10, offset = nextOffset)
                "Featured" -> repository.getFeaturedAiPosts(limit = 10, offset = nextOffset)
                "Popular"  -> repository.getPopularAiPosts(limit = 10, offset = nextOffset)
                else       -> null // ✅ makes the when exhaustive
            }

            // only collect if valid
            flow?.collect { result ->
                result.onSuccess { newPosts ->
                    val oldPosts = when (category) {
                        "Trending" -> _uiState.value.trendingPosts
                        "Featured" -> _uiState.value.featuredPosts
                        "Popular"  -> _uiState.value.popularPosts
                        else       -> emptyList()
                    }
                    val merged = oldPosts + newPosts.map { it.toPost() }

                    _uiState.value = when (category) {
                        "Trending" -> _uiState.value.copy(trendingPosts = merged)
                        "Featured" -> _uiState.value.copy(featuredPosts = merged)
                        "Popular"  -> _uiState.value.copy(popularPosts = merged)
                        else       -> _uiState.value
                    }
                }

                result.onFailure { err ->
                    Log.e(TAG, "loadMorePostsForCategory($category) failed: ${err.message}")
                    _uiState.value = _uiState.value.copy(error = err.message)
                }
            }
        }
    }


    // ✅ Convert AIPrompt -> Post in one place
    private fun com.picpose.bestphotographyapp.data.remote.dto.AIPrompt.toPost(): com.picpose.bestphotographyapp.data.remote.dto.Post =
        com.picpose.bestphotographyapp.data.remote.dto.Post(
            id         = this.id ?: "",
            title      = this.title ?: "Untitled",
            description= this.shortPrompt ?: this.fullPrompt ?: "",
            image      = this.imageUrl ?: "",
            category   = this.category ?: "",
            createdAt  = this.createdAt ?: "",
            likes      = this.likes ?: 0,
            favorites  = this.favorites ?: 0,
            views      = this.views ?: 0,
            isPopular  = this.isPopular ?: false,
            isFeatured = this.isFeatured ?: false
        )

}
