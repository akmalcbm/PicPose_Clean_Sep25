package com.picpose.bestphotographyapp.data.models

import com.google.gson.annotations.SerializedName

data class DailyTip(
    @SerializedName("id") val id: String,
    @SerializedName("tip") val tip: String,
    @SerializedName("isActive") val isActive: Boolean,
    @SerializedName("order") val order: Int,
    @SerializedName("createdAt") val createdAt: String?,
    @SerializedName("updatedAt") val updatedAt: String?
)
