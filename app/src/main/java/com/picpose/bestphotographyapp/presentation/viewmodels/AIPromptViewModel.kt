package com.picpose.bestphotographyapp.presentation.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.picpose.bestphotographyapp.data.PromptRepository
import com.picpose.bestphotographyapp.data.database.entities.EngagementEntity
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
    private val homeRepository: HomeRepository,
    private val engagementRepository: EngagementRepository,
    private val promptRepository: PromptRepository,
    private val api: ApiService,
    private val settingsManager: SettingsManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AIPromptUiState())
    val uiState: StateFlow<AIPromptUiState> = _uiState.asStateFlow()

    /* ---------------------------------------------------------------------- */
    /* ERROR HANDLER */
    /* ---------------------------------------------------------------------- */

    private val errorHandler = CoroutineExceptionHandler { _, e ->
        Log.e(TAG, "Coroutine error", e)
    }

    /* ---------------------------------------------------------------------- */
    /* SIMILAR PROMPT CLICK COUNT (ADS / LOGIC) */
    /* ---------------------------------------------------------------------- */

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

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /* ---------------------------------------------------------------------- */
    /* GEMINI SETTINGS */
    /* ---------------------------------------------------------------------- */

    val skipGeminiDialog: StateFlow<Boolean> =
        settingsManager.skipGeminiDialog.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )

    init {
        viewModelScope.launch {
            preloadPromptCacheForFavorites()
            loadCategories()
        }
    }

    /* ---------------------------------------------------------------------- */
    /* LIKE - USING CENTRALIZED HANDLER - UPDATED */
    /* ---------------------------------------------------------------------- */

    fun onLikeClicked(prompt: AIPrompt) {
        viewModelScope.launch {
            val id = prompt.id ?: return@launch

            try {
                // Use centralized handler with current like count
                val result = engagementRepository.handleLike(id, prompt.likes)

                // ✅ FIXED: Update BOTH allPrompts AND favorite prompts flow
                updatePromptInAllStates(id) { currentPrompt ->
                    currentPrompt.copy(
                        isLiked = result.isLiked,
                        likes = result.newLikes
                    )
                }

                // ✅ FIXED: Also update the prompt in promptRepository cache
                updatePromptInRepositoryCache(id, result.isLiked, result.newLikes)

                Log.d(TAG, "✅ Like handled: ${id}, liked: ${result.isLiked}, newLikes: ${result.newLikes}")

            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to handle like: ${e.message}")
            }
        }
    }

    /* ---------------------------------------------------------------------- */
    /* FAVORITE - USING CENTRALIZED HANDLER - UPDATED */
    /* ---------------------------------------------------------------------- */

    fun onFavoriteClicked(prompt: AIPrompt) {
        viewModelScope.launch {
            val id = prompt.id ?: return@launch

            try {
                // Use centralized handler with current favorite count
                val result = engagementRepository.handleBookmark(id, prompt.favorites)

                // ✅ FIXED: Update BOTH allPrompts AND favorite prompts flow
                updatePromptInAllStates(id) { currentPrompt ->
                    currentPrompt.copy(
                        isFavouriteBookmarked = result.isBookmarked,
                        favorites = result.newFavorites
                    )
                }

                // ✅ FIXED: Also update the prompt in promptRepository cache
                updatePromptInRepositoryCache(id, result.isBookmarked, result.newFavorites, isFavoriteUpdate = true)

                Log.d(TAG, "✅ Bookmark handled: ${id}, bookmarked: ${result.isBookmarked}, newFavorites: ${result.newFavorites}")

            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to handle bookmark: ${e.message}")
            }
        }
    }

    /* ---------------------------------------------------------------------- */
    /* REGISTER VIEW (CENTRALIZED) - FIXED */
    /* ---------------------------------------------------------------------- */

    fun registerView(promptId: String) {
        viewModelScope.launch {
            // Use centralized view registration
            engagementRepository.registerView(promptId)

            // 🔥 IMPORTANT: Don't update UI views here
            // Views should only be updated from server response
            // The displayViews() function will show server views automatically
        }
    }

    /* ---------------------------------------------------------------------- */
    /* UPDATED HELPER FUNCTION - UPDATES ALL STATES */
    /* ---------------------------------------------------------------------- */

    /* ---------------------------------------------------------------------- */
    /* UPDATED HELPER FUNCTION - UPDATES ALL STATES */
    /* ---------------------------------------------------------------------- */

    private fun updatePromptInAllStates(
        promptId: String,
        transform: (AIPrompt) -> AIPrompt
    ) {
        _uiState.update { currentState ->
            // Update allPrompts
            val updatedAllPrompts = currentState.allPrompts.map { prompt ->
                if (prompt.id == promptId) transform(prompt) else prompt
            }

            // Update selectedPrompt
            val updatedSelectedPrompt = if (currentState.selectedPrompt?.id == promptId) {
                transform(currentState.selectedPrompt)
            } else currentState.selectedPrompt

            currentState.copy(
                allPrompts = updatedAllPrompts,
                selectedPrompt = updatedSelectedPrompt
            )
        }

        // ✅ FIXED: Also update the prompt in promptRepository cache
        // This will automatically update favoritePromptsFlow
        val updatedPrompt = _uiState.value.allPrompts.find { it.id == promptId }
        if (updatedPrompt != null) {
            viewModelScope.launch {
                // Update cache which feeds favoritePromptsFlow
                val currentCache = promptRepository.observeAllPrompts().value
                val updatedCache = currentCache.map { prompt ->
                    if (prompt.id == promptId) updatedPrompt else prompt
                }
                promptRepository.syncPromptCache(updatedCache)
            }
        }
    }

    /* ---------------------------------------------------------------------- */
    /* NEW FUNCTION: UPDATE PROMPT IN REPOSITORY CACHE */
    /* ---------------------------------------------------------------------- */

    private fun updatePromptInRepositoryCache(
        promptId: String,
        isLiked: Boolean? = null,
        newLikes: Int? = null,
        isBookmarked: Boolean? = null,
        newFavorites: Int? = null,
        isFavoriteUpdate: Boolean = false
    ) {
        viewModelScope.launch {
            // Get current cache
            val currentCache = promptRepository.observeAllPrompts().value
            val promptToUpdate = currentCache.find { it.id == promptId }

            if (promptToUpdate != null) {
                // Create updated prompt
                val updatedPrompt = promptToUpdate.copy(
                    isLiked = isLiked ?: promptToUpdate.isLiked,
                    likes = newLikes ?: promptToUpdate.likes,
                    isFavouriteBookmarked = isBookmarked ?: promptToUpdate.isFavouriteBookmarked,
                    favorites = newFavorites ?: promptToUpdate.favorites
                )

                // Update cache
                val updatedCache = currentCache.map { prompt ->
                    if (prompt.id == promptId) updatedPrompt else prompt
                }

                promptRepository.syncPromptCache(updatedCache)

                // Log for debugging
                Log.d(TAG, "✅ Cache updated for prompt: $promptId, " +
                        "likes: ${updatedPrompt.likes}, " +
                        "favorites: ${updatedPrompt.favorites}, " +
                        "isFavoriteUpdate: $isFavoriteUpdate")
            }
        }
    }


    /* ---------------------------------------------------------------------- */
    /* MERGE WITH LOCAL ENGAGEMENT - FIXED */
    /* ---------------------------------------------------------------------- */

    private suspend fun mergeWithLocalEngagement(
        prompts: List<AIPrompt>
    ): List<AIPrompt> {
        val localStates = localEngagementStates.value
        return prompts.map { prompt ->
            val local = localStates[prompt.id]
            if (local == null) prompt
            else prompt.copy(
                isLiked = local.isLiked,
                isFavouriteBookmarked = local.isFavorited
                // 🔥 IMPORTANT: views नहीं add करें
                // Server views ही show करें
            )
        }
    }

    /* ---------------------------------------------------------------------- */
    /* PRELOAD FAVORITES (APP RESTART FIX) */
    /* ---------------------------------------------------------------------- */

    private suspend fun preloadPromptCacheForFavorites() {
        val favIds = engagementRepository.getAllFavoritedPromptIds()

        Log.e("FAV_DEBUG", "Preloading cache for favorite IDs = $favIds")

        favIds.forEach { id ->
            runCatching {
                homeRepository.getPromptById(id).collect { result ->
                    result.onSuccess { prompt ->
                        promptRepository.syncPromptCache(
                            (promptRepository.observeAllPrompts().value + prompt)
                                .distinctBy { it.id }
                        )
                        Log.e("FAV_DEBUG", "Preloaded prompt id=${prompt.id}")
                    }
                }
            }
        }
    }

    /* ---------------------------------------------------------------------- */
    /* ANALYTICS — COPY COUNT */
    /* ---------------------------------------------------------------------- */

    fun incrementCopyCount(promptId: Int) {
        viewModelScope.launch(Dispatchers.IO + errorHandler) {
            runCatching {
                api.incrementCopy(promptId, RetrofitClient.defaultApiKey)
            }
        }
    }

    /* ---------------------------------------------------------------------- */
    /* LOCAL ENGAGEMENT STATE (SINGLE SOURCE OF TRUTH) */
    /* ---------------------------------------------------------------------- */

    val localEngagementStates: StateFlow<Map<String, EngagementEntity>> =
        engagementRepository.observeAllStates()
            .map { list -> list.associateBy { it.promptId } }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyMap()
            )

    /* ---------------------------------------------------------------------- */
    /* FAVORITES FLOW */
    /* ---------------------------------------------------------------------- */

    val favoritePromptsFlow: StateFlow<List<AIPrompt>> =
        engagementRepository.observeFavoritePrompts()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    /* ---------------------------------------------------------------------- */
    /* SEARCH + CATEGORY */
    /* ---------------------------------------------------------------------- */

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        onSearchChanged(query)
    }

    fun onCategorySelected(category: String) {
        if (_uiState.value.selectedCategory == category &&
            _uiState.value.allPrompts.isNotEmpty()
        ) return

        selectedCategoryServer = if (category == "All") null else category
        currentPage = 1
        canLoadMore = true
        cachedPrompts = null
        lastCacheTime = 0L

        _uiState.update {
            it.copy(selectedCategory = category, allPrompts = emptyList())
        }

        loadAllPrompts(
            page = 1,
            category = selectedCategoryServer,
            forceRefresh = true
        )
    }

    /* ---------------------------------------------------------------------- */
    /* LOAD ALL PROMPTS */
    /* ---------------------------------------------------------------------- */

    fun loadAllPrompts(
        page: Int = 1,
        category: String? = null,
        search: String? = null,
        forceRefresh: Boolean = false
    ) {
        val now = System.currentTimeMillis()

        if (page == 1 && !forceRefresh &&
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

                homeRepository.getAllAIPromptsTyped(
                    page = page,
                    limit = pageSize,
                    category = category,
                    search = search
                ).collect { result ->
                    result.fold(
                        onSuccess = { prompts ->
                            val total = HomeRepository.getLastTotalPrompts()

                            val mergedApi =
                                if (page == 1) prompts
                                else (_uiState.value.allPrompts + prompts)
                                    .distinctBy { it.id }

                            val mergedFinal = mergeWithLocalEngagement(mergedApi)

                            cachedPrompts = mergedFinal
                            lastCacheTime = now

                            _uiState.update {
                                it.copy(
                                    allPrompts = mergedFinal,
                                    totalPrompts = total,
                                    isLoading = false
                                )
                            }

                            promptRepository.syncPromptCache(mergedFinal)

                            currentPage = page
                            canLoadMore = mergedFinal.size < total && prompts.isNotEmpty()
                        },
                        onFailure = { throwable ->
                            _uiState.update { state ->
                                state.copy(
                                    error = throwable.localizedMessage ?: "Something went wrong"
                                )
                            }
                        }
                    )
                }
            }

            isLoadingAll = false
            Log.d(TAG, "loadAllPrompts in ${elapsed}ms")
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
    /* LOAD PROMPT BY ID */
    /* ---------------------------------------------------------------------- */

    fun loadPromptById(promptId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            homeRepository.getPromptById(promptId).collect { result ->
                result.fold(
                    onSuccess = { prompt ->
                        val merged = mergeWithLocalEngagement(listOf(prompt)).first()

                        promptRepository.syncPromptCache(
                            (promptRepository.observeAllPrompts().value + merged)
                                .distinctBy { it.id }
                        )

                        _uiState.update {
                            it.copy(
                                allPrompts = (it.allPrompts + merged).distinctBy { p -> p.id },
                                selectedPrompt = merged,
                                isLoading = false
                            )
                        }

                        merged.category?.let {
                            loadSimilarPrompts(it, merged.id)
                        }
                    },
                    onFailure = { throwable ->
                        _uiState.update { state ->
                            state.copy(
                                error = throwable.localizedMessage ?: "Failed to load prompts",
                                isLoading = false
                            )
                        }
                    }

                )
            }
        }
    }

    fun loadSimilarPrompts(category: String, currentPromptId: String) {
        viewModelScope.launch {
            homeRepository.getSimilarAiPrompts(category, currentPromptId)
                .collect { result ->
                    result.fold(
                        onSuccess = {
                            _uiState.update { s -> s.copy(similarPrompts = it) }
                        },
                        onFailure = {
                            _uiState.update { s -> s.copy(similarPrompts = emptyList()) }
                        }
                    )
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
            homeRepository.getCategories().collect { result ->
                result.fold(
                    onSuccess = { cats ->
                        val names =
                            cats.mapNotNull { it.name }.distinct().sorted()
                        _uiState.update {
                            it.copy(categories = listOf("All") + names)
                        }
                        categoriesLoaded = true
                    },
                    onFailure = { throwable ->
                        _uiState.update { state ->
                            state.copy(
                                error = throwable.localizedMessage ?: "Failed to load categories"
                            )
                        }
                    }

                )
            }
        }
    }

    /* ---------------------------------------------------------------------- */
    /* GEMINI */
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
