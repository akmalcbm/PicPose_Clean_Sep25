/**
 * ---
 * File: V2ApiService.kt
 * Layer: Data
 * Project: PicPose
 *
 * Purpose:
 * Declares Retrofit endpoints used to communicate with backend services.
 *
 * Interactions:
 * Consumed by repositories to talk to backend APIs and map raw payloads into app models.
 *
 * Data Flow:
 * Repository -> Retrofit service -> Backend response -> Model mapping -> ViewModel/UI
 *
 * Maintainer Notes:
 * - Prefer backend-neutral mapping in repositories instead of leaking transport details into the UI.
 * - TODO: Add stricter error classification and retry policy where network flows are user-critical.
 * ---
 */

package com.picpose.bestphotographyapp.data.remote.api

import com.picpose.bestphotographyapp.data.remote.dto.v2.ApplyReferralCodeRequest
import com.picpose.bestphotographyapp.data.remote.dto.v2.BasicV2Response
import com.picpose.bestphotographyapp.data.remote.dto.v2.GetMyCodeResponseDto
import com.picpose.bestphotographyapp.data.remote.dto.v2.PackDetailsResponseDto
import com.picpose.bestphotographyapp.data.remote.dto.v2.PacksResponseDto
import com.picpose.bestphotographyapp.data.remote.dto.v2.PointsPackUnlockRequest
import com.picpose.bestphotographyapp.data.remote.dto.v2.PromptOfDayResponseDto
import com.picpose.bestphotographyapp.data.remote.dto.v2.ProgressResponseDto
import com.picpose.bestphotographyapp.data.remote.dto.v2.ReferralClaimResponseDto
import com.picpose.bestphotographyapp.data.remote.dto.v2.RewardAdPointsRequest
import com.picpose.bestphotographyapp.data.remote.dto.v2.RewardsHubDto
import com.picpose.bestphotographyapp.data.remote.dto.v2.StreakStatusDto
import com.picpose.bestphotographyapp.data.remote.dto.v2.UnlockPromptByAdRequest
import com.picpose.bestphotographyapp.data.remote.dto.v2.UnlockPromptByPointsRequest
import com.picpose.bestphotographyapp.data.remote.dto.v2.UnlockPromptByTokenRequest
import com.picpose.bestphotographyapp.data.remote.dto.v2.UnlockResponseDto
import com.picpose.bestphotographyapp.data.remote.dto.v2.V2ExperimentsResponseDto
import com.picpose.bestphotographyapp.data.remote.dto.v2.V2PromptDetailResponseDto
import com.picpose.bestphotographyapp.data.remote.dto.v2.V2PromptListResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface V2ApiService {
    @GET("api/v2/ai_posts/get_ai_posts.php")
    suspend fun getAiPosts(
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null,
        @Query("q") query: String? = null,
        @Query("category") category: String? = null,
        @Query("tag") tag: String? = null,
        @Query("popular") popular: Boolean? = null,
        @Query("status") status: String? = null,
        @Query("featured") featured: Boolean? = null,
    ): Response<V2PromptListResponseDto>

    @GET("api/v2/ai_posts/get_ai_post.php")
    suspend fun getAiPost(
        @Query("id") id: String,
    ): Response<V2PromptDetailResponseDto>

    @GET("api/v2/ai_posts/get_prompt_of_the_day.php")
    suspend fun getPromptOfTheDay(): Response<PromptOfDayResponseDto>

    @POST("api/v2/ai_posts/unlock_prompt_ad.php")
    suspend fun unlockPromptByAd(
        @Body request: UnlockPromptByAdRequest,
    ): Response<UnlockResponseDto>

    @POST("api/v2/ai_posts/unlock_prompt_points.php")
    suspend fun unlockPromptByPoints(
        @Body request: UnlockPromptByPointsRequest,
    ): Response<UnlockResponseDto>

    @POST("api/v2/ai_posts/unlock_prompt_token.php")
    suspend fun unlockPromptByToken(
        @Body request: UnlockPromptByTokenRequest,
    ): Response<UnlockResponseDto>

    @GET("api/v2/wallet/get_streak_status.php")
    suspend fun getStreakStatus(): Response<StreakStatusDto>

    @POST("api/v2/wallet/claim_daily_login.php")
    suspend fun claimDailyLogin(): Response<BasicV2Response>

    @POST("api/v2/wallet/reward_ad_points.php")
    suspend fun rewardAdPoints(
        @Body request: RewardAdPointsRequest,
    ): Response<BasicV2Response>

    @GET("api/v2/rewards/hub.php")
    suspend fun getRewardsHub(): Response<RewardsHubDto>

    @GET("api/v2/referrals/get_my_code.php")
    suspend fun getMyReferralCode(): Response<GetMyCodeResponseDto>

    @POST("api/v2/referrals/apply_code.php")
    suspend fun applyReferralCode(
        @Body request: ApplyReferralCodeRequest,
    ): Response<BasicV2Response>

    @POST("api/v2/referrals/claim_reward.php")
    suspend fun claimReferralReward(): Response<ReferralClaimResponseDto>

    @GET("api/v2/packs/get_packs.php")
    suspend fun getPacks(): Response<PacksResponseDto>

    @GET("api/v2/packs/get_pack_details.php")
    suspend fun getPackDetails(
        @Query("id") id: Int,
    ): Response<PackDetailsResponseDto>

    @POST("api/v2/packs/unlock_pack_points.php")
    suspend fun unlockPackWithPoints(
        @Body request: PointsPackUnlockRequest,
    ): Response<BasicV2Response>

    @GET("api/v2/progress/me.php")
    suspend fun getProgress(): Response<ProgressResponseDto>

    @GET("api/v2/feed/for_you.php")
    suspend fun getForYouFeed(
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null,
    ): Response<V2PromptListResponseDto>

    @GET("api/v2/experiments/me.php")
    suspend fun getExperiments(): Response<V2ExperimentsResponseDto>
}
