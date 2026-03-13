/**
 * ---
 * File: DailyTip.kt
 * Layer: Data
 * Project: PicPose
 *
 * Purpose:
 * Defines DTOs and domain-facing models that represent prompt, user, stats, or settings data.
 *
 * Interactions:
 * Works with nearby classes in the same layer to keep responsibilities separated and easier to maintain.
 *
 * Data Flow:
 * Feature-specific flow; see adjacent ViewModels and repositories for the full path.
 *
 * Maintainer Notes:
 * - Keep responsibilities narrow and update this header when the file grows into a larger abstraction.
 * ---
 */

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
