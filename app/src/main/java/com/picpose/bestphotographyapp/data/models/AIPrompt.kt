package com.picpose.bestphotographyapp.data.models

import com.google.gson.annotations.SerializedName

data class AIPrompt(
    val id: String,
    val title: String,
    val shortPrompt: String? = null,
    val fullPrompt: String? = null,

    // ✅ Multiple images supported
    @SerializedName("imageUrl") val imageUrl: String? = null,
    @SerializedName("imageUrl2") val imageUrl2: String? = null,

    val category: String? = null,
    val tags: List<String>? = emptyList(),

    // ✅ All stats included
    var likes: Int = 0,
    var favorites: Int = 0,
    var copies: Int = 0,
    var views: Int = 0,

    val isPopular: Boolean = false,
    val isFeatured: Boolean = false,

    // ✅ Mutable favorites for UI toggling
    var isBookmarked: Boolean = false,  //Bookmark = isFavorite Added/ Removed from the Favourite List
    var isLikes: Boolean = false,       // Likes = Likes Increase / Decrease

    val status: String? = "published",
    val priority: Int = 0,
    val createdAt: String? = null,
    val updatedAt: String? = null
)
