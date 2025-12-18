package com.picpose.bestphotographyapp.presentation.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.picpose.bestphotographyapp.data.datastore.SettingsManager
import com.picpose.bestphotographyapp.data.models.AIPrompt
import com.picpose.bestphotographyapp.data.network.ApiService
import com.picpose.bestphotographyapp.data.network.RetrofitClient
import com.picpose.bestphotographyapp.data.repository.EngagementRepository
import com.picpose.bestphotographyapp.data.repository.HomeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.system.measureTimeMillis

private const val TAG = "AIPromptVM"

/* -------------------------------------------------------------------------- */
/* UI STATE */
/* -------------------------------------------------------------------------- */

data class AIPromptUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,

    val allPrompts: List<AIPrompt> = emptyList(),
    val favoritePrompts: List<AIPrompt> = emptyList(),
    val similarPrompts: List<AIPrompt> = emptyList(),
    val tagPrompts: List<AIPrompt> = emptyList(),

    val categories: List<String> = emptyList(),
    val selectedCategory: String = "All",
    val searchQuery: String = "",

    val totalPrompts: Int = 0,
    val selectedPrompt: AIPrompt? = null,

    val error: String? = null
)

/* -------------------------------------------------------------------------- */
/* VIEWMODEL */
/* -------------------------------------------------------------------------- */

