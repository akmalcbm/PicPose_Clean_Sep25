/**
 * ---
 * File: ExploreViewModel.kt
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

package com.picpose.bestphotographyapp.presentation.viewmodels

import android.util.Log
import androidx.annotation.StringRes
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.picpose.bestphotographyapp.core.analytics.AnalyticsLogger
import com.picpose.bestphotographyapp.core.crash.CrashReporter
import com.picpose.bestphotographyapp.R
import com.picpose.bestphotographyapp.data.database.entities.EngagementEntity
import com.picpose.bestphotographyapp.data.models.AIPrompt
import com.picpose.bestphotographyapp.data.models.GuidePost
import com.picpose.bestphotographyapp.data.repository.EngagementRepository
import com.picpose.bestphotographyapp.data.repository.HomeRepository
import com.picpose.bestphotographyapp.presentation.search.SearchMatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "ExploreViewModel"

// Unified content
sealed class ExploreContent {
    data class AIPromptContent(val prompt: AIPrompt) : ExploreContent()
    data class GuidePostContent(val guidePost: GuidePost) : ExploreContent()

    val id: String get() = when (this) {
        is AIPromptContent -> prompt.id ?: ""
        is GuidePostContent -> guidePost.id ?: ""
    }

    val title: String get() = when (this) {
        is AIPromptContent -> prompt.title ?: "Untitled"
        is GuidePostContent -> guidePost.title ?: "Untitled"
    }
}

enum class SortOption(@StringRes val labelRes: Int) {
    NEWEST(R.string.newest),
    POPULAR(R.string.popular),
    MOST_LIKED(R.string.most_liked)
}

enum class ContentFilter(@StringRes val labelRes: Int) {
    ALL(R.string.all_content),
    AI_PROMPTS(R.string.ai_prompts),
    GUIDE_POSTS(R.string.guide_posts)
}

data class ExploreUiState(
    val hasLoadedOnce: Boolean = false,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val content: List<ExploreContent> = emptyList(),
    val aiPrompts: List<AIPrompt> = emptyList(),
    val guidePosts: List<GuidePost> = emptyList(),
    val categories: List<String> = emptyList(),
    val selectedCategory: String = "",
    val selectedContentFilter: ContentFilter = ContentFilter.ALL,
    val selectedSortOption: SortOption = SortOption.NEWEST,
    val searchQuery: String = "",
    val error: String? = null,
    val hasMore: Boolean = true,
    val currentPage: Int = 1
) {
    val loadState: ExploreLoadState
        get() = when {
            // Initial open or first request in progress
            isLoading && !hasLoadedOnce && content.isEmpty() -> ExploreLoadState.INITIAL

            // Loading while there is existing content -> show list + inline shimmer
            isLoading && content.isNotEmpty() -> ExploreLoadState.SUCCESS

            // Error with no data
            !isLoading && error != null && content.isEmpty() -> ExploreLoadState.ERROR

            // Empty only after first request completed successfully
            !isLoading && hasLoadedOnce && error == null && content.isEmpty() -> ExploreLoadState.EMPTY

            else -> ExploreLoadState.SUCCESS
        }
}

enum class ExploreLoadState { INITIAL, LOADING, SUCCESS, EMPTY, ERROR }

@OptIn(FlowPreview::class)
@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val homeRepository: HomeRepository,
    private val engagementRepository: EngagementRepository,
    private val analyticsLogger: AnalyticsLogger,
    private val crashReporter: CrashReporter,
    @ApplicationContext private val appContext: Context
) : ViewModel() {
    private val _uiState = MutableStateFlow(ExploreUiState())
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

    private val allCategoryLabel: String by lazy { appContext.getString(R.string.all) }
    private val _searchQuery = MutableStateFlow("")
    private var lastTrackedSearchQuery: String? = null
    private var loadContentJob: Job? = null
    private data class LoadSlice<T>(val data: List<T>, val failed: Boolean)

    // Cache
    private var cachedAIPrompts: List<AIPrompt>? = null
    private var cachedGuidePosts: List<GuidePost>? = null
    private var lastCacheTime = 0L
    private val CACHE_DURATION = 5 * 60 * 1000L // 5 minutes

    init {
        // default sort
        _uiState.update {
            it.copy(
                selectedSortOption = SortOption.NEWEST,
                selectedCategory = allCategoryLabel
            )
        }

        // debounced search
        viewModelScope.launch {
            _searchQuery.debounce(300)
                .collectLatest { query ->
                    val normalized = query.trim()
                    if (normalized.isNotBlank() && normalized != lastTrackedSearchQuery) {
                        lastTrackedSearchQuery = normalized
                        analyticsLogger.logSearchPerformed(normalized.length)
                    }
                    _uiState.update { it.copy(searchQuery = query, currentPage = 1) }
                    loadContent(forceRefresh = true)
                }
        }

        // initial data
        loadContent()
        loadCategories()
    }

    // ---------- UI handlers (do NOT clear content immediately; keep visible during refresh) ----------
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        invalidateCache()
        _uiState.update { it.copy(searchQuery = query, currentPage = 1) }
        loadContent(forceRefresh = true)
    }

    fun updateContentFilter(filter: ContentFilter) {
        invalidateCache()
        _uiState.update { it.copy(selectedContentFilter = filter, currentPage = 1, hasMore = true) }
        loadContent(forceRefresh = true)
    }

    fun updateCategory(category: String) {
        invalidateCache()
        _uiState.update { it.copy(selectedCategory = category, currentPage = 1, hasMore = true) }
        loadContent(forceRefresh = true)
    }

    fun updateSortOption(sortOption: SortOption) {
        invalidateCache()
        _uiState.update { it.copy(selectedSortOption = sortOption, currentPage = 1, hasMore = true) }
        loadContent(forceRefresh = true)
    }

    /**
     * refresh(resetFilters = true) will:
     *  - optionally reset search/category/filter/sort (atomically)
     *  - set isRefreshing + isLoading true so UI shows spinner/shimmers
     *  - IMPORTANT: we do NOT clear `content` immediately -> prevents "No content" flash
     */
    fun refresh(resetFilters: Boolean = false) {
        invalidateCache()
        _uiState.update { prev ->
            prev.copy(
                isRefreshing = true,
                isLoading = true,
                currentPage = 1,
                // only reset filters if requested; keep content to avoid flicker
                searchQuery = if (resetFilters) "" else prev.searchQuery,
                selectedCategory = if (resetFilters) allCategoryLabel else prev.selectedCategory,
                selectedContentFilter = if (resetFilters) ContentFilter.ALL else prev.selectedContentFilter,
                selectedSortOption = if (resetFilters) SortOption.NEWEST else prev.selectedSortOption,
                hasMore = true
            )
        }
        loadContent(forceRefresh = true)
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || !state.hasMore) return
        _uiState.update { it.copy(currentPage = it.currentPage + 1) }
        loadContent(append = true)
    }

    // ---------- Core loader ----------
    private fun loadContent(forceRefresh: Boolean = false, append: Boolean = false) {
        loadContentJob?.cancel()
        loadContentJob = viewModelScope.launch {
            try {
                // when not paginating, show loading (do not clear existing content here)
                if (!append) _uiState.update { it.copy(isLoading = true) }

                val state = _uiState.value
                val shouldLoadAI = state.selectedContentFilter in listOf(ContentFilter.ALL, ContentFilter.AI_PROMPTS)
                val shouldLoadGuides = state.selectedContentFilter in listOf(ContentFilter.ALL, ContentFilter.GUIDE_POSTS)

                val aiSlice = if (shouldLoadAI) loadAIPrompts(forceRefresh, state) else LoadSlice(emptyList(), failed = false)
                val guideSlice = if (shouldLoadGuides) loadGuidePosts(forceRefresh, state) else LoadSlice(emptyList(), failed = false)

                val aiPrompts = aiSlice.data
                val guidePosts = guideSlice.data

                val mixed = combineAndSortContent(aiPrompts, guidePosts, state).distinctBy { it.id }
                val anySourceFailed = aiSlice.failed || guideSlice.failed
                val hasAnyData = mixed.isNotEmpty()
                val shouldShowError = anySourceFailed && !hasAnyData

                _uiState.update {
                    val newContent = if (append) (it.content + mixed).distinctBy { c -> c.id } else mixed
                    val errorMessage = if (shouldShowError) {
                        appContext.getString(R.string.error)
                    } else {
                        null
                    }

                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        content = newContent,
                        aiPrompts = aiPrompts,
                        guidePosts = guidePosts,
                        hasMore = mixed.isNotEmpty(),
                        hasLoadedOnce = true,
                        error = errorMessage
                    )
                }

            } catch (e: CancellationException) {
                Log.d(TAG, "Cancelled loadContent")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "loadContent error: ${e.message}")
                crashReporter.recordUnexpectedNetworkFailure("explore_load_content", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = e.message,
                        hasLoadedOnce = true
                    )
                }
            }
        }
    }

    // ---------- Data loaders (cache-aware) ----------
    private suspend fun loadAIPrompts(forceRefresh: Boolean, state: ExploreUiState): LoadSlice<AIPrompt> {
        val now = System.currentTimeMillis()
        val limit = 20
        val category = if (state.selectedCategory == allCategoryLabel) null else state.selectedCategory
        val search = state.searchQuery.ifBlank { null }

        // use cache only for first page and when not forcing refresh
        if (!forceRefresh && state.currentPage == 1 && cachedAIPrompts != null && (now - lastCacheTime) < CACHE_DURATION) {
            Log.d(TAG, "Using cached AI prompts (page=${state.currentPage})")
            return LoadSlice(data = filterAIPrompts(cachedAIPrompts!!, state), failed = false)
        }

        return try {
            var result: List<AIPrompt> = emptyList()
            var failed = false
            val flow = when (state.selectedSortOption) {
                SortOption.NEWEST -> homeRepository.getAiPostsSimple(page = state.currentPage, limit = limit, category = category, search = search)
                SortOption.POPULAR -> homeRepository.getTrendingAiPosts(limit = limit, offset = (state.currentPage - 1) * limit)
                SortOption.MOST_LIKED -> homeRepository.getMostLikedAiPosts(limit = limit, offset = (state.currentPage - 1) * limit)
            }

            flow.collect { apiResult ->
                apiResult.fold(
                    onSuccess = { prompts ->
                        val sorted = prompts.sortedByDescending { it.createdAt ?: "" }
                        result = if (state.currentPage > 1 && !forceRefresh) {
                            (cachedAIPrompts.orEmpty() + sorted).distinctBy { it.id }
                        } else {
                            sorted
                        }
                        if (state.currentPage == 1) {
                            cachedAIPrompts = result
                            lastCacheTime = now
                        }
                    },
                    onFailure = { e ->
                        Log.w(TAG, "Failed to load AI prompts: ${e.message}")
                        failed = true
                    }
                )
            }

            Log.d(TAG, "Loaded ${result.size} AI prompts (page=${state.currentPage})")
            LoadSlice(data = filterAIPrompts(result, state), failed = failed)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading AI prompts: ${e.message}")
            LoadSlice(data = emptyList(), failed = true)
        }
    }

    private suspend fun loadGuidePosts(forceRefresh: Boolean, state: ExploreUiState): LoadSlice<GuidePost> {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedGuidePosts != null && (now - lastCacheTime) < CACHE_DURATION) {
            return LoadSlice(data = filterGuidePosts(cachedGuidePosts!!, state), failed = false)
        }

        return try {
            var result: List<GuidePost> = emptyList()
            var failed = false
            homeRepository.getGuidePosts(page = state.currentPage, limit = 20, search = state.searchQuery.ifBlank { null })
                .collect { apiResult ->
                    apiResult.fold(
                        onSuccess = { data ->
                            cachedGuidePosts = data.items
                            lastCacheTime = now
                            result = data.items
                        },
                        onFailure = {
                            failed = true
                            Log.w(TAG, "Guide posts load failed: ${it.message}")
                        }
                    )
                }

            LoadSlice(data = filterGuidePosts(result, state), failed = failed)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading guide posts: ${e.message}")
            LoadSlice(data = emptyList(), failed = true)
        }
    }

    // ---------- filter / combine ----------
    private fun filterAIPrompts(prompts: List<AIPrompt>, state: ExploreUiState): List<AIPrompt> {
        return prompts.filter { prompt ->
            val matchesSearch = SearchMatchers.matchesAIPrompt(prompt, state.searchQuery)

            val matchesCategory = state.selectedCategory == allCategoryLabel ||
                    prompt.category.equals(state.selectedCategory, ignoreCase = true)

            matchesSearch && matchesCategory
        }
    }

    private fun filterGuidePosts(posts: List<GuidePost>, state: ExploreUiState): List<GuidePost> {
        return posts.filter { post ->
            val matchesSearch = SearchMatchers.matchesGuidePost(post, state.searchQuery)

            val matchesCategory = state.selectedCategory == allCategoryLabel ||
                    post.category.equals(state.selectedCategory, ignoreCase = true)

            matchesSearch && matchesCategory
        }
    }

    private fun combineAndSortContent(
        aiPrompts: List<AIPrompt>,
        guidePosts: List<GuidePost>,
        state: ExploreUiState
    ): List<ExploreContent> {
        val combined = mutableListOf<ExploreContent>()
        combined.addAll(aiPrompts.map { ExploreContent.AIPromptContent(it) })
        combined.addAll(guidePosts.map { ExploreContent.GuidePostContent(it) })

        return when (state.selectedSortOption) {
            SortOption.NEWEST -> combined.sortedByDescending {
                when (it) {
                    is ExploreContent.AIPromptContent -> it.prompt.createdAt
                    is ExploreContent.GuidePostContent -> it.guidePost.createdAt
                }
            }
            SortOption.POPULAR -> combined.sortedByDescending {
                when (it) {
                    is ExploreContent.AIPromptContent -> (it.prompt.likes ?: 0) + (it.prompt.favorites ?: 0)
                    is ExploreContent.GuidePostContent -> it.guidePost.viewCount ?: 0
                }
            }
            SortOption.MOST_LIKED -> combined.sortedByDescending {
                when (it) {
                    is ExploreContent.AIPromptContent -> it.prompt.likes ?: 0
                    is ExploreContent.GuidePostContent -> it.guidePost.viewCount ?: 0
                }
            }
        }
    }

    // categories + cache helpers
    private fun loadCategories() {
        viewModelScope.launch {
            try {
                homeRepository.getCategories().collect { result ->
                    result.fold(
                        onSuccess = { cats ->
                            _uiState.update { it.copy(categories = listOf(allCategoryLabel) + cats.map { c -> c.name }) }
                        },
                        onFailure = {
                            _uiState.update {
                                it.copy(
                                    categories = listOf(
                                        allCategoryLabel,
                                        appContext.getString(R.string.category_portrait),
                                        appContext.getString(R.string.category_nature),
                                        appContext.getString(R.string.category_street),
                                        appContext.getString(R.string.category_abstract)
                                    )
                                )
                            }
                        }
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        categories = listOf(
                            allCategoryLabel,
                            appContext.getString(R.string.category_portrait),
                            appContext.getString(R.string.category_nature),
                            appContext.getString(R.string.category_street),
                            appContext.getString(R.string.category_abstract)
                        )
                    )
                }
            }
        }
    }

    private fun invalidateCache() {
        cachedAIPrompts = null
        cachedGuidePosts = null
        lastCacheTime = 0L
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }


    fun toggleGuidePostFavorite(guidePost: GuidePost) {
        viewModelScope.launch {
            homeRepository.toggleGuidePostFavorite(guidePost.id ?: return@launch).collect { result ->
                result.onSuccess { updated ->
                    cachedGuidePosts = cachedGuidePosts?.map { if (it.id == updated.id) updated else it }
                    _uiState.update {
                        it.copy(content = it.content.map { c ->
                            if (c is ExploreContent.GuidePostContent && c.guidePost.id == updated.id)
                                ExploreContent.GuidePostContent(updated) else c
                        })
                    }
                }
            }
        }
    }

    /* ---------------------------------------------------- */
    /* SIMPLIFIED ENGAGEMENT HANDLERS */
    /* ---------------------------------------------------- */

    fun togglePromptLike(prompt: AIPrompt) {
        val promptId = prompt.id ?: return

        viewModelScope.launch {
            try {
                // Use centralized handler with current like count
                val result = engagementRepository.handleLike(promptId, prompt.likes)

                // Update UI state
                updatePromptInState(promptId) { currentPrompt ->
                    currentPrompt.copy(
                        isLiked = result.isLiked,
                        likes = result.newLikes
                    )
                }
                analyticsLogger.logPromptLike(promptId)

                Log.d(TAG, "✅ Like toggled: $promptId, liked: ${result.isLiked}, newLikes: ${result.newLikes}")

            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to toggle like: ${e.message}")
                crashReporter.recordUnexpectedNetworkFailure("explore_toggle_prompt_like", e)
            }
        }
    }

    fun togglePromptBookmark(prompt: AIPrompt) {
        val promptId = prompt.id ?: return

        viewModelScope.launch {
            try {
                // Use centralized handler with current favorite count
                val result = engagementRepository.handleBookmark(promptId, prompt.favorites)

                // Update UI state
                updatePromptInState(promptId) { currentPrompt ->
                    currentPrompt.copy(
                        isFavouriteBookmarked = result.isBookmarked,
                        favorites = result.newFavorites
                    )
                }

                Log.d(TAG, "✅ Bookmark toggled: $promptId, bookmarked: ${result.isBookmarked}, newFavorites: ${result.newFavorites}")

            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to toggle bookmark: ${e.message}")
            }
        }
    }

    /* ---------------------------------------------------- */
    /* HELPER FUNCTIONS */
    /* ---------------------------------------------------- */

    private fun updatePromptInState(
        promptId: String,
        transform: (AIPrompt) -> AIPrompt
    ) {
        _uiState.update { currentState ->
            val updatedContent = currentState.content.map { content ->
                if (content is ExploreContent.AIPromptContent && content.prompt.id == promptId) {
                    ExploreContent.AIPromptContent(transform(content.prompt))
                } else {
                    content
                }
            }

            val updatedPrompts = currentState.aiPrompts.map { prompt ->
                if (prompt.id == promptId) transform(prompt) else prompt
            }

            currentState.copy(
                content = updatedContent,
                aiPrompts = updatedPrompts
            )
        }
    }

    val localEngagementStates: StateFlow<Map<String, EngagementEntity>> =
        engagementRepository.observeAllStates()   // Flow<List<EngagementEntity>>
            .map { list -> list.associateBy { it.promptId } }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyMap()
            )

}
