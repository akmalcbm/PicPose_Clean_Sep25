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
    private val likedDao: LikedPromptDao,
    private val favoriteDao: FavoritePromptDao
) {

    suspend fun toggleLike(prompt: AIPrompt): AIPrompt {
        val liked = likedDao.isLiked(prompt.id)

        if (liked) {
            likedDao.removeLiked(prompt.id)
        } else {
            likedDao.addLiked(LikedPrompt(prompt.id))
        }

        return prompt.copy(
            isLiked = !liked,
            likes = (prompt.likes ?: 0) + if (liked) -1 else 1
        )
    }

    suspend fun toggleFavorite(prompt: AIPrompt): AIPrompt {
        val fav = favoriteDao.isBookmarked(prompt.id)

        if (fav) {
            favoriteDao.removeFromFavorites(prompt.id)
        } else {
            favoriteDao.addToFavorites(prompt.toFavoritePrompt())
        }

        return prompt.copy(
            isFavouriteBookmarked = !fav,
            favorites = (prompt.favorites ?: 0) + if (fav) -1 else 1
        )
    }

    suspend fun incrementView(prompt: AIPrompt): AIPrompt {
        return prompt.copy(
            views = (prompt.views ?: 0) + 1
        )
    }
}
