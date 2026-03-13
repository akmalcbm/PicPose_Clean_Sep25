/**
 * ---
 * File: ApiResponse.kt
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

/**
 * Generic wrapper for API responses of the form:
 * {
 *   "success": true,
 *   "message": "OK",
 *   "total": 188,
 *   "data": [...]
 * }
 */
@Keep
data class ApiResponse<T>(
    val success: Boolean,
    val message: String? = null,
    val data: T? = null,
    val total: Int? = null        // ✅ NEW
)
