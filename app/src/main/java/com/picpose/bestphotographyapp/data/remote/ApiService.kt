package com.picpose.bestphotographyapp.data.remote

import com.picpose.bestphotographyapp.data.models.DailyTip
import retrofit2.Response
import retrofit2.http.GET

interface ApiService {
    @GET("api/get_daily_tips.php")
    suspend fun getDailyTips(): Response<ApiResponseDailyTips<List<DailyTip>>>

    // other endpoints...
}
