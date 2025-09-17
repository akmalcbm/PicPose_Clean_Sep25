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

    // derived state: favorites (kept simple using Eagerly to avoid WhileSubscribed timeout complexity)
    val favoritePrompts: StateFlow<List<AIPrompt>> = uiState
        .map { it.favoritePrompts }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    init {
        loadAllPrompts()
        loadFavoritePrompts()
        loadCategories()
    }

    /**
     * Loads paginated prompts (page 1 default). Unwraps PaginatedResult -> items
     */
    fun loadAllPrompts(page: Int = 1, limit: Int = 100) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                repository.getAllAIPrompts(page = page, limit = limit).collect { result ->
                    result.fold(
                        onSuccess = { paginated ->
                            // paginated is PaginatedResult<AIPrompt>
                            val items = paginated.items
                            _uiState.value = _uiState.value.copy(
                                allPrompts = items,
                                isLoading = false,
                                error = null
                            )
                            // update categories now that prompts loaded
                            updateCategoriesFromPrompts(items)
                        },
                        onFailure = { exception ->
                            _uiState.value = _uiState.value.copy(
                                error = exception.message ?: "Unknown error",
                                isLoading = false
                            )
                        }
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Exception",
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
                repository.toggleFavorite(prompt).collect { result ->
                    result.fold(
                        onSuccess = { isNowFavorite ->
                            // Update allPrompts list
                            val updatedAll = _uiState.value.allPrompts.map {
                                if (it.id == prompt.id) it.copy(isFavorite = isNowFavorite) else it
                            }

                            // Update favorite list
                            val updatedFavorites = if (isNowFavorite) {
                                // add prompt (ensure dedup)
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
                        onFailure = { exception ->
                            _uiState.value = _uiState.value.copy(error = exception.message ?: "Failed to toggle favorite")
                        }
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message ?: "Exception")
            }
        }
    }

    /**
     * Load favorites from repository and mark them in allPrompts
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun loadFavoritePrompts() {
        viewModelScope.launch {
            try {
                repository.getFavoritePrompts().collect { result ->
                    result.fold(
                        onSuccess = { favs ->
                            _uiState.value = _uiState.value.copy(favoritePrompts = favs)
                            // Update isFavorite flags in allPrompts
                            val favIds = favs.map { it.id }.toSet()
                            val updatedAll = _uiState.value.allPrompts.map { it.copy(isFavorite = favIds.contains(it.id)) }
                            _uiState.value = _uiState.value.copy(allPrompts = updatedAll)
                        },
                        onFailure = { ex ->
                            _uiState.value = _uiState.value.copy(error = ex.message ?: "Failed to load favorites")
                        }
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message ?: "Exception")
            }
        }
    }

    /**
     * Refresh favorite state by collecting favorite prompts once and updating local flags.
     * This implementation uses suspend collect safely in coroutine.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun refreshFavoriteState() {
        viewModelScope.launch {
            try {
                // collect one result from flow
                var favIds = emptySet<String>()
                repository.getFavoritePrompts().collect { result ->
                    result.onSuccess { favs ->
                        favIds = favs.map { it.id }.toSet()
                    }
                    result.onFailure {
                        // in case of error keep favIds empty
                    }
                }
                val updated = _uiState.value.allPrompts.map { it.copy(isFavorite = favIds.contains(it.id)) }
                _uiState.value = _uiState.value.copy(allPrompts = updated)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message ?: "Exception")
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
            .map { it.category }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
        _uiState.value = _uiState.value.copy(categories = cats)
    }

    /**
     * Load single prompt by id (adds to allPrompts if not present)
     */
    fun loadPromptById(promptId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val existing = _uiState.value.allPrompts.find { it.id == promptId }
                if (existing != null) {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    return@launch
                }
                repository.getPromptById(promptId).collect { result ->
                    result.fold(
                        onSuccess = { prompt ->
                            val updated = _uiState.value.allPrompts + prompt
                            _uiState.value = _uiState.value.copy(allPrompts = updated, isLoading = false)
                        },
                        onFailure = { ex ->
                            _uiState.value = _uiState.value.copy(error = ex.message ?: "Prompt not found", isLoading = false)
                        }
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message ?: "Exception", isLoading = false)
            }
        }
    }

    /**
     * Simple search that filters currently loaded prompts.
     * For server-side search use repository.getAllAIPrompts(search=...)
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
                    p.title.contains(query, ignoreCase = true) ||
                            p.shortPrompt.contains(query, ignoreCase = true) ||
                            p.category.contains(query, ignoreCase = true) ||
                            p.tags.any { it.contains(query, ignoreCase = true) }
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
            val matchesSearch = query.isBlank() ||
                    prompt.title.contains(query, ignoreCase = true) ||
                    prompt.shortPrompt.contains(query, ignoreCase = true) ||
                    prompt.category.contains(query, ignoreCase = true) ||
                    prompt.tags.any { it.contains(query, ignoreCase = true) }

            val matchesCategory = category == "All" || prompt.category == category

            matchesSearch && matchesCategory
        }
    }
}
