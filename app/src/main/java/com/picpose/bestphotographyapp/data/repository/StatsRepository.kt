package com.picpose.bestphotographyapp.data.repository

import com.picpose.bestphotographyapp.data.database.StatsDao
import com.picpose.bestphotographyapp.data.database.StatsEntity
import com.picpose.bestphotographyapp.data.models.StatsResponse
import com.picpose.bestphotographyapp.data.network.ApiService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow

class StatsRepository(
    private val api: ApiService,
    private val dao: StatsDao
) {

    fun getQuickStats(apiKey: String?): Flow<Result<StatsResponse>> = flow {
        // Emit cached first
        dao.getStats().firstOrNull()?.let { emit(Result.success(it.toStatsResponse())) }

        // Fetch new data
        val response = api.getQuickStats(apiKey)
        if (response.isSuccessful && response.body()?.success == true) {
            val data = response.body()?.data
            if (data != null) {
                dao.insertStats(data.toEntity())
                emit(Result.success(data))
            }
        } else throw Exception(response.body()?.message ?: "Failed to load stats")
    }.catch { e ->
        // Fallback to cache
        val cached = dao.getStats().firstOrNull()
        if (cached != null) emit(Result.success(cached.toStatsResponse()))
        else emit(Result.failure(e))
    }

    private fun StatsResponse.toEntity() = StatsEntity(
        total_prompts = total_prompts,
        total_likes = total_likes,
        total_favorites = total_favorites,
        total_copies = total_copies,
        total_views = total_views, // ✅ Added mapping
        last_updated = System.currentTimeMillis()
    )

    private fun StatsEntity.toStatsResponse() = StatsResponse(
        total_prompts = total_prompts,
        total_likes = total_likes,
        total_favorites = total_favorites,
        total_copies = total_copies,
        total_views = total_views
    )
}
