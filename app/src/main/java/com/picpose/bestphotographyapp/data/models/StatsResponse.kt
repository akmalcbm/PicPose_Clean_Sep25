package com.picpose.bestphotographyapp.data.models

data class StatsResponse(
    val total_prompts: Int,
    val total_likes: Int,
    val total_favorites: Int,
    val total_copies: Int,
    val total_views: Int
)
