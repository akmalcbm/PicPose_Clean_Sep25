package com.picpose.bestphotographyapp.data.repository

import android.util.Log
import com.picpose.bestphotographyapp.data.PromptRepository
import com.picpose.bestphotographyapp.data.database.dao.EngagementDao
import com.picpose.bestphotographyapp.data.database.entities.EngagementEntity
import com.picpose.bestphotographyapp.data.models.AIPrompt
import com.picpose.bestphotographyapp.data.network.ApiService
import com.picpose.bestphotographyapp.data.network.RetrofitClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class EngagementRepository @Inject constructor(
    private val engagementDao: EngagementDao,
    private val promptRepository: PromptRepository,
    private val api: ApiService
) {

    private val TAG = "EngagementRepo"

    /* ---------------------------------------------------- */
    /* 🔥 CENTRALIZED ENGAGEMENT MANAGEMENT */
    /* ---------------------------------------------------- */

    /**
     * Central like handler for ALL screens
     */
    suspend fun handleLike(promptId: String): LikeResult {
        Log.d(TAG, "🔄 handleLike called for: $promptId")

        // 1. Get current state
        val current = engagementDao.getById(promptId)
        val currentLiked = current?.isLiked ?: false
        val newLiked = !currentLiked

        // 2. Update local database
        val updatedAt = System.currentTimeMillis()
        val newState = current?.copy(
            isLiked = newLiked,
            updatedAt = updatedAt
        ) ?: EngagementEntity(
            promptId = promptId,
            isLiked = newLiked,
            updatedAt = updatedAt
        )

        engagementDao.upsert(newState)

        // 3. Sync with server
        try {
            if (newLiked) {
                api.incrementLike(promptId.toInt(), RetrofitClient.defaultApiKey)
                Log.d(TAG, "✅ Like incremented on server: $promptId")
            } else {
                api.decrementLike(promptId.toInt(), RetrofitClient.defaultApiKey)
                Log.d(TAG, "✅ Like decremented on server: $promptId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Server sync failed: ${e.message}")
            // Don't revert - keep optimistic UI update
            // Will retry on next sync
        }

        return LikeResult(
            promptId = promptId,
            isLiked = newLiked,
            newLikes = calculateNewLikes(currentLiked, newLiked)
        )
    }

    /**
     * Central bookmark handler for ALL screens
     */
    suspend fun handleBookmark(promptId: String): BookmarkResult {
        Log.d(TAG, "🔄 handleBookmark called for: $promptId")

        // 1. Get current state
        val current = engagementDao.getById(promptId)
        val currentBookmarked = current?.isFavorited ?: false
        val newBookmarked = !currentBookmarked

        // 2. Update local database
        val updatedAt = System.currentTimeMillis()
        val newState = current?.copy(
            isFavorited = newBookmarked,
            updatedAt = updatedAt
        ) ?: EngagementEntity(
            promptId = promptId,
            isFavorited = newBookmarked,
            updatedAt = updatedAt
        )

        engagementDao.upsert(newState)

        // 3. Sync with server
        try {
            if (newBookmarked) {
                api.incrementFavorite(promptId.toInt(), RetrofitClient.defaultApiKey)
                Log.d(TAG, "✅ Bookmark incremented on server: $promptId")
            } else {
                api.decrementFavorite(promptId.toInt(), RetrofitClient.defaultApiKey)
                Log.d(TAG, "✅ Bookmark decremented on server: $promptId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Server sync failed: ${e.message}")
        }

        return BookmarkResult(
            promptId = promptId,
            isBookmarked = newBookmarked,
            newFavorites = calculateNewFavorites(currentBookmarked, newBookmarked)
        )
    }

    private fun calculateNewLikes(
        wasLiked: Boolean,
        nowLiked: Boolean,
        currentLikes: Int = 0
    ): Int {
        return when {
            !wasLiked && nowLiked -> currentLikes + 1  // Like added
            wasLiked && !nowLiked -> max(0, currentLikes - 1)  // Like removed
            else -> currentLikes
        }
    }

    private fun calculateNewFavorites(
        wasBookmarked: Boolean,
        nowBookmarked: Boolean,
        currentFavorites: Int = 0
    ): Int {
        return when {
            !wasBookmarked && nowBookmarked -> currentFavorites + 1
            wasBookmarked && !nowBookmarked -> max(0, currentFavorites - 1)
            else -> currentFavorites
        }
    }

    /* ---------------------------------------------------- */
    /* DATA CLASSES FOR RESULT */
    /* ---------------------------------------------------- */

    data class LikeResult(
        val promptId: String,
        val isLiked: Boolean,
        val newLikes: Int
    )

    data class BookmarkResult(
        val promptId: String,
        val isBookmarked: Boolean,
        val newFavorites: Int
    )

    /* ---------------------------------------------------- */
    /* SNAPSHOT READ (One-time) */
    /* ---------------------------------------------------- */

    suspend fun getAllStates(): List<EngagementEntity> {
        return engagementDao.getAll()
    }

    suspend fun getEngagementState(promptId: String): EngagementEntity? {
        return engagementDao.getById(promptId)
    }

    // Alias for backward compatibility
    suspend fun getState(promptId: String): EngagementEntity? {
        return getEngagementState(promptId)
    }

    /* ---------------------------------------------------- */
    /* LIKE - NEW FUNCTIONS */
    /* ---------------------------------------------------- */

    suspend fun setLiked(promptId: String, liked: Boolean): Boolean {
        val current = engagementDao.getById(promptId)
        val updatedAt = System.currentTimeMillis()

        val newState = current?.copy(
            isLiked = liked,
            updatedAt = updatedAt
        ) ?: EngagementEntity(
            promptId = promptId,
            isLiked = liked,
            updatedAt = updatedAt
        )

        engagementDao.upsert(newState)
        return newState.isLiked
    }

    suspend fun toggleLike(promptId: String): Boolean {
        val current = engagementDao.getById(promptId)
        val newLiked = !(current?.isLiked ?: false)
        return setLiked(promptId, newLiked)
    }

    /* ---------------------------------------------------- */
    /* FAVORITE / BOOKMARK - NEW FUNCTIONS */
    /* ---------------------------------------------------- */

    suspend fun setFavorited(promptId: String, favorited: Boolean): Boolean {
        Log.d(TAG, "setFavorited: promptId=$promptId, favorited=$favorited")

        val current = engagementDao.getById(promptId)
        val updatedAt = System.currentTimeMillis()

        val newState = current?.copy(
            isFavorited = favorited,
            updatedAt = updatedAt
        ) ?: EngagementEntity(
            promptId = promptId,
            isFavorited = favorited,
            updatedAt = updatedAt
        )

        engagementDao.upsert(newState)

        // Verify the change
        val after = engagementDao.getById(promptId)
        Log.d(TAG, "After setFavorited: DB state = $after")

        return newState.isFavorited
    }

    suspend fun toggleFavorite(promptId: String): Boolean {
        val current = engagementDao.getById(promptId)
        val newFavorited = !(current?.isFavorited ?: false)
        return setFavorited(promptId, newFavorited)
    }

    /* ---------------------------------------------------- */
    /* VIEW - UPDATED WITH 5-SECOND DELAY SUPPORT */
    /* ---------------------------------------------------- */

    suspend fun registerView(promptId: String) {
        Log.d(TAG, "registerView called for: $promptId")

        val now = System.currentTimeMillis()
        val current = engagementDao.getById(promptId)

        if (current == null) {
            // First view
            engagementDao.upsert(
                EngagementEntity(
                    promptId = promptId,
                    localViewCount = 1,
                    pendingViewSync = 1,
                    updatedAt = now
                )
            )
        } else {
            // Increment local count
            val newLocalCount = current.localViewCount + 1
            val newPendingSync = current.pendingViewSync + 1

            engagementDao.upsert(
                current.copy(
                    localViewCount = newLocalCount,
                    pendingViewSync = newPendingSync,
                    updatedAt = now
                )
            )
        }

        // Sync with server in background
        syncViewWithServer(promptId)
    }

    /**
     * For manual view registration (when user stays for 5+ seconds)
     */
    suspend fun registerViewAfterDelay(promptId: String) {
        Log.d(TAG, "registerViewAfterDelay for: $promptId (5+ seconds)")
        registerView(promptId)
    }

    private suspend fun syncViewWithServer(promptId: String) {
        val state = engagementDao.getById(promptId) ?: return
        val pending = state.pendingViewSync

        if (pending <= 0) return

        try {
            // Sync with server
            api.incrementView(promptId.toInt(), RetrofitClient.defaultApiKey)

            // Mark as synced
            engagementDao.upsert(
                state.copy(pendingViewSync = 0)
            )
            Log.d(TAG, "View synced with server for: $promptId")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync view for $promptId: ${e.message}")
            // Keep pending count for retry later
        }
    }

    /* ---------------------------------------------------- */
    /* UTIL */
    /* ---------------------------------------------------- */

    suspend fun clearState(promptId: String) {
        engagementDao.delete(promptId)
        Log.d(TAG, "Cleared state for: $promptId")
    }

    suspend fun resetPendingSync(promptId: String) {
        val state = engagementDao.getById(promptId) ?: return
        engagementDao.upsert(state.copy(pendingViewSync = 0))
    }

    /* ---------------------------------------------------- */
    /* MERGE LOCAL ENGAGEMENT INTO PROMPTS (SNAPSHOT) */
    /* ---------------------------------------------------- */

    suspend fun mergeWithLocalEngagement(
        prompts: List<AIPrompt>
    ): List<AIPrompt> {
        val localStates = engagementDao.getAll()
            .associateBy { it.promptId }

        return prompts.map { prompt ->
            val local = localStates[prompt.id]

            if (local == null) {
                prompt
            } else {
                prompt.copy(
                    isLiked = local.isLiked,
                    isFavouriteBookmarked = local.isFavorited
                    //views = prompt.views + local.localViewCount
                )
            }
        }
    }

    /* ---------------------------------------------------- */
    /* 🔥 REACTIVE FLOWS */
    /* ---------------------------------------------------- */

    fun observeAllStates(): Flow<List<EngagementEntity>> {
        return engagementDao.observeAll()
    }

    fun observeEngagementState(promptId: String): Flow<EngagementEntity?> {
        return engagementDao.observeById(promptId)
    }

    /**
     * 🔥 FAVORITES — SINGLE SOURCE OF TRUTH
     */
    fun observeFavoritePrompts(): Flow<List<AIPrompt>> =
        combine(
            engagementDao.observeFavorites(),
            promptRepository.observeAllPrompts()
        ) { engagements, prompts ->
            Log.d(TAG, "observeFavoritePrompts: ${engagements.size} favorites, ${prompts.size} cached prompts")

            val favEngagements = engagements.filter { it.isFavorited }
            val promptMap = prompts.associateBy { it.id }

            favEngagements
                .sortedByDescending { it.updatedAt }
                .mapNotNull { engagement ->
                    promptMap[engagement.promptId]?.copy(
                        isFavouriteBookmarked = true,
                        isLiked = engagement.isLiked
                    )
                }
        }

    suspend fun getAllFavoritedPromptIds(): List<String> {
        return engagementDao.getAllFavoritedPromptIds()
    }

    /* ---------------------------------------------------- */
    /* NEW HELPER FUNCTIONS FOR COUNT MANAGEMENT */
    /* ---------------------------------------------------- */

    /**
     * Get local engagement-adjusted counts for display
     */
    fun getDisplayLikes(prompt: AIPrompt, localEngagement: EngagementEntity?): Int {
        val baseLikes = prompt.likes.coerceAtLeast(0)
        return when {
            localEngagement?.isLiked == true && !prompt.isLiked -> baseLikes + 1
            localEngagement?.isLiked == false && prompt.isLiked -> (baseLikes - 1).coerceAtLeast(0)
            else -> baseLikes
        }
    }

    fun getDisplayFavorites(prompt: AIPrompt, localEngagement: EngagementEntity?): Int {
        val baseFavorites = prompt.favorites.coerceAtLeast(0)
        return when {
            localEngagement?.isFavorited == true && !prompt.isFavouriteBookmarked -> baseFavorites + 1
            localEngagement?.isFavorited == false && prompt.isFavouriteBookmarked -> (baseFavorites - 1).coerceAtLeast(0)
            else -> baseFavorites
        }
    }

    fun getDisplayViews(prompt: AIPrompt, localEngagement: EngagementEntity?): Int {
        val serverViews = prompt.views.coerceAtLeast(0)
        val localViews = localEngagement?.localViewCount?.coerceAtLeast(0) ?: 0
        return serverViews + localViews
    }
}