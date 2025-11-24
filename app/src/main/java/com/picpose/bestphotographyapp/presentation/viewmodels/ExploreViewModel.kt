package com.picpose.bestphotographyapp.presentation.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.picpose.bestphotographyapp.data.models.AIPrompt
import com.picpose.bestphotographyapp.data.models.GuidePost
import com.picpose.bestphotographyapp.data.repository.ExploreRepository
import com.picpose.bestphotographyapp.data.repository.HomeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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

enum class SortOption(val displayName: String) {
    NEWEST("Newest"),
    POPULAR("Popular"),
    MOST_LIKED("Most Liked")
}

enum class ContentFilter(val displayName: String) {
    ALL("All Content"),
    AI_PROMPTS("AI Prompts"),
    GUIDE_POSTS("Guide Posts")
}

data class ExploreUiState(
    val isFirstLoad: Boolean = true,
    val hasEverLoaded: Boolean = false,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val content: List<ExploreContent> = emptyList(),
    val aiPrompts: List<AIPrompt> = emptyList(),
    val guidePosts: List<GuidePost> = emptyList(),
    val categories: List<String> = emptyList(),
    val selectedCategory: String = "All",
    val selectedContentFilter: ContentFilter = ContentFilter.ALL,
    val selectedSortOption: SortOption = SortOption.NEWEST,
    val searchQuery: String = "",
    val error: String? = null,
    val hasMore: Boolean = true,
    val currentPage: Int = 1
) {
    val loadState: ExploreLoadState
        get() = when {
            // Show full-screen shimmer when loading + first load OR loading with no existing content
            isLoading && (isFirstLoad || content.isEmpty()) -> ExploreLoadState.INITIAL

            // Loading while there is existing content -> show list + inline shimmer
            isLoading && content.isNotEmpty() -> ExploreLoadState.SUCCESS

            // Error with no data
            error != null && content.isEmpty() -> ExploreLoadState.ERROR

            // Empty only after we had at least one API attempt
            !isLoading && content.isEmpty() && hasEverLoaded -> ExploreLoadState.EMPTY

            else -> ExploreLoadState.SUCCESS
        }
}

enum class ExploreLoadState { INITIAL, LOADING, SUCCESS, EMPTY, ERROR }

