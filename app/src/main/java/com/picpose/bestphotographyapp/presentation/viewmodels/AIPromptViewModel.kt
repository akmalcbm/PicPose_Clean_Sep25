package com.picpose.bestphotographyapp.presentation.viewmodels

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.picpose.bestphotographyapp.data.models.AIPrompt
import com.picpose.bestphotographyapp.data.repository.HomeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    init {
        // initial loads
        loadAllPrompts()
        loadFavoritePrompts()
        loadCategories()
    }

    /**
     * Loads prompts using a typed API flow (expects repository.getAllAIPromptsTyped)
     */
    fun loadAllPrompts(page: Int = 1, limit: Int = 100, category: String? = null, search: String? = null) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                // explicit type so Kotlin can infer correctly
                val flow = repository.getAllAIPromptsTyped(page = page, limit = limit, category = category, search = search)
                flow.collect { result: Result<List<AIPrompt>> ->
                    result.fold(
                        onSuccess = { prompts: List<AIPrompt> ->
                            // ensure non-null list
                            val safePrompts = prompts.map { it.copy(isFavorite = it.isFavorite) }
                            _uiState.value = _uiState.value.copy(
                                allPrompts = safePrompts,
                                isLoading = false,
                                error = null
                            )
                            updateCategoriesFromPrompts(safePrompts)
                        },
                        onFailure = { ex: Throwable ->
                            _uiState.value = _uiState.value.copy(
                                error = ex.message ?: "Failed to load prompts",
                                isLoading = false
                            )
                        }
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Exception while loading prompts",
                    isLoading = false
                )
            }
        }
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
                                ( _uiState.value.favoritePrompts + prompt.copy(isFavorite = true) )
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
                            _uiState.value = _uiState.value.copy(error = ex.message ?: "Failed to toggle favorite")
                        }
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message ?: "Exception toggling favorite")
            }
        }
    }

    /**
     * Load favorites from repository and mark them in allPrompts
     */
    fun loadFavoritePrompts() {
        viewModelScope.launch {
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
                            // on failure, keep existing favorites and surface error
                            _uiState.value = _uiState.value.copy(error = ex.message ?: "Failed to load favorites")
                        }
                    )
                }
            } catch (e: Exception) {
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
                _uiState.value = _uiState.value.copy(error = e.message ?: "Exception refreshing favorites")
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
     * Load single prompt by id (adds to allPrompts if not present)
     */
    fun loadPromptById(promptId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val existing = _uiState.value.allPrompts.find { it.id == promptId }
                if (existing != null) {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    return@launch
                }

                // Try repository single-item endpoint (typed)
                var foundPrompt: AIPrompt? = null
                var lastError: String? = null

                try {
                    val flow = repository.getPromptById(promptId)
                    flow.collect { res: Result<AIPrompt> ->
                        res.fold(
                            onSuccess = { p: AIPrompt ->
                                foundPrompt = p
                            },
                            onFailure = { ex: Throwable ->
                                lastError = ex.message ?: "getPromptById failed"
                            }
                        )
                    }
                } catch (e: Exception) {
                    lastError = lastError ?: e.message
                }

                // Fallback: try loading list and searching
                if (foundPrompt == null) {
                    try {
                        val flow = repository.getAllAIPrompts(page = 1, limit = 200)
                        flow.collect { listRes: Result<com.picpose.bestphotographyapp.data.repository.PaginatedResult<AIPrompt>> ->
                            listRes.fold(
                                onSuccess = { paginated ->
                                    val item = paginated.items.find { it.id == promptId }
                                    if (item != null) foundPrompt = item
                                },
                                onFailure = { ex -> lastError = lastError ?: ex.message }
                            )
                        }
                    } catch (e: Exception) {
                        lastError = lastError ?: e.message
                    }
                }

                // Another fallback: simple typed list
                if (foundPrompt == null) {
                    try {
                        val flow = repository.getAllAIPromptsSimple(page = 1, limit = 200)
                        flow.collect { res: Result<List<AIPrompt>> ->
                            res.fold(
                                onSuccess = { list ->
                                    val item = list.find { it.id == promptId }
                                    if (item != null) foundPrompt = item
                                },
                                onFailure = { ex -> lastError = lastError ?: ex.message }
                            )
                        }
                    } catch (_: Exception) {
                        // ignore
                    }
                }

                if (foundPrompt != null) {
                    val updated = _uiState.value.allPrompts + foundPrompt!!
                    _uiState.value = _uiState.value.copy(allPrompts = updated, isLoading = false)
                } else {
                    _uiState.value = _uiState.value.copy(error = lastError ?: "Prompt not found", isLoading = false)
                }

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message ?: "Exception", isLoading = false)
            }
        }
    }

    /**
     * Simple search that filters currently loaded prompts.
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
}
