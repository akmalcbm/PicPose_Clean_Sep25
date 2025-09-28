package com.picpose.bestphotographyapp.presentation.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.picpose.bestphotographyapp.data.models.AIPrompt
import com.picpose.bestphotographyapp.data.models.GuidePost
import com.picpose.bestphotographyapp.data.repository.HomeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "ExploreViewModel"

// Unified content item for mixed display
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
    FAVORITES("Favorites")
}

enum class ContentFilter(val displayName: String) {
    ALL("All Content"),
    AI_PROMPTS("AI Prompts"),
    GUIDE_POSTS("Guide Posts")
}

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
    
    // Job management for proper cancellation
    private var loadContentJob: Job? = null
    private var searchJob: Job? = null
    
    // Caching mechanism
    private var cachedAIPrompts: List<AIPrompt>? = null
    private var cachedGuidePosts: List<GuidePost>? = null
    private var lastCacheTime = 0L
    private val CACHE_DURATION = 5 * 60 * 1000L // 5 minutes cache
    
    init {
        // Set up search debouncing
        viewModelScope.launch {
            _searchQuery
                .debounce(300)
                .collectLatest { query ->
                    _uiState.value = _uiState.value.copy(searchQuery = query)
                    loadContent(forceRefresh = false)
                }
        }
        
        // Initial load
        loadContent()
        loadCategories()
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateContentFilter(filter: ContentFilter) {
        _uiState.value = _uiState.value.copy(
            selectedContentFilter = filter,
            currentPage = 1
        )
        loadContent(forceRefresh = true)
    }

    fun updateCategory(category: String) {
        _uiState.value = _uiState.value.copy(
            selectedCategory = category,
            currentPage = 1
        )
        loadContent(forceRefresh = true)
    }

    fun updateSortOption(sortOption: SortOption) {
        _uiState.value = _uiState.value.copy(selectedSortOption = sortOption)
        loadContent(forceRefresh = true)
    }

    fun refresh() {
        _uiState.value = _uiState.value.copy(isRefreshing = true, currentPage = 1)
        invalidateCache()
        loadContent(forceRefresh = true)
    }

    fun loadMore() {
        if (_uiState.value.isLoading || !_uiState.value.hasMore) return
        
        val nextPage = _uiState.value.currentPage + 1
        _uiState.value = _uiState.value.copy(currentPage = nextPage)
        loadContent(append = true)
    }

    private fun loadContent(forceRefresh: Boolean = false, append: Boolean = false) {
        loadContentJob?.cancel()
        loadContentJob = viewModelScope.launch {
            try {
                if (!append) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = true,
                        error = null
                    )
                }

                val currentState = _uiState.value
                val shouldLoadAI = currentState.selectedContentFilter in listOf(ContentFilter.ALL, ContentFilter.AI_PROMPTS)
                val shouldLoadGuides = currentState.selectedContentFilter in listOf(ContentFilter.ALL, ContentFilter.GUIDE_POSTS)

                val aiPrompts = if (shouldLoadAI) {
                    loadAIPrompts(forceRefresh, currentState)
                } else emptyList()

                val guidePosts = if (shouldLoadGuides) {
                    loadGuidePosts(forceRefresh, currentState)
                } else emptyList()

                // Combine and sort content
                val mixedContent = combineAndSortContent(aiPrompts, guidePosts, currentState)
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    content = if (append) _uiState.value.content + mixedContent else mixedContent,
                    aiPrompts = aiPrompts,
                    guidePosts = guidePosts,
                    hasMore = mixedContent.isNotEmpty() // Simplified - in real app, check pagination
                )
                
            } catch (e: CancellationException) {
                Log.d(TAG, "loadContent cancelled")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "loadContent error: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    error = e.message
                )
            }
        }
    }

    private suspend fun loadAIPrompts(forceRefresh: Boolean, state: ExploreUiState): List<AIPrompt> {
        val now = System.currentTimeMillis()
        
        // Use cache if available and not forcing refresh
        if (!forceRefresh && cachedAIPrompts != null && (now - lastCacheTime) < CACHE_DURATION) {
            return filterAIPrompts(cachedAIPrompts!!, state)
        }

        return try {
            val category = if (state.selectedCategory == "All") null else state.selectedCategory
            val search = state.searchQuery.ifBlank { null }
            
            var result: List<AIPrompt> = emptyList()
            
            repository.getAiPostsSimple(
                page = state.currentPage,
                limit = 20,
                category = category,
                search = search
            ).collect { apiResult ->
                apiResult.fold(
                    onSuccess = { prompts ->
                        cachedAIPrompts = prompts
                        lastCacheTime = now
                        result = prompts
                    },
                    onFailure = { 
                        Log.w(TAG, "Failed to load AI prompts: ${it.message}")
                        result = emptyList()
                    }
                )
            }
            
            filterAIPrompts(result, state)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading AI prompts: ${e.message}")
            emptyList()
        }
    }

    private suspend fun loadGuidePosts(forceRefresh: Boolean, state: ExploreUiState): List<GuidePost> {
        val now = System.currentTimeMillis()
        
        // Use cache if available and not forcing refresh
        if (!forceRefresh && cachedGuidePosts != null && (now - lastCacheTime) < CACHE_DURATION) {
            return filterGuidePosts(cachedGuidePosts!!, state)
        }

        return try {
            val category = if (state.selectedCategory == "All") null else state.selectedCategory
            val search = state.searchQuery.ifBlank { null }
            
            var result: List<GuidePost> = emptyList()
            
            repository.getGuidePosts(
                page = state.currentPage,
                limit = 20,
                category = category,
                search = search
            ).collect { apiResult ->
                apiResult.fold(
                    onSuccess = { posts ->
                        cachedGuidePosts = posts.data
                        lastCacheTime = now
                        result = posts.data
                    },
                    onFailure = { 
                        Log.w(TAG, "Failed to load guide posts: ${it.message}")
                        result = emptyList()
                    }
                )
            }
            
            filterGuidePosts(result, state)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading guide posts: ${e.message}")
            emptyList()
        }
    }

    private fun filterAIPrompts(prompts: List<AIPrompt>, state: ExploreUiState): List<AIPrompt> {
        return prompts.filter { prompt ->
            val matchesSearch = if (state.searchQuery.isBlank()) {
                true
            } else {
                prompt.title?.contains(state.searchQuery, ignoreCase = true) == true ||
                prompt.fullPrompt?.contains(state.searchQuery, ignoreCase = true) == true
            }
            
            val matchesCategory = if (state.selectedCategory == "All") {
                true
            } else {
                prompt.category == state.selectedCategory
            }
            
            matchesSearch && matchesCategory
        }
    }

    private fun filterGuidePosts(posts: List<GuidePost>, state: ExploreUiState): List<GuidePost> {
        return posts.filter { post ->
            val matchesSearch = if (state.searchQuery.isBlank()) {
                true
            } else {
                post.title?.contains(state.searchQuery, ignoreCase = true) == true ||
                post.content?.contains(state.searchQuery, ignoreCase = true) == true
            }
            
            val matchesCategory = if (state.selectedCategory == "All") {
                true
            } else {
                post.category == state.selectedCategory
            }
            
            matchesSearch && matchesCategory
        }
    }

    private fun combineAndSortContent(
        aiPrompts: List<AIPrompt>,
        guidePosts: List<GuidePost>,
        state: ExploreUiState
    ): List<ExploreContent> {
        val content = mutableListOf<ExploreContent>()
        
        // Convert to ExploreContent
        content.addAll(aiPrompts.map { ExploreContent.AIPromptContent(it) })
        content.addAll(guidePosts.map { ExploreContent.GuidePostContent(it) })
        
        // Sort according to selected option
        return when (state.selectedSortOption) {
            SortOption.NEWEST -> content.sortedByDescending { 
                when (it) {
                    is ExploreContent.AIPromptContent -> it.prompt.createdAt ?: ""
                    is ExploreContent.GuidePostContent -> it.guidePost.createdAt ?: ""
                }
            }
            SortOption.POPULAR -> content.sortedByDescending {
                when (it) {
                    is ExploreContent.AIPromptContent -> it.prompt.isPopular ?: false
                    is ExploreContent.GuidePostContent -> it.guidePost.viewCount ?: 0
                }
            }
            SortOption.FAVORITES -> content.sortedByDescending {
                when (it) {
                    is ExploreContent.AIPromptContent -> it.prompt.isFavorite ?: false
                    is ExploreContent.GuidePostContent -> it.guidePost.isFavorited ?: false
                }
            }
        }
    }

    private fun loadCategories() {
        viewModelScope.launch {
            try {
                repository.getCategories().collect { result ->
                    result.fold(
                        onSuccess = { categories ->
                            val categoryNames = listOf("All") + categories.map { it.name }
                            _uiState.value = _uiState.value.copy(categories = categoryNames)
                        },
                        onFailure = { error ->
                            Log.w(TAG, "Failed to load categories: ${error.message}")
                            // Fallback to default categories
                            val fallbackCategories = listOf("All", "Portrait", "Landscape", "Architecture", "Nature", "Street", "Abstract")
                            _uiState.value = _uiState.value.copy(categories = fallbackCategories)
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading categories: ${e.message}")
                // Fallback to default categories
                val fallbackCategories = listOf("All", "Portrait", "Landscape", "Architecture", "Nature", "Street", "Abstract")
                _uiState.value = _uiState.value.copy(categories = fallbackCategories)
            }
        }
    }

    private fun invalidateCache() {
        cachedAIPrompts = null
        cachedGuidePosts = null
        lastCacheTime = 0L
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun togglePromptFavorite(prompt: AIPrompt) {
        viewModelScope.launch {
            try {
                repository.toggleAIPromptFavorite(prompt.id ?: return@launch).collect { result ->
                    result.fold(
                        onSuccess = { updatedPrompt ->
                            // Update cached data
                            cachedAIPrompts = cachedAIPrompts?.map { 
                                if (it.id == updatedPrompt.id) updatedPrompt else it 
                            }
                            
                            // Update UI state
                            val updatedContent = _uiState.value.content.map { content ->
                                when (content) {
                                    is ExploreContent.AIPromptContent -> {
                                        if (content.prompt.id == updatedPrompt.id) {
                                            ExploreContent.AIPromptContent(updatedPrompt)
                                        } else content
                                    }
                                    else -> content
                                }
                            }
                            
                            _uiState.value = _uiState.value.copy(content = updatedContent)
                        },
                        onFailure = { error ->
                            _uiState.value = _uiState.value.copy(
                                error = "Failed to update favorite: ${error.message}"
                            )
                        }
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Error updating favorite: ${e.message}"
                )
            }
        }
    }

    fun toggleGuidePostFavorite(guidePost: GuidePost) {
        viewModelScope.launch {
            try {
                repository.toggleGuidePostFavorite(guidePost.id ?: return@launch).collect { result ->
                    result.fold(
                        onSuccess = { updatedPost ->
                            // Update cached data
                            cachedGuidePosts = cachedGuidePosts?.map { 
                                if (it.id == updatedPost.id) updatedPost else it 
                            }
                            
                            // Update UI state
                            val updatedContent = _uiState.value.content.map { content ->
                                when (content) {
                                    is ExploreContent.GuidePostContent -> {
                                        if (content.guidePost.id == updatedPost.id) {
                                            ExploreContent.GuidePostContent(updatedPost)
                                        } else content
                                    }
                                    else -> content
                                }
                            }
                            
                            _uiState.value = _uiState.value.copy(content = updatedContent)
                        },
                        onFailure = { error ->
                            _uiState.value = _uiState.value.copy(
                                error = "Failed to update favorite: ${error.message}"
                            )
                        }
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Error updating favorite: ${e.message}"
                )
            }
        }
    }
}