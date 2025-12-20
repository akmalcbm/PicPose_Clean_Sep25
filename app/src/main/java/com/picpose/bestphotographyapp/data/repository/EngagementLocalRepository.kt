package com.picpose.bestphotographyapp.data.repository

import com.picpose.bestphotographyapp.data.database.dao.EngagementDao
import com.picpose.bestphotographyapp.data.database.entities.EngagementEntity
import com.picpose.bestphotographyapp.data.models.AIPrompt
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EngagementLocalRepository @Inject constructor(
    private val dao: EngagementDao
) {

    /* ------------------------------------ */
    /* READ */
    /* ------------------------------------ */

    suspend fun getAllStates(): List<EngagementEntity> {
        return dao.getAll()
    }

    suspend fun getState(promptId: String): EngagementEntity? {
        return dao.getById(promptId)
    }

    /* ------------------------------------ */
    /* LIKE */
    /* ------------------------------------ */

    suspend fun toggleLike(promptId: String): Boolean {
        val current = dao.getById(promptId)

        val newState = current?.copy(
            isLiked = !current.isLiked
        ) ?: EngagementEntity(
            promptId = promptId,
            isLiked = true
        )

        dao.upsert(newState)
        return newState.isLiked
    }

    /* ------------------------------------ */
    /* FAVORITE */
    /* ------------------------------------ */

    suspend fun toggleFavorite(promptId: String): Boolean {
        val current = dao.getById(promptId)

        val newState = current?.copy(
            isFavorited = !current.isFavorited
        ) ?: EngagementEntity(
            promptId = promptId,
            isFavorited = true
        )

        dao.upsert(newState)
        return newState.isFavorited
    }

    /* ------------------------------------ */
    /* VIEW */
    /* ------------------------------------ */

    suspend fun incrementView(promptId: String) {
        val current = dao.getById(promptId)

        if (current == null) {
            dao.upsert(
                EngagementEntity(
                    promptId = promptId,
                    localViewCount = 1
                )
            )
        } else {
            dao.incrementView(promptId)
        }
    }

    /* ------------------------------------ */
    /* UTIL (Future use) */
    /* ------------------------------------ */

    suspend fun clearState(promptId: String) {
        dao.upsert(
            EngagementEntity(promptId = promptId)
        )
    }


    /* ------------------------------------ */
    /* 🔥 MERGE LOCAL ENGAGEMENT */
    /* ------------------------------------ */
    suspend fun mergeWithLocalEngagement(
        prompts: List<AIPrompt>
    ): List<AIPrompt> {

        val localStates = dao.getAll()
            .associateBy { it.promptId }

        return prompts.map { prompt ->
            val local = localStates[prompt.id]

            if (local == null) {
                prompt
            } else {
                prompt.copy(
                    isLiked = local.isLiked,
                    isFavouriteBookmarked = local.isFavorited,
                    //views = prompt.views + local.localViewCount
                )
            }
        }
    }


    fun observeAllStates(): Flow<List<EngagementEntity>> {
        return dao.observeAll()
    }


}
