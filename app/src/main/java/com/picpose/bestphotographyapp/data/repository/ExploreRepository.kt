/**
 * ---
 * File: ExploreRepository.kt
 * Layer: Data
 * Project: PicPose
 *
 * Purpose:
 * Coordinates data access, merges local and remote sources, and exposes results to the presentation layer.
 *
 * Interactions:
 * Sits between ViewModels and data sources, combining Retrofit, Room, and local caches into UI-ready results.
 *
 * Data Flow:
 * ViewModel -> Repository -> Network service / Room DAO -> Result returned to UI state
 *
 * Maintainer Notes:
 * - Keep source-of-truth rules explicit when mixing Room, in-memory cache, and network responses.
 * - TODO: Consider extracting use cases if repository logic continues to grow across features.
 * ---
 */

package com.picpose.bestphotographyapp.data.repository

import android.content.Context
import com.picpose.bestphotographyapp.data.database.AppDatabase
import com.picpose.bestphotographyapp.data.database.FavoritePrompt
import com.picpose.bestphotographyapp.data.database.LikedPrompt
import com.picpose.bestphotographyapp.data.models.AIPrompt
import com.picpose.bestphotographyapp.data.network.ApiService
import com.picpose.bestphotographyapp.data.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class ExploreRepository(
    context: Context,
    private val apiService: ApiService = RetrofitClient.apiService,
    private val apiKey: String? = RetrofitClient.defaultApiKey
) {

    private val db = AppDatabase.getDatabase(context)
    private val favoriteDao = db.favoriteDao()
    private val likedDao = db.likedPromptDao()

    private val requestApiKey: String? = apiKey ?: RetrofitClient.defaultApiKey

    // -------------------------------------------------
    // ❤️ LIKE SYSTEM (Only Like increments server)
    // -------------------------------------------------
    fun togglePromptLike(prompt: AIPrompt): Flow<Result<AIPrompt>> = flow {
        try {
            val isLiked = withContext(Dispatchers.IO) {
                likedDao.isLiked(prompt.id)
            }

            if (!isLiked) {
                // Save locally
                withContext(Dispatchers.IO) {
                    likedDao.addLiked(LikedPrompt(prompt.id))
                }

                // Server LIKE++
                try {
                    apiService.incrementLike(prompt.id.toInt(), requestApiKey)
                } catch (_: Exception) { }

                emit(
                    Result.success(
                        prompt.copy(
                            isLiked = true,
                            likes = prompt.likes + 1
                        )
                    )
                )

            } else {
                // UNLIKE → local only
                withContext(Dispatchers.IO) {
                    likedDao.removeLiked(prompt.id)
                }

                emit(
                    Result.success(
                        prompt.copy(
                            isLiked = false,
                            likes = (prompt.likes - 1).coerceAtLeast(0)
                        )
                    )
                )
            }

        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    // -------------------------------------------------
// 🔖 BOOKMARK SYSTEM (Server ++ ONLY when adding)
// -------------------------------------------------
    fun togglePromptBookmark(prompt: AIPrompt): Flow<Result<AIPrompt>> = flow {
        try {
            val isFav = withContext(Dispatchers.IO) {
                favoriteDao.isBookmarked(prompt.id)
            }

            if (!isFav) {
                // ✅ Save only intent locally
                withContext(Dispatchers.IO) {
                    favoriteDao.addToFavorites(
                        FavoritePrompt(promptId = prompt.id)
                    )
                }

                // ✅ Server favorite++
                try {
                    apiService.incrementFavorite(prompt.id.toInt(), requestApiKey)
                } catch (_: Exception) { }

                emit(
                    Result.success(
                        prompt.copy(isFavouriteBookmarked = true)
                    )
                )

            } else {
                // ❌ Remove local only
                withContext(Dispatchers.IO) {
                    favoriteDao.removeFromFavorites(prompt.id)
                }

                emit(
                    Result.success(
                        prompt.copy(isFavouriteBookmarked = false)
                    )
                )
            }

        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

}