@HiltViewModel
class AIPromptViewModel @Inject constructor(
    private val repository: HomeRepository,
    private val engagementRepository: EngagementRepository,
    private val api: ApiService,
    private val settingsManager: SettingsManager

) : ViewModel() {

    private val _uiState = MutableStateFlow(AIPromptUiState())
    val uiState: StateFlow<AIPromptUiState> = _uiState.asStateFlow()

    /* ---------------------------------------------------------------------- */
    /* ERROR HANDLER */
    /* ---------------------------------------------------------------------- */

    private val errorHandler = CoroutineExceptionHandler { _, e ->
        Log.e(TAG, "Coroutine error: ${e.localizedMessage}", e)
    }

    // Inside AIPromptViewModel
    private val _similarPromptsClickCount = MutableStateFlow(0)
    val similarPromptsClickCount: StateFlow<Int> =
        _similarPromptsClickCount.asStateFlow()

    fun onSimilarPromptClicked() {
        _similarPromptsClickCount.update { it + 1 }
    }

    fun resetSimilarPromptClickCount() {
        _similarPromptsClickCount.value = 0
    }


    /* ---------------------------------------------------------------------- */
    /* PAGINATION + CACHE */
    /* ---------------------------------------------------------------------- */

    private val pageSize = 20
    private var currentPage = 1
    private var canLoadMore = true
    private var isLoadingAll = false

    private var cachedPrompts: List<AIPrompt>? = null
    private var lastCacheTime = 0L
    private val CACHE_DURATION = 5 * 60 * 1000L

    private var selectedCategoryServer: String? = null
    private var loadAllJob: Job? = null
    private var loadFavoritesJob: Job? = null

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /* ---------------------------------------------------------------------- */
    /* GEMINI DIALOG (DATASTORE) */
    /* ---------------------------------------------------------------------- */

    val skipGeminiDialog: StateFlow<Boolean> =
        settingsManager.skipGeminiDialog.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )

    init {
        viewModelScope.launch {
            loadFavoritePrompts()
            loadCategories()
        }
    }

    /* ---------------------------------------------------------------------- */
    /* LIKE / FAVORITE / VIEW — FINAL & CORRECT */
    /* ---------------------------------------------------------------------- */

    fun toggleLike(prompt: AIPrompt) {
        viewModelScope.launch {
            val updated = repository.toggleLikeLocal(prompt)
            updatePromptEverywhere(updated)
        }
    }


    fun toggleFavorite(prompt: AIPrompt) {
        viewModelScope.launch {
            try {
                val updated = repository.toggleFavoriteLocal(prompt)
                updatePromptEverywhere(updated)
            } catch (e: Exception) {
                Log.e(TAG, "toggleFavorite failed: ${e.message}")
            }
        }
    }

    fun onPromptViewed(prompt: AIPrompt) {
        viewModelScope.launch {
            try {
                val updated = engagementRepository.incrementView(prompt)
                updatePromptEverywhere(updated)
            } catch (e: Exception) {
                Log.e(TAG, "incrementView failed: ${e.message}")
            }
        }
    }

    /* ---------------------------------------------------------------------- */
    /* COPY ANALYTICS (SAFE) */
    /* ---------------------------------------------------------------------- */

    fun incrementCopyCount(promptId: Int) {
        viewModelScope.launch(Dispatchers.IO + errorHandler) {
            try {
                api.incrementCopy(promptId, apiKey = RetrofitClient.defaultApiKey)
            } catch (e: Exception) {
                Log.w(TAG, "Copy increment failed: ${e.message}")
            }
        }
    }

    /* ---------------------------------------------------------------------- */
    /* INTERNAL STATE UPDATE (MOST IMPORTANT) */
    /* ---------------------------------------------------------------------- */
    private fun updatePromptEverywhere(updated: AIPrompt) {
        _uiState.update { state ->
            state.copy(
                allPrompts = state.allPrompts.map {
                    if (it.id == updated.id) updated else it
                },
                favoritePrompts = state.favoritePrompts.map {
                    if (it.id == updated.id) updated else it
                },
                selectedPrompt =
                    if (state.selectedPrompt?.id == updated.id) updated
                    else state.selectedPrompt
            )
        }
    }

    /* ---------------------------------------------------------------------- */
    /* CATEGORY + SEARCH */
    /* ---------------------------------------------------------------------- */

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        onSearchChanged(query)
    }

    fun onCategorySelected(category: String) {
        if (
            _uiState.value.selectedCategory == category &&
            _uiState.value.allPrompts.isNotEmpty()
        ) return

        selectedCategoryServer = if (category == "All") null else category
        currentPage = 1
        canLoadMore = true
        cachedPrompts = null

        _uiState.update {
            it.copy(
                selectedCategory = category,
                allPrompts = emptyList()
            )
        }

        loadAllPrompts(page = 1, category = selectedCategoryServer, forceRefresh = true)
    }

    /* ---------------------------------------------------------------------- */
    /* LOAD ALL PROMPTS (CACHE + PAGING) */
    /* ---------------------------------------------------------------------- */

    fun loadAllPrompts(
        page: Int = 1,
        category: String? = null,
        search: String? = null,
        forceRefresh: Boolean = false
    ) {
        val now = System.currentTimeMillis()

        if (
            page == 1 &&
            !forceRefresh &&
            cachedPrompts != null &&
            (now - lastCacheTime) < CACHE_DURATION
        ) {
            _uiState.update {
                it.copy(
                    allPrompts = cachedPrompts!!,
                    totalPrompts = HomeRepository.getLastTotalPrompts(),
                    isLoading = false
                )
            }
            return
        }

        if (isLoadingAll) return
        loadAllJob?.cancel()

        loadAllJob = viewModelScope.launch(errorHandler) {
            isLoadingAll = true

            val elapsed = measureTimeMillis {
                if (page == 1) {
                    _uiState.update { it.copy(isLoading = true, error = null) }
                }

                try {
                    repository.getAllAIPromptsTyped(
                        page = page,
                        limit = pageSize,
                        category = category,
                        search = search
                    ).collect { result ->
                        result.fold(
                            onSuccess = { prompts ->
                                val total = HomeRepository.getLastTotalPrompts()

                                val merged =
                                    if (page == 1) prompts
                                    else (_uiState.value.allPrompts + prompts)
                                        .distinctBy { it.id }

                                cachedPrompts = merged
                                lastCacheTime = now

                                _uiState.update {
                                    it.copy(
                                        allPrompts = merged,
                                        totalPrompts = total,
                                        isLoading = false
                                    )
                                }

                                currentPage = page
                                canLoadMore =
                                    merged.size < total && prompts.isNotEmpty()
                            },
                            onFailure = { ex ->
                                _uiState.update {
                                    it.copy(error = ex.message, isLoading = false)
                                }
                            }
                        )
                    }
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(error = e.message, isLoading = false)
                    }
                }
            }

            isLoadingAll = false
            Log.d(TAG, "loadAllPrompts done in ${elapsed}ms (page=$page)")
        }
    }

    fun onListEndReached() {
        if (!canLoadMore || isLoadingAll) return
        loadAllPrompts(
            page = currentPage + 1,
            category = selectedCategoryServer,
            search = _uiState.value.searchQuery.ifBlank { null }
        )
    }

    /* ---------------------------------------------------------------------- */
    /* DETAIL + SIMILAR */
    /* ---------------------------------------------------------------------- */

    fun selectPromptForDetail(prompt: AIPrompt) {
        _uiState.update { it.copy(selectedPrompt = prompt) }
    }

    fun loadPromptById(promptId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                repository.getPromptById(promptId).collect { result ->
                    result.fold(
                        onSuccess = { prompt ->
                            _uiState.update { state ->
                                state.copy(
                                    allPrompts = (state.allPrompts + prompt).distinctBy { it.id },
                                    selectedPrompt = prompt,
                                    isLoading = false
                                )
                            }
                            prompt.category?.let {
                                loadSimilarPrompts(it, prompt.id)
                            }
                        },
                        onFailure = { ex ->
                            _uiState.update {
                                it.copy(
                                    error = ex.message ?: "Failed to load prompt",
                                    isLoading = false
                                )
                            }
                        }

                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    fun loadSimilarPrompts(category: String, currentPromptId: String) {
        viewModelScope.launch {
            repository.getSimilarAiPrompts(category, currentPromptId).collect { result ->
                result.fold(
                    onSuccess = { list ->
                        _uiState.update { it.copy(similarPrompts = list) }
                    },
                    onFailure = {
                        _uiState.update { it.copy(similarPrompts = emptyList()) }
                    }
                )
            }
        }
    }

    /* ---------------------------------------------------------------------- */
    /* FAVORITES */
    /* ---------------------------------------------------------------------- */

    fun loadFavoritePrompts() {
        loadFavoritesJob?.cancel()
        loadFavoritesJob = viewModelScope.launch(errorHandler) {
            try {
                repository.getFavoritePrompts().collect { result ->
                    result.fold(
                        onSuccess = { favs ->
                            _uiState.update { state ->
                                state.copy(favoritePrompts = favs)
                            }
                        },
                        onFailure = { ex ->
                            if (ex !is CancellationException) {
                                _uiState.update { it.copy(error = ex.message) }
                            }
                        }
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    /* ---------------------------------------------------------------------- */
    /* CATEGORIES */
    /* ---------------------------------------------------------------------- */

    private var categoriesLoaded = false

    fun loadCategories() {
        if (categoriesLoaded) return

        viewModelScope.launch(errorHandler) {
            repository.getCategories().collect { result ->
                result.fold(
                    onSuccess = { categories ->
                        val names = categories.mapNotNull { it.name }.distinct().sorted()
                        _uiState.update { it.copy(categories = listOf("All") + names) }
                        categoriesLoaded = true
                    },
                    onFailure = { e ->
                        _uiState.update { it.copy(error = e.message) }
                    }
                )
            }
        }
    }

    /* ---------------------------------------------------------------------- */
    /* GEMINI SETTINGS */
    /* ---------------------------------------------------------------------- */

    fun setSkipGeminiDialog(skip: Boolean) {
        viewModelScope.launch {
            settingsManager.setSkipGeminiDialog(skip)
        }
    }

    fun resetGeminiDialogPreference() {
        viewModelScope.launch {
            settingsManager.resetGeminiDialogPreference()
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun onSearchChanged(query: String) {
        loadAllJob?.cancel()

        // reset paging
        currentPage = 1
        canLoadMore = true
        cachedPrompts = null
        lastCacheTime = 0L

        loadAllJob = viewModelScope.launch {
            delay(400)
            loadAllPrompts(
                page = 1,
                category = selectedCategoryServer,
                search = query.ifBlank { null },
                forceRefresh = true
            )
        }
    }


}



/*
package com.picpose.bestphotographyapp.presentation.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.picpose.bestphotographyapp.data.datastore.SettingsManager
import com.picpose.bestphotographyapp.data.models.AIPrompt
import com.picpose.bestphotographyapp.data.network.ApiService
import com.picpose.bestphotographyapp.data.network.RetrofitClient
import com.picpose.bestphotographyapp.data.repository.HomeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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

    // 🔹 Add this missing property!
    val tagPrompts: List<AIPrompt> = emptyList(),

    // 🔢 Server total count (from "total")
    val totalPrompts: Int = 0,

    // 🎯 Currently opened in detail
    val selectedPrompt: AIPrompt? = null
)

@HiltViewModel
class AIPromptViewModel @Inject constructor(
    private val repository: HomeRepository,
    private val api: ApiService,
    private val settingsManager: SettingsManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AIPromptUiState())
    val uiState: StateFlow<AIPromptUiState> = _uiState.asStateFlow()

    // Global coroutine error handler
    private val errorHandler = CoroutineExceptionHandler { _, e ->
        Log.e(TAG, "Coroutine error: ${e.localizedMessage}", e)
    }

    // ✅ Caching + Pagination
    private var cachedPrompts: List<AIPrompt>? = null
    private var lastCacheTime = 0L
    private val CACHE_DURATION = 5 * 60 * 1000L // 5 minutes

    private val pageSize = 20          // how many per page
    private var currentPage = 1        // last loaded page
    private var canLoadMore = true     // false when reach totalPrompts

    private var selectedCategoryServer: String? = null

    private val _similarPromptsClickCount = MutableStateFlow(0)
    val similarPromptsClickCount: StateFlow<Int> = _similarPromptsClickCount.asStateFlow()

    val favoritePrompts: StateFlow<List<AIPrompt>> = uiState
        .map { it.favoritePrompts }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    // 🔥 Gemini dialog preference (DataStore → UI)
    val skipGeminiDialog: StateFlow<Boolean> =
        settingsManager.skipGeminiDialog
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = false
            )

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    @Volatile
    private var isLoadingAll: Boolean = false

    private var lastRefreshTimestamp = 0L
    private val MIN_REFRESH_INTERVAL_MS = 3_000L

    private var loadAllJob: Job? = null
    private var loadFavoritesJob: Job? = null

    init {
        viewModelScope.launch {
            // First page load
            //loadAllPrompts(page = 1, forceRefresh = true)
            loadFavoritePrompts()
            loadCategories()
        }
    }

    // 💾 Save "Don't ask again"
    fun setSkipGeminiDialog(skip: Boolean) {
        viewModelScope.launch {
            settingsManager.setSkipGeminiDialog(skip)
        }
    }

    // 🔁 Reset Gemini preference (Settings screen ke liye)
    fun resetGeminiDialogPreference() {
        viewModelScope.launch {
            settingsManager.resetGeminiDialogPreference()
        }
    }


    // =========================================================================
    // 🔹 Search & Category
    // =========================================================================

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        onSearchChanged(query)
    }


    fun onCategorySelected(category: String) {

        // 🛡 SAFETY GUARD — same category & data already loaded
        if (
            _uiState.value.selectedCategory == category &&
            _uiState.value.allPrompts.isNotEmpty()
        ) {
            return
        }

        selectedCategoryServer = if (category == "All") null else category

        // reset paging
        currentPage = 1
        canLoadMore = true
        cachedPrompts = null

        _uiState.update {
            it.copy(
                selectedCategory = category,
                allPrompts = emptyList()
            )
        }

        loadAllPrompts(
            page = 1,
            category = selectedCategoryServer,
            forceRefresh = true
        )
    }



    */
/*
    //Check Later If Above Works Remove Below nad where it is uses replace with onCategorySelected
    @Deprecated("Use onCategorySelected instead")
    fun updateSelectedCategory(category: String) {
        _uiState.update { it.copy(selectedCategory = category) }

        val categoryFilter = if (category == "All") null else category

        // reset paging on category change
        currentPage = 1
        canLoadMore = true
        cachedPrompts = null
        lastCacheTime = 0L

        loadAllPrompts(
            page = currentPage + 1,
            category = selectedCategoryServer,
            search = _uiState.value.searchQuery.ifBlank { null },
            forceRefresh = true
        )
    }*//*




    // =========================================================================
    // 🔹 Load All Prompts (with cache + paging)
    // =========================================================================

    fun loadAllPrompts(
        page: Int = 1,
        category: String? = null,
        search: String? = null,
        forceRefresh: Boolean = false
    ) {
        val now = System.currentTimeMillis()

        // ✅ Use cache only for FIRST PAGE
        if (
            page == 1 &&
            !forceRefresh &&
            cachedPrompts != null &&
            (now - lastCacheTime) < CACHE_DURATION
        ) {
            Log.d(TAG, "Using cached prompts, skipping API call")
            val totalFromServer = HomeRepository.getLastTotalPrompts()
            _uiState.update {
                it.copy(
                    allPrompts = cachedPrompts!!,
                    totalPrompts = totalFromServer,
                    isLoading = false
                )
            }
            return
        }

        // Prevent overlapping calls
        if (isLoadingAll) return
        loadAllJob?.cancel()

        loadAllJob = viewModelScope.launch(errorHandler) {
            isLoadingAll = true

            val elapsed = measureTimeMillis {
                // Big loader sirf first page ke liye
                if (page == 1) {
                    _uiState.update { it.copy(isLoading = true, error = null) }
                }

                try {
                    val flow = repository.getAllAIPromptsTyped(
                        page = page,
                        limit = pageSize,
                        category = category,
                        search = search
                    )

                    flow.collect { result ->
                        result.fold(
                            onSuccess = { prompts ->
                                val totalFromServer = HomeRepository.getLastTotalPrompts()

                                if (page == 1) {
                                    // fresh load
                                    cachedPrompts = prompts
                                    lastCacheTime = now

                                    _uiState.update {
                                        it.copy(
                                            allPrompts = prompts,
                                            totalPrompts = totalFromServer,
                                            isLoading = false,
                                            error = null
                                        )
                                    }
                                    //updateCategoriesFromPrompts(prompts)
                                } else {
                                    // append for pagination (avoid duplicates)
                                    val currentList = _uiState.value.allPrompts
                                    val merged = currentList + prompts.filterNot { newItem ->
                                        currentList.any { it.id == newItem.id }
                                    }

                                    cachedPrompts = merged
                                    lastCacheTime = now

                                    _uiState.update {
                                        it.copy(
                                            allPrompts = merged,
                                            totalPrompts = totalFromServer,
                                            isLoading = false,
                                            error = null
                                        )
                                    }
                                }

                                currentPage = page
                                // agar already sab aa chuke hain, to aage mat load karo
                                val loadedCount = _uiState.value.allPrompts.size
                                canLoadMore = loadedCount < totalFromServer && prompts.isNotEmpty()
                                Log.d(
                                    TAG,
                                    "Paging → page=$page, loaded=$loadedCount / total=$totalFromServer, canLoadMore=$canLoadMore"
                                )
                            },
                            onFailure = { ex ->
                                Log.e(TAG, "loadAllPrompts failed: ${ex.message}")
                                _uiState.update {
                                    it.copy(
                                        error = ex.message,
                                        isLoading = false
                                    )
                                }
                            }
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "loadAllPrompts exception: ${e.message}", e)
                    _uiState.update {
                        it.copy(
                            error = e.message,
                            isLoading = false
                        )
                    }
                }
            }

            isLoadingAll = false
            Log.d(TAG, "loadAllPrompts finished in ${elapsed}ms (page=$page)")
        }
    }

    */
/**
     * Called from UI when user scrolls near end of list.
     *//*

    fun onListEndReached() {
        if (!canLoadMore || isLoadingAll) return

        val categoryFilter = _uiState.value.let {
            if (it.selectedCategory == "All") null else it.selectedCategory
        }
        val searchFilter = _uiState.value.searchQuery.ifBlank { null }

        loadAllPrompts(
            page = currentPage + 1,
            category = selectedCategoryServer,
            search = searchFilter,
            forceRefresh = false
        )
    }

    // =========================================================================
    // 🔹 Detail Screen – Single Prompt
    // =========================================================================

    fun selectPromptForDetail(prompt: AIPrompt) {
        _uiState.update { it.copy(selectedPrompt = prompt) }
    }


    // 🔹 Single Prompt for Detail Screen
    fun loadPromptById(promptId: String) {
        // already same prompt selected → skip
        uiState.value.selectedPrompt?.let {
            if (it.id == promptId) {
                Log.d(TAG, "loadPromptById: skipping, already selected id=$promptId")
                return
            }
        }

        viewModelScope.launch {
            Log.d(TAG, "loadPromptById: fetching from API id=$promptId")

            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                repository.getPromptById(promptId).collect { result ->
                    result.fold(
                        onSuccess = { prompt ->
                            _uiState.update { state ->
                                // merge into list (avoid duplicates)
                                val merged = (state.allPrompts + prompt)
                                    .distinctBy { it.id }

                                state.copy(
                                    allPrompts = merged,
                                    selectedPrompt = prompt,
                                    isLoading = false,
                                    error = null
                                )
                            }

                            prompt.category?.let { cat ->
                                loadSimilarPrompts(cat, prompt.id ?: "")
                            }
                        },
                        onFailure = { ex ->
                            Log.e(TAG, "loadPromptById failed: ${ex.message}")
                            _uiState.update {
                                it.copy(
                                    error = ex.message ?: "Failed to load prompt",
                                    isLoading = false
                                )
                            }
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadPromptById exception: ${e.message}")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Unknown error"
                    )
                }
            }
        }
    }



    // =========================================================================
    // 🔹 Search With Debounce
    // =========================================================================

    fun onSearchChanged(query: String) {
        _searchQuery.value = query
        loadAllJob?.cancel()

        // reset paging
        currentPage = 1
        canLoadMore = true
        cachedPrompts = null
        lastCacheTime = 0L

        loadAllJob = viewModelScope.launch {
            kotlinx.coroutines.delay(400)
            loadAllPrompts(
                page = 1,
                category = selectedCategoryServer,
                search = query.ifBlank { null },
                forceRefresh = true
            )
        }
    }

    // =========================================================================
    // 🔹 FAVORITE TOGGLE + Remote Analytics
    // =========================================================================

    fun toggleFavorite(prompt: AIPrompt) {
        viewModelScope.launch(errorHandler) {
            try {
                repository.toggleFavorite(prompt).collect { result ->
                    result.fold(
                        onSuccess = { isNowFavorite ->
                            // cache update
                            cachedPrompts = cachedPrompts?.map { p ->
                                if (p.id == prompt.id) p.copy(isBookmarked = isNowFavorite) else p
                            }

                            val updatedAll = _uiState.value.allPrompts.map { p ->
                                if (p.id == prompt.id) p.copy(isBookmarked = isNowFavorite) else p
                            }

                            val updatedFavorites = if (isNowFavorite) {
                                (_uiState.value.favoritePrompts + prompt.copy(isBookmarked = true))
                                    .distinctBy { it.id }
                            } else {
                                _uiState.value.favoritePrompts.filter { it.id != prompt.id }
                            }

                            _uiState.update {
                                it.copy(
                                    allPrompts = updatedAll,
                                    favoritePrompts = updatedFavorites,
                                    selectedPrompt = it.selectedPrompt?.let { sel ->
                                        if (sel.id == prompt.id) sel.copy(isBookmarked = isNowFavorite) else sel
                                    }
                                )
                            }

                            // background analytics
                            prompt.id?.toIntOrNull()?.let { idInt ->
                                if (isNowFavorite) incrementFavoriteCount(idInt)
                            }
                        },
                        onFailure = { ex ->
                            _uiState.update { it.copy(error = ex.message) }
                        }
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    // =========================================================================
    // 🔹 Load Favorite Prompts
    // =========================================================================

    fun loadFavoritePrompts() {
        loadFavoritesJob?.cancel()
        loadFavoritesJob = viewModelScope.launch(errorHandler) {
            try {
                repository.getFavoritePrompts().collect { result ->
                    result.fold(
                        onSuccess = { favs ->
                            val safeFavs = favs.map { it.copy(isBookmarked = true) }
                            val favIds = safeFavs.mapNotNull { it.id }.toSet()

                            _uiState.update { state ->
                                state.copy(
                                    favoritePrompts = safeFavs,
                                    allPrompts = state.allPrompts.map {
                                        it.copy(isBookmarked = favIds.contains(it.id))
                                    },
                                    selectedPrompt = state.selectedPrompt?.let { sel ->
                                        sel.copy(isBookmarked = favIds.contains(sel.id))
                                    }
                                )
                            }
                        },
                        onFailure = { ex ->
                            if (ex !is CancellationException) {
                                _uiState.update { it.copy(error = ex.message) }
                            }
                        }
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    // =========================================================================
    // 🔹 Pull-to-Refresh
    // =========================================================================

    fun refresh() {
        val now = System.currentTimeMillis()
        if (now - lastRefreshTimestamp < MIN_REFRESH_INTERVAL_MS) return
        lastRefreshTimestamp = now

        viewModelScope.launch(errorHandler) {
            _uiState.update { it.copy(isRefreshing = true) }
            try {
                cachedPrompts = null
                lastCacheTime = 0L
                currentPage = 1
                canLoadMore = true

                val jobs = listOf(
                    launch { loadAllPrompts(page = 1, forceRefresh = true) },
                    launch { loadFavoritePrompts() }
                )
                jobs.forEach { it.join() }
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    // =========================================================================
    // 🔹 Categories
    // =========================================================================

    private var categoriesLoaded = false

    fun loadCategories() {
        if (categoriesLoaded) return

        viewModelScope.launch(errorHandler) {
            repository.getCategories().collect { result ->
                result.fold(
                    onSuccess = { categories ->
                        val names = categories
                            .mapNotNull { it.name }
                            .distinct()
                            .sorted()

                        _uiState.update {
                            it.copy(categories = listOf("All") + names)
                        }

                        categoriesLoaded = true
                    },
                    onFailure = { e ->
                        _uiState.update { it.copy(error = e.message) }
                    }
                )
            }
        }
    }



    */
/*private fun updateCategoriesFromPrompts(prompts: List<AIPrompt>) {
        val cats = listOf("All") + prompts
            .mapNotNull { it.category?.takeIf { cat -> cat.isNotBlank() } }
            .distinct()
            .sorted()

        _uiState.update { it.copy(categories = cats) }
    }*//*


    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }


    // =========================================================================
    // 🔹 Similar Prompts
    // =========================================================================

    fun loadSimilarPrompts(category: String, currentPromptId: String) {
        viewModelScope.launch {
            repository
                .getSimilarAiPrompts(
                    category = category,
                    excludePromptId = currentPromptId
                )
                .collect { result ->
                    result.fold(
                        onSuccess = { list ->
                            _uiState.update {
                                it.copy(similarPrompts = list)
                            }
                        },
                        onFailure = { e ->
                            Log.e(TAG, "Similar prompts failed: ${e.message}")
                            _uiState.update {
                                it.copy(similarPrompts = emptyList())
                            }
                        }
                    )
                }
        }
    }


    fun onSimilarPromptClicked() {
        _similarPromptsClickCount.value++
    }

    fun resetSimilarPromptClickCount() {
        _similarPromptsClickCount.value = 0
    }

    // =========================================================================
    // 🔹 Analytics Counters
    // =========================================================================

    fun incrementViewCount(promptId: Int) {
        viewModelScope.launch(Dispatchers.IO + errorHandler) {
            try {
                api.incrementView(promptId, apiKey = RetrofitClient.defaultApiKey)
                Log.d(TAG, "✅ View incremented for $promptId")

                _uiState.update { state ->
                    val idStr = promptId.toString()
                    val updatedList = state.allPrompts.map { prompt ->
                        if (prompt.id == idStr) prompt.copy(views = prompt.views + 1) else prompt
                    }
                    val updatedSelected = state.selectedPrompt?.let { sel ->
                        if (sel.id == idStr) sel.copy(views = sel.views + 1) else sel
                    }
                    state.copy(allPrompts = updatedList, selectedPrompt = updatedSelected)
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

                _uiState.update { state ->
                    val idStr = promptId.toString()
                    val updatedList = state.allPrompts.map { prompt ->
                        if (prompt.id == idStr) prompt.copy(likes = prompt.likes + 1) else prompt
                    }
                    val updatedSelected = state.selectedPrompt?.let { sel ->
                        if (sel.id == idStr) sel.copy(likes = sel.likes + 1) else sel
                    }
                    state.copy(allPrompts = updatedList, selectedPrompt = updatedSelected)
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
*/
