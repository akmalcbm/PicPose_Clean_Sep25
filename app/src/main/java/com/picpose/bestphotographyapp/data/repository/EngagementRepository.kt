package com.picpose.bestphotographyapp.data.repository

import com.picpose.bestphotographyapp.data.database.FavoritePromptDao
import com.picpose.bestphotographyapp.data.database.LikedPrompt
import com.picpose.bestphotographyapp.data.database.LikedPromptDao
import com.picpose.bestphotographyapp.data.models.AIPrompt
import com.picpose.bestphotographyapp.data.network.ApiService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EngagementRepository @Inject constructor(
    private val api: ApiService,
    private val likedDao: LikedPromptDao,
    private val favoriteDao: FavoritePromptDao
) {

    /* ---------------- LIKE ---------------- */

    suspend fun toggleLike(prompt: AIPrompt): AIPrompt {
        val alreadyLiked = likedDao.isLiked(prompt.id)

        return if (alreadyLiked) {
            likedDao.removeLiked(prompt.id)

            prompt.copy(
                isLiked = false,
                likes = (prompt.likes - 1).coerceAtLeast(0)
            )
        } else {
            likedDao.addLiked(LikedPrompt(prompt.id))
            api.incrementLike(prompt.id.toInt())

            prompt.copy(
                isLiked = true,
                likes = prompt.likes + 1
            )
        }
    }

    /* ---------------- FAVORITE ---------------- */

    suspend fun toggleFavorite(prompt: AIPrompt): AIPrompt {
        val alreadyFav = favoriteDao.isBookmarked(prompt.id)

        return if (alreadyFav) {
            favoriteDao.removeFromFavorites(prompt.id)
            prompt.copy(isFavorited = false)
        } else {
            favoriteDao.addToFavorites(prompt.toFavoritePrompt())
            api.incrementFavorite(prompt.id.toInt())
            prompt.copy(isFavorited = true)
        }
    }

    /* ---------------- VIEW ---------------- */

    suspend fun incrementView(prompt: AIPrompt): AIPrompt {
        api.incrementView(prompt.id.toInt())
        return prompt.copy(views = prompt.views + 1)
    }

    /* ---------------- SHARE ---------------- */

    suspend fun incrementShare(promptId: Int) {
        api.incrementCopy(promptId)
    }
}