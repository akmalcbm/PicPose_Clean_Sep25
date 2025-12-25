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

@Singleton
class EngagementRepository @Inject constructor(
    private val engagementDao: EngagementDao,
    private val promptRepository: PromptRepository,
    private val api: ApiService
) {

    /* ---------------------------------------------------- */
    /* SNAPSHOT READ (One-time) */
    /* ---------------------------------------------------- */

    suspend fun getAllStates(): List<EngagementEntity> {
        return engagementDao.getAll()
    }

    suspend fun getState(promptId: String): EngagementEntity? {
        return engagementDao.getById(promptId)
    }

    /* ---------------------------------------------------- */
    /* LIKE */
    /* ---------------------------------------------------- */

    suspend fun toggleLike(promptId: String): Boolean {
        val current = engagementDao.getById(promptId)

        val newState = current?.copy(
            isLiked = !current.isLiked,
            updatedAt = System.currentTimeMillis()
        ) ?: EngagementEntity(
            promptId = promptId,
            isLiked = true,
            updatedAt = System.currentTimeMillis()
        )

        engagementDao.upsert(newState)
        return newState.isLiked
    }

    /* ---------------------------------------------------- */
    /* FAVORITE / BOOKMARK */
    /* ---------------------------------------------------- */

    suspend fun toggleFavorite(promptId: String): Boolean {

        Log.e("FAV_DEBUG", "toggleFavorite() called with promptId = $promptId")

        val current = engagementDao.getById(promptId)
        Log.e("FAV_DEBUG", "Before toggle DB state = $current")

        val newState = current?.copy(
            isFavorited = !current.isFavorited,
            updatedAt = System.currentTimeMillis()
        ) ?: EngagementEntity(
            promptId = promptId,
            isFavorited = true,
            updatedAt = System.currentTimeMillis()
        )

        engagementDao.upsert(newState)

        val after = engagementDao.getById(promptId)
        Log.e("FAV_DEBUG", "After toggle DB state = $after")

        return newState.isFavorited
    }


    /**
     * Snapshot helper (used earlier in project)
     */
    suspend fun getFavoritedPromptIds(): Set<String> {
        return engagementDao.getAll()
            .filter { it.isFavorited }
            .map { it.promptId }
            .toSet()
    }

    /* ---------------------------------------------------- */
    /* VIEW */
    /* ---------------------------------------------------- */

    /*suspend fun incrementView(promptId: String) {
        val now = System.currentTimeMillis()
        val current = engagementDao.getById(promptId)

        if (current == null) {
            // First ever view for this prompt
            engagementDao.upsert(
                EngagementEntity(
                    promptId = promptId,
                    localViewCount = 1,
                    isLiked = false,
                    isFavorited = false,
                    updatedAt = now
                )
            )
        } else {
            // Atomic increment + timestamp update
            engagementDao.incrementView(
                id = promptId,
                updatedAt = now
            )
        }
    }*/

    suspend fun registerView(promptId: String) {
        val now = System.currentTimeMillis()
        val current = engagementDao.getById(promptId)

        if (current == null) {
            engagementDao.upsert(
                EngagementEntity(
                    promptId = promptId,
                    localViewCount = 1,
                    pendingViewSync = 1,
                    updatedAt = now
                )
            )
        } else {
            engagementDao.incrementView(promptId, now)
        }

        syncViewWithServer(promptId)
    }


    private suspend fun syncViewWithServer(promptId: String) {
        val state = engagementDao.getById(promptId) ?: return
        val pending = state.pendingViewSync
        if (pending <= 0) return

        runCatching {
            repeat(pending) {
                api.incrementView(promptId.toInt(), RetrofitClient.defaultApiKey)
            }
            engagementDao.upsert(
                state.copy(pendingViewSync = 0)
            )
        }
    }






    /* ---------------------------------------------------- */
    /* UTIL */
    /* ---------------------------------------------------- */

    suspend fun clearState(promptId: String) {
        engagementDao.upsert(
            EngagementEntity(
                promptId = promptId,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    /* ---------------------------------------------------- */
    /* 🔥 MERGE LOCAL ENGAGEMENT INTO PROMPTS (SNAPSHOT) */
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
                    isFavouriteBookmarked = local.isFavorited,
                    views = local.localViewCount
                )
            }
        }
    }

    /* ---------------------------------------------------- */
    /* 🔥 REACTIVE FLOWS */
    /* ---------------------------------------------------- */

    /**
     * Observe ALL engagement states
     * Used by:
     * - All prompts screen
     * - Detail screen
     * - Icon sync
     */
    fun observeAllStates(): Flow<List<EngagementEntity>> {
        return engagementDao.observeAll()
    }

    /**
     * 🔥 FAVORITES — SINGLE SOURCE OF TRUTH
     * This is what FIXES the Favorites screen
     */
    fun observeFavoritePrompts(): Flow<List<AIPrompt>> =
        combine(
            engagementDao.observeFavorites(),          // EngagementEntity (Room)
            promptRepository.observeAllPrompts()       // Prompt cache (Memory)
        ) { engagements, prompts ->

            // 🔴 DEBUG START
            Log.e("FAV_DEBUG", "-----------------------------")
            Log.e("FAV_DEBUG", "observeFavoritePrompts() CALLED")
            Log.e("FAV_DEBUG", "Engagements size = ${engagements.size}")
            Log.e("FAV_DEBUG", "Prompts cache size = ${prompts.size}")

            val favEngagements = engagements.filter { it.isFavorited }
            Log.e(
                "FAV_DEBUG",
                "Favorited Engagement IDs = ${favEngagements.map { it.promptId }}"
            )

            val promptMap = prompts.associateBy { it.id }

            val result = favEngagements
                .sortedByDescending { it.updatedAt }   // 🔥 latest first
                .mapNotNull { engagement ->
                    val prompt = promptMap[engagement.promptId]

                    if (prompt == null) {
                        Log.e(
                            "FAV_DEBUG",
                            "❌ Prompt NOT FOUND for engagementId=${engagement.promptId}"
                        )
                        null
                    } else {
                        Log.e(
                            "FAV_DEBUG",
                            "✅ Matched promptId=${prompt.id} title=${prompt.title}"
                        )
                        prompt.copy(
                            isFavouriteBookmarked = true,
                            isLiked = engagement.isLiked
                        )
                    }
                }

            Log.e("FAV_DEBUG", "Final favorite prompts = ${result.size}")
            Log.e("FAV_DEBUG", "-----------------------------")

            result
        }

    suspend fun getAllFavoritedPromptIds(): List<String> {
        return engagementDao.getAllFavoritedPromptIds()
    }



}
