/**
 * ---
 * File: V2PromptsRepository.kt
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

import android.util.Log
import com.picpose.bestphotographyapp.data.remote.dto.v2.PromptOfDayResponseDto
import com.picpose.bestphotographyapp.data.remote.dto.v2.UnlockResponseDto
import com.picpose.bestphotographyapp.data.remote.dto.v2.UnlockPromptByAdRequest
import com.picpose.bestphotographyapp.data.remote.dto.v2.UnlockPromptByPointsRequest
import com.picpose.bestphotographyapp.data.remote.dto.v2.UnlockPromptByTokenRequest
import com.picpose.bestphotographyapp.data.remote.dto.v2.V2PromptDto
import com.picpose.bestphotographyapp.data.remote.api.V2ApiService
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import retrofit2.Response

@Singleton
class V2PromptsRepository @Inject constructor(
    private val apiService: V2ApiService,
) {
    data class PromptPageResult(
        val items: List<V2PromptDto>,
        val total: Int? = null,
        val hasMore: Boolean = false,
    )

    suspend fun getPrompts(
        category: String? = null,
        query: String? = null,
        featuredOnly: Boolean = false,
        premiumOnly: Boolean? = null,
        limit: Int = 40,
        offset: Int = 0,
    ): Result<List<V2PromptDto>> = getPromptsPage(
        category = category,
        query = query,
        featuredOnly = featuredOnly,
        premiumOnly = premiumOnly,
        limit = limit,
        offset = offset,
    ).map { it.items }

    suspend fun getPromptsPage(
        category: String? = null,
        query: String? = null,
        featuredOnly: Boolean = false,
        premiumOnly: Boolean? = null,
        limit: Int = 40,
        offset: Int = 0,
    ): Result<PromptPageResult> = withContext(Dispatchers.IO) {
        runCatching {
            val response = apiService.getAiPosts(
                limit = limit,
                offset = offset,
                query = query?.takeIf { it.isNotBlank() },
                category = category?.takeIf { it.isNotBlank() && !it.equals("All", ignoreCase = true) },
                tier = when (premiumOnly) {
                    true -> "PREMIUM"
                    false -> "FREE"
                    null -> null
                },
                featured = featuredOnly.takeIf { it },
            )
            val body = response.body()
            ensureSuccess(response, body?.message, body?.success == true)
            val items = body?.data.orEmpty()
            val filtered = when (premiumOnly) {
                true -> items.filter { it.isPremium }
                false -> items.filter { !it.isPremium }
                null -> items
            }
            val backendHasMore = body?.hasMore == true
            val inferredHasMore = items.size >= limit
            PromptPageResult(
                items = filtered,
                total = body?.total?.takeIf { it > 0 },
                hasMore = backendHasMore || inferredHasMore,
            )
        }
    }

    suspend fun getPromptDetail(promptId: String): Result<V2PromptDto> = withContext(Dispatchers.IO) {
        runCatching {
            val response = apiService.getAiPost(promptId)
            val body = response.body()
            ensureSuccess(response, body?.message, body?.success == true)
            body?.data ?: error("Prompt not found")
        }
    }

    suspend fun unlockPromptWithPoints(promptId: String): Result<UnlockResponseDto> = withContext(Dispatchers.IO) {
        runCatching {
            val response = apiService.unlockPromptByPoints(UnlockPromptByPointsRequest(postId = promptId))
            parseUnlockResponse(response)
        }
    }

    suspend fun unlockPromptWithAd(promptId: String, adRewardId: String): Result<UnlockResponseDto> = withContext(Dispatchers.IO) {
        runCatching {
            val response = apiService.unlockPromptByAd(
                UnlockPromptByAdRequest(
                    postId = promptId,
                    adRewardId = adRewardId,
                )
            )
            parseUnlockResponse(response)
        }
    }

    suspend fun unlockPromptWithToken(promptId: String): Result<UnlockResponseDto> = withContext(Dispatchers.IO) {
        runCatching {
            val response = apiService.unlockPromptByToken(UnlockPromptByTokenRequest(postId = promptId))
            parseUnlockResponse(response)
        }
    }

    suspend fun getPromptOfTheDay(): Result<PromptOfDayResponseDto> = withContext(Dispatchers.IO) {
        runCatching {
            val response = apiService.getPromptOfTheDay()
            val body = response.body()
            ensureSuccess(response, body?.dayDate, body?.success == true)
            body ?: error("Empty prompt of the day response")
        }
    }

    private fun parseUnlockResponse(response: Response<UnlockResponseDto>): UnlockResponseDto {
        val body = response.body()
        ensureSuccess(response, body?.message, body?.success == true)
        return body ?: UnlockResponseDto(success = false, message = "Empty response")
    }

    private fun ensureSuccess(
        response: Response<*>,
        fallbackMessage: String?,
        success: Boolean,
    ) {
        if (response.isSuccessful && success) return
        throw V2ApiException(
            code = response.code(),
            message = response.errorBody()?.string()?.let(::extractMessage)
                ?: fallbackMessage
                ?: "Request failed (${response.code()})",
        )
    }

    private fun extractMessage(raw: String): String {
        return runCatching {
            val body = JSONObject(raw)
            val message = body.optString("message").takeIf { it.isNotBlank() }
            val required = body.optInt("required_points", Int.MIN_VALUE)
            val current = body.optInt("current_points", Int.MIN_VALUE)

            if (
                required != Int.MIN_VALUE &&
                current != Int.MIN_VALUE &&
                message?.contains("insufficient", ignoreCase = true) == true
            ) {
                "You need $required credits, but your balance is only $current."
            } else {
                message
            }
        }.getOrNull() ?: raw
    }

    companion object {
        private const val TAG = "V2PromptsRepository"
    }
}

class V2ApiException(
    val code: Int,
    override val message: String,
) : Exception(message)

class V2FeatureUnavailableException(
    override val message: String,
) : Exception(message)
