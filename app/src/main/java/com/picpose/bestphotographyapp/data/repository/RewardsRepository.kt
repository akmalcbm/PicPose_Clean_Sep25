/**
 * ---
 * File: RewardsRepository.kt
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

import com.picpose.bestphotographyapp.data.local.datastore.ReferralCodeCache
import com.picpose.bestphotographyapp.data.local.datastore.RewardsHubCache
import com.picpose.bestphotographyapp.data.remote.dto.v2.ApplyReferralCodeRequest
import com.picpose.bestphotographyapp.data.remote.dto.v2.BasicV2Response
import com.picpose.bestphotographyapp.data.remote.dto.v2.GetMyCodeResponseDto
import com.picpose.bestphotographyapp.data.remote.dto.v2.PackDetailsResponseDto
import com.picpose.bestphotographyapp.data.remote.dto.v2.PacksResponseDto
import com.picpose.bestphotographyapp.data.remote.dto.v2.PointsPackUnlockRequest
import com.picpose.bestphotographyapp.data.remote.dto.v2.ProgressResponseDto
import com.picpose.bestphotographyapp.data.remote.dto.v2.ReferralClaimResponseDto
import com.picpose.bestphotographyapp.data.remote.dto.v2.RewardAdPointsRequest
import com.picpose.bestphotographyapp.data.remote.dto.v2.RewardsHubDto
import com.picpose.bestphotographyapp.data.remote.dto.v2.StreakStatusDto
import com.picpose.bestphotographyapp.data.remote.dto.v2.V2ExperimentsResponseDto
import com.picpose.bestphotographyapp.data.remote.dto.v2.V2PromptListResponseDto
import com.picpose.bestphotographyapp.data.remote.api.V2ApiService
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import retrofit2.Response

@Singleton
class RewardsRepository @Inject constructor(
    private val apiService: V2ApiService,
    private val rewardsHubCache: RewardsHubCache,
    private val referralCodeCache: ReferralCodeCache,
) {
    val cachedHub: Flow<RewardsHubDto?> = rewardsHubCache.cachedHub

    suspend fun refreshHub(userIdForCache: String? = null): Result<RewardsHubDto> = withContext(Dispatchers.IO) {
        runCatching {
            val response = apiService.getRewardsHub()
            val body = response.body()
            ensureSuccess(response, body?.message, body?.success == true)
            val hub = body ?: error("Empty rewards hub response")
            rewardsHubCache.save(hub)
            val referralCode = hub.referral?.myCode ?: hub.referral?.code
            if (!userIdForCache.isNullOrBlank() && !referralCode.isNullOrBlank()) {
                referralCodeCache.save(userIdForCache, referralCode)
            }
            hub
        }
    }

    suspend fun getCachedHub(): RewardsHubDto? = rewardsHubCache.readOnce()

    suspend fun getStreakStatus(): Result<StreakStatusDto> = withContext(Dispatchers.IO) {
        runCatching {
            val response = apiService.getStreakStatus()
            val body = response.body()
            ensureSuccess(response, body?.message, body?.success == true)
            body ?: error("Empty streak response")
        }
    }

    suspend fun claimDailyLogin(): Result<BasicV2Response> = withContext(Dispatchers.IO) {
        runCatching {
            val response = apiService.claimDailyLogin()
            val body = response.body()
            ensureSuccess(response, body?.message, body?.success == true)
            body ?: error("Empty claim response")
        }
    }

    suspend fun rewardAdPoints(adRewardId: String): Result<BasicV2Response> = withContext(Dispatchers.IO) {
        runCatching {
            val response = apiService.rewardAdPoints(RewardAdPointsRequest(adRewardId))
            val body = response.body()
            ensureSuccess(response, body?.message, body?.success == true)
            body ?: error("Empty reward response")
        }
    }

    suspend fun getMyCode(userIdForCache: String): Result<GetMyCodeResponseDto> = withContext(Dispatchers.IO) {
        runCatching {
            val response = apiService.getMyReferralCode()
            val body = response.body()
            ensureSuccess(response, body?.message, body?.success == true)
            val codeResponse = body ?: error("Empty code response")
            val code = codeResponse.code.orEmpty()
            if (code.isNotBlank()) {
                referralCodeCache.save(userIdForCache, code)
            }
            codeResponse
        }
    }

    suspend fun getCachedReferralCode(userId: String): String? = referralCodeCache.readOnce(userId)

    suspend fun applyReferralCode(code: String): Result<BasicV2Response> = withContext(Dispatchers.IO) {
        runCatching {
            val response = apiService.applyReferralCode(ApplyReferralCodeRequest(code))
            val body = response.body()
            ensureSuccess(response, body?.message, body?.success == true)
            body ?: error("Empty apply response")
        }
    }

    suspend fun claimReferralReward(): Result<ReferralClaimResponseDto> = withContext(Dispatchers.IO) {
        runCatching {
            val response = apiService.claimReferralReward()
            val body = response.body()
            ensureSuccess(response, body?.message, body?.success == true)
            body ?: error("Empty claim response")
        }
    }

    suspend fun getPacks(): Result<PacksResponseDto> = withContext(Dispatchers.IO) {
        runCatching {
            val response = apiService.getPacks()
            val body = response.body()
            ensureSuccess(response, body?.message, body?.success == true)
            body ?: error("Empty packs response")
        }
    }

    suspend fun getPackDetails(packId: Int): Result<PackDetailsResponseDto> = withContext(Dispatchers.IO) {
        runCatching {
            val response = apiService.getPackDetails(packId)
            val body = response.body()
            ensureSuccess(response, body?.message, body?.success == true)
            body ?: error("Empty pack details response")
        }
    }

    suspend fun unlockPack(packId: Int): Result<BasicV2Response> = withContext(Dispatchers.IO) {
        runCatching {
            val response = apiService.unlockPackWithPoints(PointsPackUnlockRequest(packId))
            val body = response.body()
            ensureSuccess(response, body?.message, body?.success == true)
            body ?: error("Empty pack unlock response")
        }
    }

    suspend fun getProgress(): Result<ProgressResponseDto> = withContext(Dispatchers.IO) {
        runCatching {
            val response = apiService.getProgress()
            val body = response.body()
            ensureSuccess(response, body?.message, body?.success == true)
            body ?: error("Empty progress response")
        }
    }

    suspend fun getForYou(limit: Int = 20, offset: Int = 0): Result<V2PromptListResponseDto> = withContext(Dispatchers.IO) {
        runCatching {
            val response = apiService.getForYouFeed(limit = limit, offset = offset)
            val body = response.body()
            ensureSuccess(response, body?.message, body?.success == true)
            body ?: error("Empty for you response")
        }
    }

    suspend fun getExperiments(): Result<V2ExperimentsResponseDto> = withContext(Dispatchers.IO) {
        runCatching {
            val response = apiService.getExperiments()
            val body = response.body()
            ensureSuccess(response, body?.message, body?.success == true)
            body ?: error("Empty experiments response")
        }
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
            JSONObject(raw).optString("message").takeIf { it.isNotBlank() }
        }.getOrNull() ?: raw
    }
}
