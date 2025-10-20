package com.picpose.bestphotographyapp.data.models

import com.google.gson.annotations.SerializedName

/**
 * Data model for AdMob app settings fetched from server
 */
data class AppSettings(
    @SerializedName("app_id") val appId: String = "",
    @SerializedName("banner1_id") val banner1Id: String = "",
    @SerializedName("banner2_id") val banner2Id: String = "",
    @SerializedName("interstitial1_id") val interstitial1Id: String = "",
    @SerializedName("interstitial2_id") val interstitial2Id: String = "",
    @SerializedName("native1_id") val native1Id: String = "",
    @SerializedName("native2_id") val native2Id: String = "",
    @SerializedName("native3_id") val native3Id: String = "",
    @SerializedName("rewarded1_id") val rewarded1Id: String = ""
)

/**
 * Response wrapper for app settings API call
 */
data class AppSettingsResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: AppSettings? = null
)