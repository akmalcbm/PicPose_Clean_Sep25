/**
 * ---
 * File: EngagementResponses.kt
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

package com.picpose.bestphotographyapp.data.remote.response

data class LikeResponse(
    val success: Boolean,
    val likes: Int
)

data class FavoriteResponse(
    val success: Boolean,
    val favorites: Int
)

data class ViewResponse(
    val success: Boolean,
    val views: Int
)

data class CopyResponse(
    val success: Boolean,
    val copies: Int
)
