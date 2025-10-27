package com.picpose.bestphotographyapp.data.models

import com.google.gson.annotations.SerializedName

/**
 * Data model for app settings fetched from server
 * Matches API structure from /api/get_app_settings.php
 */
data class AppSettings(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("admin_name") val adminName: String = "",
    @SerializedName("app_name") val appName: String = "PicPose",
    @SerializedName("tagline") val tagline: String = "",
    @SerializedName("description") val description: String = "",
    @SerializedName("google_play_url") val googlePlayUrl: String = "",
    @SerializedName("admob") val admob: Admob = Admob(),
    @SerializedName("contact") val contact: Contact = Contact(),
    @SerializedName("policies") val policies: Policies = Policies(),
    @SerializedName("about") val about: About = About(),
    @SerializedName("meta") val meta: Meta = Meta()
) {
    // Backward compatibility properties for existing code
    val appId: String get() = admob.appId
    val banner1Id: String get() = admob.banner1Id
    val banner2Id: String get() = admob.banner2Id
    val interstitial1Id: String get() = admob.interstitial1Id
    val interstitial2Id: String get() = admob.interstitial2Id
    val native1Id: String get() = admob.native1Id
    val native2Id: String get() = admob.native2Id
    val native3Id: String get() = admob.native3Id
    val rewarded1Id: String get() = admob.rewarded1Id
    val supportEmail: String get() = contact.email
    val supportPhone: String get() = contact.phone
    val privacyPolicy: String get() = policies.privacyPolicyHtml
    val termsConditions: String get() = policies.termsConditionsHtml
    val appVersion: String get() = "1.0.0" // Can be retrieved from BuildConfig
    val developer: String get() = adminName
}

/**
 * AdMob configuration
 */
data class Admob(
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
 * Contact information
 */
data class Contact(
    @SerializedName("email") val email: String = "",
    @SerializedName("phone") val phone: String = ""
)

/**
 * Policies (Privacy & Terms)
 */
data class Policies(
    @SerializedName("privacy_policy_html") val privacyPolicyHtml: String = "",
    @SerializedName("terms_conditions_html") val termsConditionsHtml: String = "",
    @SerializedName("privacy_policy_text") val privacyPolicyText: String = "",
    @SerializedName("terms_conditions_text") val termsConditionsText: String = ""
)

/**
 * About information
 */
data class About(
    @SerializedName("html") val html: String = "",
    @SerializedName("text") val text: String = ""
)

/**
 * Metadata (timestamps)
 */
data class Meta(
    @SerializedName("created_at") val createdAt: String = "",
    @SerializedName("updated_at") val updatedAt: String = ""
)

/**
 * Response wrapper for app settings API call
 */
data class AppSettingsResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: AppSettings = AppSettings(),
    @SerializedName("meta") val meta: Map<String, Any?>? = null
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