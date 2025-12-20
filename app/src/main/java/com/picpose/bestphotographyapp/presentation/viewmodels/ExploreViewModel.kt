package com.picpose.bestphotographyapp.presentation.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.picpose.bestphotographyapp.data.database.entities.EngagementEntity
import com.picpose.bestphotographyapp.data.models.AIPrompt
import com.picpose.bestphotographyapp.data.models.GuidePost
import com.picpose.bestphotographyapp.data.repository.EngagementLocalRepository
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
    private val exploreRepository: ExploreRepository,
    private val engagementLocalRepo: EngagementLocalRepository
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
        val id = prompt.id ?: return

        viewModelScope.launch {
            engagementLocalRepo.toggleLike(id)

            // 🔁 Server sync already happens inside ExploreRepository
            exploreRepository.togglePromptLike(prompt).collect()

            remergeExploreList()
        }
    }



    fun togglePromptBookmark(prompt: AIPrompt) {
        val id = prompt.id ?: return

        viewModelScope.launch {
            engagementLocalRepo.toggleFavorite(id)

            exploreRepository.togglePromptBookmark(prompt).collect()

            remergeExploreList()
        }
    }

    val localEngagementStates: StateFlow<Map<String, EngagementEntity>> =
        engagementLocalRepo.observeAllStates()   // Flow<List<EngagementEntity>>
            .map { list -> list.associateBy { it.promptId } }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyMap()
            )




    private suspend fun remergeExploreList() {
        val current = _uiState.value.content

        val updated = current.map { item ->
            when (item) {
                is ExploreContent.AIPromptContent -> {
                    val merged =
                        engagementLocalRepo
                            .mergeWithLocalEngagement(listOf(item.prompt))
                            .first()

                    ExploreContent.AIPromptContent(merged)
                }
                else -> item
            }
        }

        _uiState.update { it.copy(content = updated) }
    }

    fun onPromptViewed(promptId: String) {
        viewModelScope.launch {
            engagementLocalRepo.incrementView(promptId)
        }
    }




}