@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val homeRepository: HomeRepository,
    private val exploreRepository: ExploreRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ExploreUiState())
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    private var loadContentJob: Job? = null

    // Cache
    private var cachedAIPrompts: List<AIPrompt>? = null
    private var cachedGuidePosts: List<GuidePost>? = null
    private var lastCacheTime = 0L
    private val CACHE_DURATION = 5 * 60 * 1000L // 5 minutes

    init {
        // start in loading state
        _uiState.update { it.copy(isLoading = true) }

        // default sort
        _uiState.update { it.copy(selectedSortOption = SortOption.NEWEST) }

        // debounced search
        viewModelScope.launch {
            _searchQuery.debounce(300)
                .collectLatest { query ->
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
                selectedCategory = if (resetFilters) "All" else prev.selectedCategory,
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

                val aiPrompts = if (shouldLoadAI) loadAIPrompts(forceRefresh, state) else emptyList()
                val guidePosts = if (shouldLoadGuides) loadGuidePosts(forceRefresh, state) else emptyList()

                val mixed = combineAndSortContent(aiPrompts, guidePosts, state).distinctBy { it.id }

                _uiState.update {
                    val newContent = if (append) (it.content + mixed).distinctBy { c -> c.id } else mixed

                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        content = newContent,
                        aiPrompts = aiPrompts,
                        guidePosts = guidePosts,
                        hasMore = mixed.isNotEmpty(),
                        hasEverLoaded = true,
                        isFirstLoad = false,
                        error = null
                    )
                }

            } catch (e: CancellationException) {
                Log.d(TAG, "Cancelled loadContent")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "loadContent error: ${e.message}")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = e.message,
                        // mark we've attempted a load so EMPTY shows correctly next time
                        hasEverLoaded = true,
                        isFirstLoad = false
                    )
                }
            }
        }
    }

    // ---------- Data loaders (cache-aware) ----------
    private suspend fun loadAIPrompts(forceRefresh: Boolean, state: ExploreUiState): List<AIPrompt> {
        val now = System.currentTimeMillis()
        val limit = 20
        val category = if (state.selectedCategory == "All") null else state.selectedCategory
        val search = state.searchQuery.ifBlank { null }

        // use cache only for first page and when not forcing refresh
        if (!forceRefresh && state.currentPage == 1 && cachedAIPrompts != null && (now - lastCacheTime) < CACHE_DURATION) {
            Log.d(TAG, "Using cached AI prompts (page=${state.currentPage})")
            return filterAIPrompts(cachedAIPrompts!!, state)
        }

        return try {
            var result: List<AIPrompt> = emptyList()
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
                    }
                )
            }

            Log.d(TAG, "Loaded ${result.size} AI prompts (page=${state.currentPage})")
            filterAIPrompts(result, state)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading AI prompts: ${e.message}")
            emptyList()
        }
    }

    private suspend fun loadGuidePosts(forceRefresh: Boolean, state: ExploreUiState): List<GuidePost> {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedGuidePosts != null && (now - lastCacheTime) < CACHE_DURATION) {
            return filterGuidePosts(cachedGuidePosts!!, state)
        }

        return try {
            var result: List<GuidePost> = emptyList()
            homeRepository.getGuidePosts(page = state.currentPage, limit = 20, search = state.searchQuery.ifBlank { null })
                .collect { apiResult ->
                    apiResult.fold(
                        onSuccess = { data ->
                            cachedGuidePosts = data.items
                            lastCacheTime = now
                            result = data.items
                        },
                        onFailure = { Log.w(TAG, "Guide posts load failed: ${it.message}") }
                    )
                }

            filterGuidePosts(result, state)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading guide posts: ${e.message}")
            emptyList()
        }
    }

    // ---------- filter / combine ----------
    private fun filterAIPrompts(prompts: List<AIPrompt>, state: ExploreUiState): List<AIPrompt> {
        return prompts.filter { prompt ->
            val matchesSearch = state.searchQuery.isBlank() ||
                    prompt.title?.contains(state.searchQuery, true) == true ||
                    prompt.fullPrompt?.contains(state.searchQuery, true) == true ||
                    prompt.shortPrompt?.contains(state.searchQuery, true) == true

            val matchesCategory = state.selectedCategory == "All" ||
                    prompt.category.equals(state.selectedCategory, ignoreCase = true)

            matchesSearch && matchesCategory
        }
    }

    private fun filterGuidePosts(posts: List<GuidePost>, state: ExploreUiState): List<GuidePost> {
        return posts.filter { post ->
            val matchesSearch = state.searchQuery.isBlank() ||
                    post.title?.contains(state.searchQuery, true) == true ||
                    post.content?.contains(state.searchQuery, true) == true

            val matchesCategory = state.selectedCategory == "All" ||
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
                            _uiState.update { it.copy(categories = listOf("All") + cats.map { c -> c.name }) }
                        },
                        onFailure = {
                            _uiState.update { it.copy(categories = listOf("All", "Portrait", "Nature", "Street", "Abstract")) }
                        }
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(categories = listOf("All", "Portrait", "Nature", "Street", "Abstract")) }
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

    fun togglePromptLike(prompt: AIPrompt) {
        viewModelScope.launch {
            exploreRepository.togglePromptLike(prompt).collect { result ->
                result.onSuccess { updated ->
                    updatePromptInList(updated)
                }
            }
        }
    }




    fun togglePromptBookmark(prompt: AIPrompt) {
        viewModelScope.launch {
            exploreRepository.togglePromptBookmark(prompt).collect { result ->
                result.onSuccess { updated ->
                    updatePromptInList(updated)
                }
            }
        }
    }



    /*
    // favorites toggles (update content & cache)
    fun togglePromptFavorite(prompt: AIPrompt) {
        viewModelScope.launch {
            repository.toggleAIPromptFavorite(prompt ?: return@launch).collect { result ->
                result.onSuccess { updated ->
                    cachedAIPrompts = cachedAIPrompts?.map { if (it.id == updated.id) updated else it }
                    _uiState.update {
                        it.copy(content = it.content.map { c ->
                            if (c is ExploreContent.AIPromptContent && c.prompt.id == updated.id)
                                ExploreContent.AIPromptContent(updated) else c
                        })
                    }
                }
            }
        }
    }
    */

    fun updatePromptInList(updated: AIPrompt) {
        _uiState.update { state ->
            state.copy(
                content = state.content.map { item ->
                    if (item is ExploreContent.AIPromptContent && item.prompt.id == updated.id) {
                        ExploreContent.AIPromptContent(updated)
                    } else item
                }
            )
        }
    }


}



