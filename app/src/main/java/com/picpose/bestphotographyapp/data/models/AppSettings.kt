package com.picpose.bestphotographyapp.data.models

import com.google.gson.annotations.SerializedName

/**
 * Data model for app settings fetched from server
 * Includes AdMob settings, app info, and support details
 */
data class AppSettings(
    // AdMob settings
    @SerializedName("app_id") val appId: String = "",
    @SerializedName("banner1_id") val banner1Id: String = "",
    @SerializedName("banner2_id") val banner2Id: String = "",
    @SerializedName("interstitial1_id") val interstitial1Id: String = "",
    @SerializedName("interstitial2_id") val interstitial2Id: String = "",
    @SerializedName("native1_id") val native1Id: String = "",
    @SerializedName("native2_id") val native2Id: String = "",
    @SerializedName("native3_id") val native3Id: String = "",
    @SerializedName("rewarded1_id") val rewarded1Id: String = "",
    
    // App information
    @SerializedName("app_name") val appName: String = "PicPose",
    @SerializedName("app_version") val appVersion: String = "1.0.0",
    @SerializedName("tagline") val tagline: String = "",
    @SerializedName("description") val description: String = "",
    @SerializedName("about") val about: String = "",
    @SerializedName("developer") val developer: String = "",
    
    // Privacy & Policy
    @SerializedName("privacy_policy") val privacyPolicy: String = "",
    
    // Support information
    @SerializedName("support_email") val supportEmail: String = "",
    @SerializedName("support_phone") val supportPhone: String = ""
)

/**
 * Response wrapper for app settings API call
 */
data class AppSettingsResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: AppSettings? = null
)

/**
 * Support query request model
 */
data class SupportQueryRequest(
    @SerializedName("user_id") val userId: String? = null,
    @SerializedName("email") val email: String,
    @SerializedName("name") val name: String,
    @SerializedName("message") val message: String
)

/**
 * Support query response model
 */
data class SupportQueryResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String? = null
)