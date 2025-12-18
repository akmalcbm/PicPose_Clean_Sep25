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
    /* LIKE / FAVORITE / VIEW — SINGLE SOURCE OF TRUTH */
    /* ---------------------------------------------------------------------- */

    fun toggleLike(prompt: AIPrompt) {
        viewModelScope.launch {
            val updated = engagementRepository.toggleLike(prompt)
            updatePromptEverywhere(updated)
        }
    }

    fun toggleFavorite(prompt: AIPrompt) {
        viewModelScope.launch {
            val updated = engagementRepository.toggleFavorite(prompt)
            updatePromptEverywhere(updated)
        }
    }

    fun onPromptViewed(prompt: AIPrompt) {
        viewModelScope.launch {
            val updated = engagementRepository.incrementView(prompt)
            updatePromptEverywhere(updated)
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
