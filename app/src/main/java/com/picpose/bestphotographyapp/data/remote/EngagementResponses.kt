package com.picpose.bestphotographyapp.data.remote

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
