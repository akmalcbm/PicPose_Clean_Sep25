package com.picpose.bestphotographyapp.data.remote

/**
 * Generic wrapper for API responses of the form:
 * {
 *   "success": true,
 *   "message": "OK",
 *   "data": [...]
 * }
 */
data class ApiResponse<T>(
    val success: Boolean,
    val message: String? = null,
    val data: T? = null
)