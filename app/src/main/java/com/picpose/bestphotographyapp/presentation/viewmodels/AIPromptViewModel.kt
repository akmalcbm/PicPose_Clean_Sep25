package com.picpose.bestphotographyapp.presentation.viewmodels

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

class AIPromptViewModel(private val repository: HomeRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(AIPromptUiState())
    val uiState: StateFlow<AIPromptUiState> = _uiState.asStateFlow()

    val favoritePrompts: StateFlow<List<AIPrompt>> = uiState
        .map { it.favoritePrompts }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        loadAllPrompts()
        loadFavoritePrompts()
        loadCategories()
    }

    fun loadAllPrompts() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                repository.getAllAIPrompts().collect { result ->
                    result.fold(
                        onSuccess = { prompts ->
                            _uiState.value = _uiState.value.copy(
                                allPrompts = prompts,
                                isLoading = false
                            )
                        },
                        onFailure = { exception ->
                            _uiState.value = _uiState.value.copy(
                                error = exception.message,
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
    }

    fun toggleFavorite(prompt: AIPrompt) {
        viewModelScope.launch {
            try {
                repository.toggleFavorite(prompt).collect { result ->
                    result.fold(
                        onSuccess = { isFavorite ->
                            // Update the prompt in allPrompts list
                            val updatedAllPrompts = _uiState.value.allPrompts.map {
                                if (it.id == prompt.id) {
                                    it.copy(isFavorite = isFavorite)
                                } else {
                                    it
                                }
                            }

                            // Update favorites list
                            val updatedFavorites = if (isFavorite) {
                                // Add to favorites
                                val updatedPrompt = prompt.copy(isFavorite = true)
                                _uiState.value.favoritePrompts + updatedPrompt
                            } else {
                                // Remove from favorites
                                _uiState.value.favoritePrompts.filter { it.id != prompt.id }
                            }

                            _uiState.value = _uiState.value.copy(
                                allPrompts = updatedAllPrompts,
                                favoritePrompts = updatedFavorites
                            )
                        },
                        onFailure = { exception ->
                            _uiState.value = _uiState.value.copy(error = exception.message)
                        }
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun loadFavoritePrompts() {
        viewModelScope.launch {
            try {
                repository.getFavoritePrompts().collect { result ->
                    result.fold(
                        onSuccess = { favorites ->
                            _uiState.value = _uiState.value.copy(favoritePrompts = favorites)

                            // Also update the isFavorite flag in allPrompts
                            val favoriteIds = favorites.map { it.id }.toSet()
                            val updatedAllPrompts = _uiState.value.allPrompts.map { prompt ->
                                prompt.copy(isFavorite = favoriteIds.contains(prompt.id))
                            }
                            _uiState.value = _uiState.value.copy(allPrompts = updatedAllPrompts)
                        },
                        onFailure = { exception ->
                            _uiState.value = _uiState.value.copy(error = exception.message)
                        }
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    // Add this method to refresh favorite state
    fun refreshFavoriteState() {
        viewModelScope.launch {
            try {
                val favoriteIds = repository.getFavoritePrompts().let { flow ->
                    var ids = emptySet<String>()
                    flow.collect { result ->
                        result.onSuccess { favorites ->
                            ids = favorites.map { it.id }.toSet()
                        }
                    }
                    ids
                }

                val updatedAllPrompts = _uiState.value.allPrompts.map { prompt ->
                    prompt.copy(isFavorite = favoriteIds.contains(prompt.id))
                }

                _uiState.value = _uiState.value.copy(allPrompts = updatedAllPrompts)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun loadCategories() {
        viewModelScope.launch {
            try {
                val categories = listOf("All") + _uiState.value.allPrompts
                    .map { it.category }
                    .distinct()
                    .sorted()

                _uiState.value = _uiState.value.copy(categories = categories)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    // FIXED: loadPromptById method
    fun loadPromptById(promptId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                // Try to find in current prompts first
                val existingPrompt = _uiState.value.allPrompts.find { it.id == promptId }

                if (existingPrompt == null) {
                    // Load from repository if not found
                    repository.getPromptById(promptId).collect { result ->
                        result.fold(
                            onSuccess = { prompt ->
                                val updatedPrompts = _uiState.value.allPrompts + prompt
                                _uiState.value = _uiState.value.copy(
                                    allPrompts = updatedPrompts,
                                    isLoading = false
                                )
                            },
                            onFailure = { exception ->
                                _uiState.value = _uiState.value.copy(
                                    error = exception.message,
                                    isLoading = false
                                )
                            }
                        )
                    }
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message,
                    isLoading = false
                )
            }
        }
    }

    fun searchPrompts(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)

        if (query.isBlank()) {
            loadAllPrompts()
            return
        }

        viewModelScope.launch {
            try {
                val filteredPrompts = repository.getMockAIPrompts().filter { prompt ->
                    prompt.title.contains(query, ignoreCase = true) ||
                            prompt.shortPrompt.contains(query, ignoreCase = true) ||
                            prompt.category.contains(query, ignoreCase = true) ||
                            prompt.tags.any { it.contains(query, ignoreCase = true) }
                }

                _uiState.value = _uiState.value.copy(allPrompts = filteredPrompts)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
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
                val filteredPrompts = repository.getMockAIPrompts().filter {
                    it.category == category
                }
                _uiState.value = _uiState.value.copy(allPrompts = filteredPrompts)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    // Get filtered prompts based on current filters
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
