/**
 * ---
 * File: GuideEngagementResponses.kt
 * Layer: Data
 * Project: PicPose
 *
 * Purpose:
 * Supports the PicPose app with feature-specific models, helpers, or UI building blocks.
 *
 * Interactions:
 * Consumed by repositories to talk to backend APIs and map raw payloads into app models.
 *
 * Data Flow:
 * Repository -> Retrofit service -> Backend response -> Model mapping -> ViewModel/UI
 *
 * Maintainer Notes:
 * - Keep responsibilities narrow and update this header when the file grows into a larger abstraction.
 * ---
 */

package com.picpose.bestphotographyapp.data.remote

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
data class GuideViewPayload(
    @SerializedName("id") val id: String = "",
    @SerializedName("views") val views: Int = 0,
    @SerializedName("views_total") val viewsTotal: Int = 0
)

@Keep
data class GuideLikePayload(
    @SerializedName("id") val id: String = "",
    @SerializedName("likes") val likes: Int = 0,
    @SerializedName("likes_total") val likesTotal: Int = 0,
    @SerializedName("liked") val liked: Boolean = false,
    @SerializedName("is_liked_by_user") val isLikedByUser: Boolean = false
)
