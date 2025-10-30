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

// 🔹 UI State
data class ExploreUiState(
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
)

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
        // Default sort is always NEWEST
        _uiState.value = _uiState.value.copy(selectedSortOption = SortOption.NEWEST)

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
        _uiState.update { it.copy(isRefreshing = true, currentPage = 1) }
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
                if (!append) _uiState.update { it.copy(isLoading = true, error = null) }

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
                        hasMore = mixed.isNotEmpty() // ✅ Determines if more pages exist
                    )
                }

            } catch (e: CancellationException) {
                Log.d(TAG, "Cancelled loadContent")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "loadContent error: ${e.message}")
                _uiState.update { it.copy(isLoading = false, isRefreshing = false, error = e.message) }
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
