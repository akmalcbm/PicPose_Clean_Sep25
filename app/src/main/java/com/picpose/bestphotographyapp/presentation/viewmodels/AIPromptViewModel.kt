package com.picpose.bestphotographyapp.presentation.viewmodels

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.picpose.bestphotographyapp.data.models.AIPrompt
import com.picpose.bestphotographyapp.data.repository.HomeRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.system.measureTimeMillis

private const val TAG = "AIPromptVM"

data class AIPromptUiState(
    val isLoading: Boolean = false,
    val allPrompts: List<AIPrompt> = emptyList(),
    val favoritePrompts: List<AIPrompt> = emptyList(),
    val featuredPrompts: List<AIPrompt> = emptyList(),
    val categories: List<String> = emptyList(),
    val selectedCategory: String = "All",
    val searchQuery: String = "",
    val error: String? = null,
    val isRefreshing: Boolean = false
)

@RequiresApi(Build.VERSION_CODES.O)
class AIPromptViewModel(private val repository: HomeRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(AIPromptUiState())
    val uiState: StateFlow<AIPromptUiState> = _uiState.asStateFlow()

    // derived state: favorites (eagerly kept)
    val favoritePrompts: StateFlow<List<AIPrompt>> = uiState
        .map { it.favoritePrompts }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    // Search debounce flow (UI should call onSearchChanged)
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    // concurrency guard for loadAllPrompts
    @Volatile
    private var isLoadingAll: Boolean = false

    // refresh throttle
    private var lastRefreshTimestamp = 0L
    private val MIN_REFRESH_INTERVAL_MS = 3_000L // 3s

    // keep reference to any running job if needed
    private var loadAllJob: Job? = null
    private var loadFavoritesJob: Job? = null

    init {
        // debounce search queries
        viewModelScope.launch {
            _searchQuery
                .debounce(400)
                .collectLatest { q ->
                    Log.d(TAG, "Debounced search -> '$q'")
                    // update state searchQuery and invoke load
                    _uiState.value = _uiState.value.copy(searchQuery = q)
                    // If blank, pass null so repository returns default list
                    loadAllPrompts(page = 1, limit = 100, category = null, search = q.ifBlank { null })
                }
        }

        // initial loads
        loadAllPrompts()
        loadFavoritePrompts()
        loadCategories()
    }

    // ✅ Add these at the top of AIPromptViewModel
    private var cacheTimestamp = 0L
    private val CACHE_DURATION = 5 * 60 * 1000L // 5 minutes

    /**
     * Check if cache is still valid
     */
    private fun isCacheValid(): Boolean {
        return (System.currentTimeMillis() - cacheTimestamp) < CACHE_DURATION
    }

    /**
     * Load all prompts with caching
     */
    fun loadAllPrompts(page: Int = 1, limit: Int = 100, category: String? = null, search: String? = null) {
        // ✅ Skip if already loading or cache is valid
        if (isLoadingAll) {
            Log.w(TAG, "loadAllPrompts skipped - already loading")
            return
        }

        if (search.isNullOrBlank() && category.isNullOrBlank() && isCacheValid() && _uiState.value.allPrompts.isNotEmpty()) {
            Log.d(TAG, "loadAllPrompts skipped - cache is still valid")
            return
        }

        // ✅ Update cache timestamp when loading
        cacheTimestamp = System.currentTimeMillis()
    }


    /**
     * Toggle favorite state via repository and update UI lists locally.
     */
    fun toggleFavorite(prompt: AIPrompt) {
        viewModelScope.launch {
            try {
                val flow = repository.toggleFavorite(prompt)
                flow.collect { result: Result<Boolean> ->
                    result.fold(
                        onSuccess = { isNowFavorite: Boolean ->
                            // Update allPrompts list
                            val updatedAll = _uiState.value.allPrompts.map { p ->
                                if (p.id == prompt.id) p.copy(isFavorite = isNowFavorite) else p
                            }

                            // Update favorite list accordingly
                            val updatedFavorites = if (isNowFavorite) {
                                (_uiState.value.favoritePrompts + prompt.copy(isFavorite = true))
                                    .distinctBy { it.id }
                            } else {
                                _uiState.value.favoritePrompts.filter { it.id != prompt.id }
                            }

                            _uiState.value = _uiState.value.copy(
                                allPrompts = updatedAll,
                                favoritePrompts = updatedFavorites
                            )
                        },
                        onFailure = { ex: Throwable ->
                            Log.w(TAG, "toggleFavorite failed: ${ex.message}")
                            _uiState.value = _uiState.value.copy(error = ex.message ?: "Failed to toggle favorite")
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "toggleFavorite exception: ${e.message}")
                _uiState.value = _uiState.value.copy(error = e.message ?: "Exception toggling favorite")
            }
        }
    }

    /**
     * Load favorites from repository and mark them in allPrompts
     */
    fun loadFavoritePrompts() {
        loadFavoritesJob?.cancel()
        loadFavoritesJob = viewModelScope.launch {
            try {
                val flow = repository.getFavoritePrompts()
                flow.collect { result: Result<List<AIPrompt>> ->
                    result.fold(
                        onSuccess = { favs: List<AIPrompt> ->
                            val safeFavs = favs.map { it.copy(isFavorite = true) }
                            _uiState.value = _uiState.value.copy(favoritePrompts = safeFavs)
                            // Update isFavorite flags in allPrompts
                            val favIds = safeFavs.map { it.id }.toSet()
                            val updatedAll = _uiState.value.allPrompts.map { p ->
                                p.copy(isFavorite = favIds.contains(p.id))
                            }
                            _uiState.value = _uiState.value.copy(allPrompts = updatedAll)
                        },
                        onFailure = { ex: Throwable ->
                            Log.w(TAG, "loadFavoritePrompts failed: ${ex.message}")
                            _uiState.value = _uiState.value.copy(error = ex.message ?: "Failed to load favorites")
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadFavoritePrompts exception: ${e.message}")
                _uiState.value = _uiState.value.copy(error = e.message ?: "Exception loading favorites")
            }
        }
    }

    /**
     * Refresh favorite state by collecting favorite prompts once and updating local flags.
     */
    fun refreshFavoriteState() {
        viewModelScope.launch {
            try {
                var favIds = emptySet<String>()
                val flow = repository.getFavoritePrompts()
                flow.collect { result: Result<List<AIPrompt>> ->
                    result.onSuccess { favs ->
                        favIds = favs.map { it.id }.toSet()
                    }
                    // ignore onFailure for this short refresh
                }
                val updated = _uiState.value.allPrompts.map { it.copy(isFavorite = favIds.contains(it.id)) }
                _uiState.value = _uiState.value.copy(allPrompts = updated)
            } catch (e: Exception) {
                Log.e(TAG, "refreshFavoriteState exception: ${e.message}")
                _uiState.value = _uiState.value.copy(error = e.message ?: "Exception refreshing favorites")
            }
        }
    }

    /**
     * Refresh all prompt data (throttled).
     */
    fun refresh() {
        val now = System.currentTimeMillis()
        if (now - lastRefreshTimestamp < MIN_REFRESH_INTERVAL_MS) {
            Log.w(TAG, "refresh throttled: only ${now - lastRefreshTimestamp}ms since last refresh")
            _uiState.value = _uiState.value.copy(error = "Please wait a moment before refreshing again.")
            return
        }
        lastRefreshTimestamp = now

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true, error = null)
            try {
                // refresh favorites and all prompts
                launch { loadFavoritePrompts() }.join()
                launch { loadAllPrompts() }.join()
            } catch (e: Exception) {
                Log.e(TAG, "refresh exception: ${e.message}")
                _uiState.value = _uiState.value.copy(error = e.message ?: "Exception during refresh")
            } finally {
                _uiState.value = _uiState.value.copy(isRefreshing = false)
            }
        }
    }

    /**
     * Load categories from current prompts. If you want server categories, call repository.getCategories()
     */
    fun loadCategories() {
        viewModelScope.launch {
            try {
                updateCategoriesFromPrompts(_uiState.value.allPrompts)
            } catch (e: Exception) {
                Log.e(TAG, "loadCategories exception: ${e.message}")
                _uiState.value = _uiState.value.copy(error = e.message ?: "Exception loading categories")
            }
        }
    }

    private fun updateCategoriesFromPrompts(prompts: List<AIPrompt>) {
        val cats = listOf("All") + prompts
            .mapNotNull { it.category?.takeIf { c -> c.isNotBlank() } }
            .distinct()
            .sorted()
        _uiState.value = _uiState.value.copy(categories = cats)
    }

    /**
     * Load single prompt by id - FIXED VERSION
     */
    fun loadPromptById(promptId: String) {
        viewModelScope.launch {
            try {
                // ✅ First check if prompt already exists in cache
                val existing = _uiState.value.allPrompts.find { it.id == promptId }
                if (existing != null) {
                    Log.d(TAG, "loadPromptById: found in cache, no API call needed")
                    return@launch // ✅ Exit early - no API call needed!
                }

                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                Log.d(TAG, "loadPromptById: $promptId - not in cache, making API call")

                // ✅ Try to get single prompt first (if you have this API endpoint)
                var foundPrompt: AIPrompt? = null

                try {
                    // If you have a single prompt endpoint like: /api/get_ai_post.php?id={promptId}
                    // Use it here instead of loading all prompts
                    val flow = repository.getPromptById(promptId)
                    flow.collect { result ->
                        result.fold(
                            onSuccess = { prompt ->
                                foundPrompt = prompt
                                Log.d(TAG, "loadPromptById: found single prompt via API")
                            },
                            onFailure = { ex ->
                                Log.w(TAG, "loadPromptById: single API failed: ${ex.message}")
                            }
                        )
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "loadPromptById: single API exception: ${e.message}")
                }

                if (foundPrompt != null) {
                    // ✅ Add to existing list without replacing
                    val updated = _uiState.value.allPrompts + foundPrompt!!
                    _uiState.value = _uiState.value.copy(
                        allPrompts = updated.distinctBy { it.id }, // Remove duplicates
                        isLoading = false
                    )
                } else {
                    // ✅ Only fallback to loadAllPrompts if absolutely necessary
                    Log.w(TAG, "loadPromptById: falling back to loadAllPrompts (not ideal)")
                    loadAllPrompts(page = 1, limit = 50) // ✅ Reduced limit
                }

            } catch (e: Exception) {
                Log.e(TAG, "loadPromptById exception: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Failed to load prompt details",
                    isLoading = false
                )
            }
        }
    }

    /**
     * Simple search that filters currently loaded prompts.
     * Note: prefer using onSearchChanged() + debounce which triggers loadAllPrompts via API.
     */
    fun searchPrompts(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        if (query.isBlank()) {
            loadAllPrompts()
            return
        }
        viewModelScope.launch {
            try {
                val filtered = _uiState.value.allPrompts.filter { p ->
                    val title = p.title ?: ""
                    val short = p.shortPrompt ?: ""
                    val categoryContains = p.category?.contains(query, ignoreCase = true) ?: false
                    val tags = p.tags ?: emptyList()
                    (title.contains(query, ignoreCase = true)
                            || short.contains(query, ignoreCase = true)
                            || categoryContains
                            || tags.any { it.contains(query, ignoreCase = true) })
                }
                _uiState.value = _uiState.value.copy(allPrompts = filtered)
            } catch (e: Exception) {
                Log.e(TAG, "searchPrompts exception: ${e.message}")
                _uiState.value = _uiState.value.copy(error = e.message ?: "Exception")
            }
        }
    }

    fun filterByCategory(category: String) {
        _uiState.value = _uiState.value.copy(selectedCategory = category)
        if (category == "All") {
            loadAllPrompts()
            return
        }
        viewModelScope.launch {
            try {
                val filtered = _uiState.value.allPrompts.filter { it.category == category }
                _uiState.value = _uiState.value.copy(allPrompts = filtered)
            } catch (e: Exception) {
                Log.e(TAG, "filterByCategory exception: ${e.message}")
                _uiState.value = _uiState.value.copy(error = e.message ?: "Exception")
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    // helper to get filtered prompts for UI
    fun getFilteredPrompts(): List<AIPrompt> {
        val prompts = _uiState.value.allPrompts
        val query = _uiState.value.searchQuery
        val category = _uiState.value.selectedCategory

        return prompts.filter { prompt ->
            val title = prompt.title ?: ""
            val short = prompt.shortPrompt ?: ""
            val tags = prompt.tags ?: emptyList()

            val matchesSearch = query.isBlank() ||
                    title.contains(query, ignoreCase = true) ||
                    short.contains(query, ignoreCase = true) ||
                    (prompt.category?.contains(query, ignoreCase = true) ?: false) ||
                    tags.any { it.contains(query, ignoreCase = true) }

            val matchesCategory = category == "All" || (prompt.category ?: "") == category

            matchesSearch && matchesCategory
        }
    }

    /**
     * ✅ OPTIMIZED: Load single prompt without falling back to loading all posts
     *//*
    fun loadPromptByIdOptimized(promptId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                // Check cache first
                val existing = _uiState.value.allPrompts.find { it.id == promptId }
                if (existing != null) {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    return@launch
                }

                // ✅ Try repository single-item endpoint ONLY
                try {
                    val flow = repository.getPromptById(promptId)
                    var foundPrompt: AIPrompt? = null

                    flow.collect { result ->
                        result.fold(
                            onSuccess = { prompt ->
                                foundPrompt = prompt
                                // Add to cache without replacing entire list
                                val updated = _uiState.value.allPrompts + prompt
                                _uiState.value = _uiState.value.copy(
                                    allPrompts = updated,
                                    isLoading = false
                                )
                            },
                            onFailure = { error ->
                                Log.w(TAG, "Single prompt load failed: ${error.message}")
                                _uiState.value = _uiState.value.copy(
                                    error = "Prompt not found: $promptId",
                                    isLoading = false
                                )
                            }
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "loadPromptByIdOptimized exception: ${e.message}")
                    _uiState.value = _uiState.value.copy(
                        error = "Failed to load prompt: ${e.message}",
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Unknown error",
                    isLoading = false
                )
            }
        }
    }*/
}
