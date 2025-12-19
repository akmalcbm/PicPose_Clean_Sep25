package com.picpose.bestphotographyapp.data.repository

import com.picpose.bestphotographyapp.data.database.FavoritePrompt
import com.picpose.bestphotographyapp.data.database.FavoritePromptDao
import com.picpose.bestphotographyapp.data.database.LikedPrompt
import com.picpose.bestphotographyapp.data.database.LikedPromptDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EngagementLocalRepository @Inject constructor(
    private val likedDao: LikedPromptDao,
    private val favoriteDao: FavoritePromptDao
) {

    suspend fun toggleLikeLocal(promptId: String): Boolean {
        val isLiked = likedDao.isLiked(promptId)
        if (isLiked) {
            likedDao.removeLiked(promptId)
        } else {
            likedDao.addLiked(LikedPrompt(promptId))
        }
        return !isLiked
    }

    suspend fun toggleFavoriteLocal(promptId: String): Boolean {
        val isFav = favoriteDao.isBookmarked(promptId)
        if (isFav) {
            favoriteDao.removeFromFavorites(promptId)
        } else {
            favoriteDao.addToFavorites(FavoritePrompt(promptId))
        }
        return !isFav
    }
}

