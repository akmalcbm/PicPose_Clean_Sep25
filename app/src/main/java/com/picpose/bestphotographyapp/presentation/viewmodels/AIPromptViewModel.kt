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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.system.measureTimeMillis
// Add these imports to your Repository and ViewModel files:
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.collect

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

    // ✅ CACHING MECHANISM
    private var cachedPrompts: List<AIPrompt>? = null
    private var lastCacheTime = 0L
    private val CACHE_DURATION = 5 * 60 * 1000L // 5 minutes

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
        // ✅ FIXED: Remove automatic search debouncing to prevent duplicate calls
        // Only load data when explicitly requested

        // Initial loads - but not duplicate
        loadAllPrompts()
        loadFavoritePrompts()
        loadCategories()
    }

    /**
     * ✅ FIXED: Check cache first, prevent duplicate calls
     */
    fun loadAllPrompts(
        page: Int = 1,
        limit: Int = 100,
        category: String? = null,
        search: String? = null,
        forceRefresh: Boolean = false
    ) {
        // ✅ Check cache first
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedPrompts != null && (now - lastCacheTime) < CACHE_DURATION) {
            Log.d(TAG, "Using cached prompts, skipping API call")
            _uiState.value = _uiState.value.copy(
                allPrompts = cachedPrompts!!,
                isLoading = false
            )
            return
        }

        // prevent concurrent loads
        if (isLoadingAll) {
            Log.w(TAG, "loadAllPrompts skipped - already loading")
            return
        }

        loadAllJob?.cancel()
        loadAllJob = viewModelScope.launch {
            isLoadingAll = true
            val elapsed = measureTimeMillis {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                try {
                    Log.d(TAG, "loadAllPrompts: page=$page limit=$limit category=$category search=${search ?: "null"}")

                    val flow = repository.getAllAIPromptsTyped(page = page, limit = limit, category = category, search = search)
                    flow.collect { result: Result<List<AIPrompt>> ->
                        result.fold(
                            onSuccess = { prompts: List<AIPrompt> ->
                                val safePrompts = prompts.map { it.copy(isFavorite = it.isFavorite) }

                                // ✅ Cache the results
                                cachedPrompts = safePrompts
                                lastCacheTime = now

                                _uiState.value = _uiState.value.copy(
                                    allPrompts = safePrompts,
                                    isLoading = false,
                                    error = null
                                )
                                updateCategoriesFromPrompts(safePrompts)
                            },
                            onFailure = { ex: Throwable ->
                                Log.w(TAG, "loadAllPrompts failed: ${ex.message}")
                                _uiState.value = _uiState.value.copy(
                                    error = ex.message ?: "Failed to load prompts",
                                    isLoading = false
                                )
                            }
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "loadAllPrompts exception: ${e.message}")
                    _uiState.value = _uiState.value.copy(
                        error = e.message ?: "Exception while loading prompts",
                        isLoading = false
                    )
                }
            }
            isLoadingAll = false
            Log.d(TAG, "loadAllPrompts finished in ${elapsed}ms")
        }
    }

    /**
     * ✅ FIXED: Get prompt by ID from cache first, then API if needed
     */
    fun loadPromptById(promptId: String) {
        viewModelScope.launch {
            // ✅ First check if prompt exists in current cache
            val existing = _uiState.value.allPrompts.find { it.id == promptId }
            if (existing != null) {
                Log.d(TAG, "Found prompt $promptId in cache, no API call needed")
                return@launch
            }

            // ✅ If not in cache, try to find it in cached prompts
            if (cachedPrompts != null) {
                val cachedPrompt = cachedPrompts!!.find { it.id == promptId }
                if (cachedPrompt != null) {
                    Log.d(TAG, "Found prompt $promptId in cached data")
                    val updated = _uiState.value.allPrompts + cachedPrompt
                    _uiState.value = _uiState.value.copy(allPrompts = updated)
                    return@launch
                }
            }

            // ✅ Only make API call if absolutely necessary
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                // Try single prompt API if available
                val flow = repository.getPromptById(promptId)
                flow.collect { result ->
                    result.fold(
                        onSuccess = { prompt ->
                            val updated = _uiState.value.allPrompts + prompt
                            _uiState.value = _uiState.value.copy(
                                allPrompts = updated,
                                isLoading = false
                            )
                        },
                        onFailure = { ex ->
                            Log.w(TAG, "loadPromptById failed: ${ex.message}")
                            _uiState.value = _uiState.value.copy(
                                error = ex.message ?: "Prompt not found",
                                isLoading = false
                            )
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadPromptById exception: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Exception",
                    isLoading = false
                )
            }
        }
    }

    /**
     * ✅ Search with proper debouncing
     */
    fun onSearchChanged(query: String) {
        _searchQuery.value = query

        // Cancel previous search job
        loadAllJob?.cancel()

        // Debounce search
        loadAllJob = viewModelScope.launch {
            kotlinx.coroutines.delay(400) // 400ms debounce
            Log.d(TAG, "Executing search: '$query'")
            loadAllPrompts(
                page = 1,
                limit = 100,
                search = query.ifBlank { null },
                forceRefresh = true // Force refresh for search
            )
        }
    }

    // Replace your toggleFavorite method with this FIXED version:

    fun toggleFavorite(prompt: AIPrompt) {
        viewModelScope.launch(Dispatchers.Main) { // ✅ FIXED: Explicit Main dispatcher
            try {
                Log.d(TAG, "toggleFavorite: starting for ${prompt.id}")

                // ✅ FIXED: Proper flow collection with context handling
                repository.toggleFavorite(prompt)
                    .flowOn(Dispatchers.IO) // ✅ Ensure flow operations on IO
                    .collect { result: Result<Boolean> ->
                        result.fold(
                            onSuccess = { isNowFavorite: Boolean ->
                                Log.d(TAG, "toggleFavorite success: ${prompt.id} -> $isNowFavorite")

                                // ✅ Update allPrompts list on Main thread
                                val updatedAll = _uiState.value.allPrompts.map { p ->
                                    if (p.id == prompt.id) p.copy(isFavorite = isNowFavorite) else p
                                }

                                // ✅ Update favorite list accordingly
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

                                Log.d(TAG, "toggleFavorite: UI state updated successfully")
                            },
                            onFailure = { ex: Throwable ->
                                Log.w(TAG, "toggleFavorite failed: ${ex.message}")
                                _uiState.value = _uiState.value.copy(
                                    error = ex.message ?: "Failed to toggle favorite"
                                )
                            }
                        )
                    }
            } catch (e: Exception) {
                Log.e(TAG, "toggleFavorite exception: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Exception toggling favorite"
                )
            }
        }
    }

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

    fun refresh() {
        val now = System.currentTimeMillis()
        if (now - lastRefreshTimestamp < MIN_REFRESH_INTERVAL_MS) {
            Log.w(TAG, "refresh throttled")
            return
        }
        lastRefreshTimestamp = now

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true, error = null)
            try {
                // Clear cache to force refresh
                cachedPrompts = null
                lastCacheTime = 0L

                val jobs = listOf(
                    launch { loadAllPrompts(forceRefresh = true) },
                    launch { loadFavoritePrompts() }
                )
                jobs.forEach { it.join() }
            } catch (e: Exception) {
                Log.e(TAG, "refresh exception: ${e.message}")
                _uiState.value = _uiState.value.copy(error = e.message ?: "Exception during refresh")
            } finally {
                _uiState.value = _uiState.value.copy(isRefreshing = false)
            }
        }
    }

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

    // Add these methods to AIPromptViewModel class

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun updateSelectedCategory(category: String) {
        _uiState.value = _uiState.value.copy(selectedCategory = category)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}