/*

package com.picpose.bestphotographyapp.presentation.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.picpose.bestphotographyapp.data.models.AIPrompt
import com.picpose.bestphotographyapp.data.models.GuidePost
import com.picpose.bestphotographyapp.data.repository.HomeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "ExploreViewModel"

// 🔹 Unified content item for mixed display
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

// 🔹 Sorting & Content filter options
enum class SortOption(val displayName: String) {
    NEWEST("Newest"),
    POPULAR("Popular"),
    MOST_LIKED("Most Liked")
}

enum class ContentFilter(val displayName: String) {
    ALL("All Content"),
    AI_PROMPTS("AI Prompts"),
    GUIDE_POSTS("Guide Posts")
}

// 🔹 Explore UI State
data class ExploreUiState(
    val isFirstLoad: Boolean = true,
    val hasEverLoaded: Boolean = false,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val content: List<ExploreContent> = emptyList(),
    val aiPrompts: List<AIPrompt> = emptyList(),
    val guidePosts: List<GuidePost> = emptyList(),
    val categories: List<String> = emptyList(),
    val selectedCategory: String = "All",
    val selectedContentFilter: ContentFilter = ContentFilter.ALL,
    val selectedSortOption: SortOption = SortOption.NEWEST,
    val searchQuery: String = "",
    val error: String? = null,
    val hasMore: Boolean = true,
    val currentPage: Int = 1
) {

    val loadState: ExploreLoadState
        get() = when {

            // FIRST APP LOAD
            isFirstLoad && isLoading -> ExploreLoadState.INITIAL

            // ANY REFRESH OR NEW FILTERS → SHOW SHIMMER
            isLoading && content.isEmpty() -> ExploreLoadState.INITIAL

            // PAGINATION (inline shimmer)
            isLoading && content.isNotEmpty() -> ExploreLoadState.SUCCESS

            // ERROR + NO DATA
            error != null && content.isEmpty() -> ExploreLoadState.ERROR

            // EMPTY ONLY WHEN:
            // 1. Not loading
            // 2. No content
            // 3. First load already completed
            // 4. NOT refreshing
            !isLoading && content.isEmpty() && hasEverLoaded && !isRefreshing ->
                ExploreLoadState.EMPTY

            else -> ExploreLoadState.SUCCESS
        }




}


enum class ExploreLoadState {
    INITIAL,
    LOADING,
    SUCCESS,
    EMPTY,
    ERROR
}



@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val repository: HomeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExploreUiState())
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    private var loadContentJob: Job? = null

    // 🔹 Cache
    private var cachedAIPrompts: List<AIPrompt>? = null
    private var cachedGuidePosts: List<GuidePost>? = null
    private var lastCacheTime = 0L
    private val CACHE_DURATION = 5 * 60 * 1000L // 5 min

    init {
        // 👇 Force loading state immediately on launch
        _uiState.update { it.copy(isLoading = true) }

        // Default sort
        _uiState.update { it.copy(selectedSortOption = SortOption.NEWEST) }

        // 🔹 Debounced Search
        viewModelScope.launch {
            _searchQuery.debounce(300)
                .collectLatest { query ->
                    _uiState.update { it.copy(searchQuery = query, currentPage = 1) }
                    loadContent(forceRefresh = false)
                }
        }

        // Initial load
        loadContent()
        loadCategories()
    }

    // -----------------------------------------------
    // 🔹 UI Update Handlers
    // -----------------------------------------------
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        invalidateCache()
    }

    fun updateContentFilter(filter: ContentFilter) {
        invalidateCache()
        _uiState.update {
            it.copy(selectedContentFilter = filter, currentPage = 1, content = emptyList())
        }
        loadContent(forceRefresh = true)
    }

    fun updateCategory(category: String) {
        // ✅ Reset cache + content for category change
        invalidateCache()
        _uiState.update {
            it.copy(
                selectedCategory = category,
                currentPage = 1,
                content = emptyList(),
                hasMore = true
            )
        }
        loadContent(forceRefresh = true)
    }

    fun updateSortOption(sortOption: SortOption) {
        _uiState.update { it.copy(selectedSortOption = sortOption, currentPage = 1) }
        invalidateCache()
        loadContent(forceRefresh = true)
    }

    fun refresh() {
        _uiState.update {
            it.copy(
                isRefreshing = true,
                isLoading = true,   // ⭐ This prevents flicker
                content = emptyList(),
                currentPage = 1
            )
        }
        invalidateCache()
        loadContent(forceRefresh = true)
    }



    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || !state.hasMore) return
        _uiState.update { it.copy(currentPage = it.currentPage + 1) }
        loadContent(append = true)
    }

    // -----------------------------------------------
    // 🔹 Core Loader
    // -----------------------------------------------
    private fun loadContent(forceRefresh: Boolean = false, append: Boolean = false) {
        loadContentJob?.cancel()
        loadContentJob = viewModelScope.launch {
            try {
                if (!append) _uiState.update { it.copy(isLoading = true, content = it.content) }

                val state = _uiState.value
                val shouldLoadAI = state.selectedContentFilter in listOf(ContentFilter.ALL, ContentFilter.AI_PROMPTS)
                val shouldLoadGuides = state.selectedContentFilter in listOf(ContentFilter.ALL, ContentFilter.GUIDE_POSTS)

                val aiPrompts = if (shouldLoadAI) loadAIPrompts(forceRefresh, state) else emptyList()
                val guidePosts = if (shouldLoadGuides) loadGuidePosts(forceRefresh, state) else emptyList()

                val mixed = combineAndSortContent(aiPrompts, guidePosts, state).distinctBy { it.id }

                _uiState.update {
                    val newContent = if (append)
                        (it.content + mixed).distinctBy { c -> c.id }
                    else
                        mixed

                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        content = newContent,
                        aiPrompts = aiPrompts,
                        guidePosts = guidePosts,
                        hasMore = mixed.isNotEmpty(),
                        hasEverLoaded = true,
                        isFirstLoad = false
                    )
                }

            } catch (e: CancellationException) {
                Log.d(TAG, "Cancelled loadContent")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "loadContent error: ${e.message}")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = e.message,
                        hasEverLoaded = true,
                        isFirstLoad = false   // ADD THIS
                    )

                }
            }
        }
    }


    // -----------------------------------------------
    // 🔹 Load AI Prompts
    // -----------------------------------------------
    private suspend fun loadAIPrompts(forceRefresh: Boolean, state: ExploreUiState): List<AIPrompt> {
        val now = System.currentTimeMillis()
        val limit = 20
        val category = if (state.selectedCategory == "All") null else state.selectedCategory
        val search = state.searchQuery.ifBlank { null }

        // ✅ Skip cache when scrolling (page > 1)
        if (!forceRefresh && state.currentPage == 1 && cachedAIPrompts != null && (now - lastCacheTime) < CACHE_DURATION) {
            Log.d(TAG, "Using cached AI prompts (page=${state.currentPage})")
            return filterAIPrompts(cachedAIPrompts!!, state)
        }

        return try {
            var result: List<AIPrompt> = emptyList()

            val flow = when (state.selectedSortOption) {
                SortOption.NEWEST -> repository.getAiPostsSimple(
                    page = state.currentPage,
                    limit = limit,
                    category = category,
                    search = search
                )
                SortOption.POPULAR -> repository.getTrendingAiPosts(
                    limit = limit,
                    offset = (state.currentPage - 1) * limit
                )
                SortOption.MOST_LIKED -> repository.getMostLikedAiPosts(
                    limit = limit,
                    offset = (state.currentPage - 1) * limit
                )
            }

            flow.collect { apiResult ->
                apiResult.fold(
                    onSuccess = { prompts ->
                        // ✅ Sort by newest first
                        val sorted = prompts.sortedByDescending { it.createdAt ?: "" }

                        // ✅ Append new items if paginating, else reset
                        result = if (state.currentPage > 1 && !forceRefresh) {
                            val combined = (cachedAIPrompts.orEmpty() + sorted)
                            combined.distinctBy { it.id } // remove duplicates
                        } else {
                            sorted
                        }

                        // ✅ Update cache only for first page
                        if (state.currentPage == 1) {
                            cachedAIPrompts = result
                            lastCacheTime = now
                        }
                    },
                    onFailure = { e ->
                        Log.w(TAG, "Failed to load AI prompts: ${e.message}")
                    }
                )
            }

            Log.d(TAG, "Loaded ${result.size} AI prompts (page=${state.currentPage})")
            filterAIPrompts(result, state)

        } catch (e: Exception) {
            Log.e(TAG, "Error loading AI prompts: ${e.message}")
            emptyList()
        }
    }



    // -----------------------------------------------
    // 🔹 Load Guide Posts
    // -----------------------------------------------
    private suspend fun loadGuidePosts(forceRefresh: Boolean, state: ExploreUiState): List<GuidePost> {
        val now = System.currentTimeMillis()

        if (!forceRefresh && cachedGuidePosts != null && (now - lastCacheTime) < CACHE_DURATION) {
            return filterGuidePosts(cachedGuidePosts!!, state)
        }

        return try {
            var result: List<GuidePost> = emptyList()
            repository.getGuidePosts(
                page = state.currentPage,
                limit = 20,
                search = state.searchQuery.ifBlank { null }
            ).collect { apiResult ->
                apiResult.fold(
                    onSuccess = { data ->
                        cachedGuidePosts = data.items
                        lastCacheTime = now
                        result = data.items
                    },
                    onFailure = { Log.w(TAG, "Guide posts load failed: ${it.message}") }
                )
            }

            filterGuidePosts(result, state)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading guide posts: ${e.message}")
            emptyList()
        }
    }

    // -----------------------------------------------
    // 🔹 Filtering & Sorting
    // -----------------------------------------------
    private fun filterAIPrompts(prompts: List<AIPrompt>, state: ExploreUiState): List<AIPrompt> {
        return prompts.filter { prompt ->
            val matchesSearch = state.searchQuery.isBlank() ||
                    prompt.title?.contains(state.searchQuery, true) == true ||
                    prompt.fullPrompt?.contains(state.searchQuery, true) == true ||
                    prompt.shortPrompt?.contains(state.searchQuery, true) == true

            val matchesCategory =
                state.selectedCategory == "All" ||
                        prompt.category.equals(state.selectedCategory, ignoreCase = true)

            matchesSearch && matchesCategory
        }
    }

    private fun filterGuidePosts(posts: List<GuidePost>, state: ExploreUiState): List<GuidePost> {
        return posts.filter { post ->
            val matchesSearch = state.searchQuery.isBlank() ||
                    post.title?.contains(state.searchQuery, true) == true ||
                    post.content?.contains(state.searchQuery, true) == true

            val matchesCategory =
                state.selectedCategory == "All" ||
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

    // -----------------------------------------------
    // 🔹 Categories, Cache, Favorites
    // -----------------------------------------------
    private fun loadCategories() {
        viewModelScope.launch {
            try {
                repository.getCategories().collect { result ->
                    result.fold(
                        onSuccess = { cats ->
                            _uiState.update {
                                it.copy(categories = listOf("All") + cats.map { c -> c.name })
                            }
                        },
                        onFailure = {
                            _uiState.update {
                                it.copy(categories = listOf("All", "Portrait", "Nature", "Street", "Abstract"))
                            }
                        }
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(categories = listOf("All", "Portrait", "Nature", "Street", "Abstract"))
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

    fun togglePromptFavorite(prompt: AIPrompt) {
        viewModelScope.launch {
            repository.toggleAIPromptFavorite(prompt.id ?: return@launch).collect { result ->
                result.onSuccess { updated ->
                    cachedAIPrompts = cachedAIPrompts?.map { if (it.id == updated.id) updated else it }
                    _uiState.update {
                        it.copy(content = it.content.map { c ->
                            if (c is ExploreContent.AIPromptContent && c.prompt.id == updated.id)
                                ExploreContent.AIPromptContent(updated) else c
                        })
                    }
                }
            }
        }
    }

    fun toggleGuidePostFavorite(guidePost: GuidePost) {
        viewModelScope.launch {
            repository.toggleGuidePostFavorite(guidePost.id ?: return@launch).collect { result ->
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
}
*/
