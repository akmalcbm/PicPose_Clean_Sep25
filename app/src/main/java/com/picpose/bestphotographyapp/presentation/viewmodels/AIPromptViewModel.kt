package com.picpose.bestphotographyapp.presentation.viewmodels

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.picpose.bestphotographyapp.data.models.AIPrompt
import com.picpose.bestphotographyapp.data.network.ApiService
import com.picpose.bestphotographyapp.data.network.RetrofitClient
import com.picpose.bestphotographyapp.data.repository.HomeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
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
    val isRefreshing: Boolean = false,
    val similarPrompts: List<AIPrompt> = emptyList(),
)

@RequiresApi(Build.VERSION_CODES.O)
@HiltViewModel
class AIPromptViewModel @Inject constructor(
    private val repository: HomeRepository,
    private val api: ApiService // ✅ injected by Hilt
) : ViewModel() {

    private val _uiState = MutableStateFlow(AIPromptUiState())
    val uiState: StateFlow<AIPromptUiState> = _uiState.asStateFlow()

    // Error handler for safe coroutines
    private val errorHandler = CoroutineExceptionHandler { _, e ->
        Log.e(TAG, "Coroutine error: ${e.localizedMessage}")
    }

    // ✅ CACHING MECHANISM
    private var cachedPrompts: List<AIPrompt>? = null
    private var lastCacheTime = 0L
    private val CACHE_DURATION = 5 * 60 * 1000L // 5 minutes

    private val _similarPromptsClickCount = MutableStateFlow(0)
    val similarPromptsClickCount: StateFlow<Int> = _similarPromptsClickCount.asStateFlow()

    val favoritePrompts: StateFlow<List<AIPrompt>> = uiState
        .map { it.favoritePrompts }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    @Volatile
    private var isLoadingAll: Boolean = false
    private var lastRefreshTimestamp = 0L
    private val MIN_REFRESH_INTERVAL_MS = 3_000L // 3s
    private var loadAllJob: Job? = null
    private var loadFavoritesJob: Job? = null

    init {
        loadAllPrompts()
        loadFavoritePrompts()
        loadCategories()
    }

    // =========================================================================
    // 🔹 Update UI States
    // =========================================================================
    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        onSearchChanged(query)
    }

    fun updateSelectedCategory(category: String) {
        _uiState.value = _uiState.value.copy(selectedCategory = category)
        val categoryFilter = if (category == "All") null else category
        loadAllPrompts(
            page = 1,
            limit = 100,
            category = categoryFilter,
            search = _uiState.value.searchQuery,
            forceRefresh = true
        )
    }

    // =========================================================================
    // 🔹 Load All Prompts (with cache)
    // =========================================================================
    fun loadAllPrompts(
        page: Int = 1,
        limit: Int = 100,
        category: String? = null,
        search: String? = null,
        forceRefresh: Boolean = false
    ) {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedPrompts != null && (now - lastCacheTime) < CACHE_DURATION) {
            Log.d(TAG, "Using cached prompts, skipping API call")
            _uiState.value = _uiState.value.copy(
                allPrompts = cachedPrompts!!,
                isLoading = false
            )
            return
        }

        if (isLoadingAll) return
        loadAllJob?.cancel()

        loadAllJob = viewModelScope.launch(errorHandler) {
            isLoadingAll = true
            val elapsed = measureTimeMillis {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                try {
                    val flow = repository.getAllAIPromptsTyped(page, limit, category, search)
                    flow.collect { result ->
                        result.fold(
                            onSuccess = { prompts ->
                                cachedPrompts = prompts
                                lastCacheTime = now
                                _uiState.value = _uiState.value.copy(
                                    allPrompts = prompts,
                                    isLoading = false,
                                    error = null
                                )
                                updateCategoriesFromPrompts(prompts)
                            },
                            onFailure = { ex ->
                                _uiState.value = _uiState.value.copy(
                                    error = ex.message,
                                    isLoading = false
                                )
                            }
                        )
                    }
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(
                        error = e.message,
                        isLoading = false
                    )
                }
            }
            isLoadingAll = false
            Log.d(TAG, "loadAllPrompts finished in ${elapsed}ms")
        }
    }

    // =========================================================================
    // 🔹 Load Prompt By ID (from cache or API)
    // =========================================================================
    fun loadPromptById(promptId: String) {
        viewModelScope.launch {
            val existing = _uiState.value.allPrompts.find { it.id == promptId }
            if (existing != null) {
                existing.category?.let { loadSimilarPrompts(it, promptId) }
                return@launch
            }

            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                repository.getPromptById(promptId).collect { result ->
                    result.fold(
                        onSuccess = { prompt ->
                            val updated = _uiState.value.allPrompts + prompt
                            _uiState.value = _uiState.value.copy(
                                allPrompts = updated,
                                isLoading = false
                            )
                            prompt.category?.let { loadSimilarPrompts(it, promptId) }
                        },
                        onFailure = { ex ->
                            _uiState.value = _uiState.value.copy(
                                error = ex.message,
                                isLoading = false
                            )
                        }
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    // =========================================================================
    // 🔹 Search With Debounce
    // =========================================================================
    fun onSearchChanged(query: String) {
        _searchQuery.value = query
        loadAllJob?.cancel()
        loadAllJob = viewModelScope.launch {
            kotlinx.coroutines.delay(400)
            loadAllPrompts(
                page = 1,
                limit = 100,
                search = query.ifBlank { null },
                forceRefresh = true
            )
        }
    }

    // =========================================================================
    // 🔹 FAVORITE TOGGLE + Remote Analytics
    // =========================================================================
    fun toggleFavorite(prompt: AIPrompt) {
        viewModelScope.launch {
            try {
                repository.toggleFavorite(prompt).collect { result ->
                    result.fold(
                        onSuccess = { isNowFavorite ->
                            cachedPrompts = cachedPrompts?.map { p ->
                                if (p.id == prompt.id) p.copy(isFavorite = isNowFavorite) else p
                            }

                            val updatedAll = _uiState.value.allPrompts.map { p ->
                                if (p.id == prompt.id) p.copy(isFavorite = isNowFavorite) else p
                            }

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

                            // ✅ background API update (analytics)
                            if (isNowFavorite) incrementFavoriteCount(prompt.id.toInt())
                        },
                        onFailure = { ex ->
                            _uiState.value = _uiState.value.copy(error = ex.message)
                        }
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    // =========================================================================
    // 🔹 Load Favorite Prompts
    // =========================================================================
    fun loadFavoritePrompts() {
        loadFavoritesJob?.cancel()
        loadFavoritesJob = viewModelScope.launch {
            try {
                repository.getFavoritePrompts().collect { result ->
                    result.fold(
                        onSuccess = { favs ->
                            val safeFavs = favs.map { it.copy(isFavorite = true) }
                            val favIds = safeFavs.mapNotNull { it.id }.toSet()
                            _uiState.value = _uiState.value.copy(
                                favoritePrompts = safeFavs,
                                allPrompts = _uiState.value.allPrompts.map {
                                    it.copy(isFavorite = favIds.contains(it.id))
                                }
                            )
                        },
                        onFailure = { ex ->
                            if (ex !is CancellationException) {
                                _uiState.value = _uiState.value.copy(error = ex.message)
                            }
                        }
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    // =========================================================================
    // 🔹 Refresh All
    // =========================================================================
    fun refresh() {
        val now = System.currentTimeMillis()
        if (now - lastRefreshTimestamp < MIN_REFRESH_INTERVAL_MS) return
        lastRefreshTimestamp = now

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            try {
                cachedPrompts = null
                lastCacheTime = 0L
                val jobs = listOf(
                    launch { loadAllPrompts(forceRefresh = true) },
                    launch { loadFavoritePrompts() }
                )
                jobs.forEach { it.join() }
            } finally {
                _uiState.value = _uiState.value.copy(isRefreshing = false)
            }
        }
    }

    // =========================================================================
    // 🔹 Categories & Helpers
    // =========================================================================
    fun loadCategories() {
        viewModelScope.launch {
            try {
                updateCategoriesFromPrompts(_uiState.value.allPrompts)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    private fun updateCategoriesFromPrompts(prompts: List<AIPrompt>) {
        val cats = listOf("All") + prompts.mapNotNull { it.category?.takeIf { it.isNotBlank() } }
            .distinct()
            .sorted()
        _uiState.value = _uiState.value.copy(categories = cats)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    // =========================================================================
    // 🔹 Similar Prompts
    // =========================================================================
    fun loadSimilarPrompts(category: String, currentPromptId: String) {
        viewModelScope.launch {
            val similar = (_uiState.value.allPrompts)
                .filter { it.category == category && it.id != currentPromptId }
                .take(10)
            _uiState.value = _uiState.value.copy(similarPrompts = similar)
        }
    }

    fun onSimilarPromptClicked() {
        _similarPromptsClickCount.value++
    }

    fun resetSimilarPromptClickCount() {
        _similarPromptsClickCount.value = 0
    }

    // =========================================================================
    // ✅ NEW: Background Analytics Methods
    // =========================================================================
    fun incrementViewCount(promptId: Int) {
        viewModelScope.launch(Dispatchers.IO + errorHandler) {
            try {
                api.incrementView(promptId, apiKey = RetrofitClient.defaultApiKey)
                Log.d(TAG, "✅ View incremented for $promptId")

                // ✅ Update locally (dynamic UI update)
                _uiState.update { state ->
                    val updatedList = state.allPrompts.map { prompt ->
                        if (prompt.id == promptId.toString())
                            prompt.copy(views = prompt.views + 1)
                        else prompt
                    }

                    state.copy(allPrompts = updatedList)
                }

            } catch (e: Exception) {
                Log.w(TAG, "❌ View increment failed: ${e.message}")
            }
        }
    }

    fun incrementCopyCount(promptId: Int) {
        viewModelScope.launch(Dispatchers.IO + errorHandler) {
            try {
                api.incrementCopy(promptId, apiKey = RetrofitClient.defaultApiKey)
                Log.d(TAG, "✅ Copy incremented for $promptId")
            } catch (e: Exception) {
                Log.w(TAG, "❌ Copy increment failed: ${e.message}")
            }
        }
    }

    fun incrementLikeCount(promptId: Int) {
        viewModelScope.launch(Dispatchers.IO + errorHandler) {
            try {
                api.incrementLike(promptId, apiKey = RetrofitClient.defaultApiKey)
                Log.d(TAG, "✅ Like incremented for $promptId")

                // ✅ Update locally (dynamic UI update)
                _uiState.update { state ->
                    val updatedList = state.allPrompts.map { prompt ->
                        if (prompt.id == promptId.toString())
                            prompt.copy(likes = prompt.likes + 1)
                        else prompt
                    }

                    state.copy(allPrompts = updatedList)
                }

            } catch (e: Exception) {
                Log.w(TAG, "❌ Like increment failed: ${e.message}")
            }
        }
    }

    fun incrementFavoriteCount(promptId: Int) {
        viewModelScope.launch(Dispatchers.IO + errorHandler) {
            try {
                api.incrementFavorite(promptId, apiKey = RetrofitClient.defaultApiKey)
                Log.d(TAG, "✅ Favorite incremented for $promptId")
            } catch (e: Exception) {
                Log.w(TAG, "❌ Favorite increment failed: ${e.message}")
            }
        }
    }
}
