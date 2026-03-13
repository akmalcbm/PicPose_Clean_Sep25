/**
 * ---
 * File: StatsResponse.kt
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

data class StatsResponse(
    val total_prompts: Int,
    val total_likes: Int,
    val total_favorites: Int,
    val total_copies: Int,
    val total_views: Int
)
