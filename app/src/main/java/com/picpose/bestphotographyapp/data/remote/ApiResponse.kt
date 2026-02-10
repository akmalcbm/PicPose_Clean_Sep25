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
