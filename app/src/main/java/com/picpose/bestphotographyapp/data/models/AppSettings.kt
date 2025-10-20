package com.picpose.bestphotographyapp.data.models

import com.google.gson.annotations.SerializedName

/**
 * Represents the entire App Settings response returned by:
 * https://picpose.iamakmal.in/api/get_app_settings.php

🧩 Example Usage in Repository / ViewModel
// Retrofit interface
interface ApiService {
@GET("api/get_app_settings.php")
suspend fun getAppSettings(): AppSettingsResponse
}

// Inside your Repository or ViewModel
val response = apiService.getAppSettings()
if (response.success && response.data != null) {
val appName = response.data.appName
val bannerId = response.data.admob?.banner1Id
val privacyPolicyHtml = response.data.policies?.privacyPolicyHtml
}

 */


data class AppSettingsResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("data") val data: AppSettingsData? = null,
    @SerializedName("meta") val meta: MetaData? = null
)

/**
 * Main data object containing app details, AdMob config, and policies.
 */
data class AppSettingsData(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("admin_name") val adminName: String = "",
    @SerializedName("app_name") val appName: String = "",
    @SerializedName("tagline") val tagline: String = "",
    @SerializedName("description") val description: String = "",
    @SerializedName("google_play_url") val googlePlayUrl: String = "",
    @SerializedName("admob") val admob: AdMobSettings? = null,
    @SerializedName("policies") val policies: AppPolicies? = null
)

/**
 * Nested object for AdMob configuration.
 */
data class AdMobSettings(
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
 * Nested object for app policy information.
 */
data class AppPolicies(
    @SerializedName("privacy_policy_html") val privacyPolicyHtml: String = "",
    @SerializedName("terms_conditions_html") val termsConditionsHtml: String = "",
    @SerializedName("privacy_policy_text") val privacyPolicyText: String = "",
    @SerializedName("terms_conditions_text") val termsConditionsText: String = ""
)

/**
 * Meta info (like timestamp, version, etc.)
 */
data class MetaData(
    @SerializedName("generated_at") val generatedAt: String = ""
)
