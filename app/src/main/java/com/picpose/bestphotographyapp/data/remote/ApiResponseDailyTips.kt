package com.picpose.bestphotographyapp.data.remote

import com.google.gson.annotations.SerializedName

data class ApiResponseDailyTips<T>(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String?,
    @SerializedName("data") val data: T?
)
