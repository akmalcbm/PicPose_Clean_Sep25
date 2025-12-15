package com.picpose.bestphotographyapp.data.models

import com.google.gson.annotations.SerializedName

// 🔹 Data Transfer Object (matches API JSON structure)
data class PostDto(
    @SerializedName("id") val id: String = "",
    @SerializedName("title") val title: String = "",
    @SerializedName("excerpt") val excerpt: String? = null,
    @SerializedName("shortPrompt") val shortPrompt: String? = null,
    @SerializedName("fullPrompt") val fullPrompt: String? = null,
    @SerializedName("content") val content: String? = null,

    // ✅ Primary and secondary image URLs
    @SerializedName("featured_image") val featuredImage: String? = null,
    @SerializedName("imageUrl") val imageUrl: String? = null,
    @SerializedName("imageUrl2") val imageUrl2: String? = null,

    @SerializedName("category") val category: String? = null,
    @SerializedName("tags") val tags: List<String> = emptyList(),

    // ✅ Stats
    @SerializedName("likes") val likes: Int = 0,
    @SerializedName("favorites") val favorites: Int = 0,
    @SerializedName("copies") val copies: Int = 0,
    @SerializedName("views") val views: Int = 0,

    // ✅ Metadata
    @SerializedName("isPopular") val isPopular: Boolean = false,
    @SerializedName("isFeatured") val isFeatured: Boolean = false,
    @SerializedName("status") val status: String? = "published",
    @SerializedName("priority") val priority: Int = 0,

    // ✅ Creator info (optional future-proof)
    @SerializedName("createdBy") val createdBy: String? = null,
    @SerializedName("createdAt") val createdAt: String? = null,
    @SerializedName("created_at") val created_at: String? = null,
    @SerializedName("updatedAt") val updatedAt: String? = null
)

data class PostResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: List<PostDto> = emptyList()
)

// 🔹 Domain model used by UI layer
data class Post(
    val id: String,
    val title: String,
    val description: String = "",
    val image: String = "",
    val image2: String = "",
    val category: String = "",
    val author: String = "",
    val createdAt: String = "",
    val updatedAt: String = "",
    val likes: Int = 0,
    val favorites: Int = 0,
    val copies: Int = 0,
    val views: Int = 0,
    val isPopular: Boolean = false,
    val isFeatured: Boolean = false,
    val tags: List<String> = emptyList(),
    val isLiked: Boolean = false
)

// 🔹 Mapper (from DTO → Domain)
fun PostDto.toPost(): Post {
    val desc = excerpt ?: shortPrompt ?: fullPrompt ?: content ?: ""
    val img = featuredImage ?: imageUrl ?: ""
    val img2 = imageUrl2 ?: ""
    val created = createdAt ?: created_at ?: ""
    val updated = updatedAt ?: ""

    return Post(
        id = id,
        title = title,
        description = desc,
        image = img,
        image2 = img2,
        category = category ?: "",
        author = createdBy ?: "",
        createdAt = created,
        updatedAt = updated,
        likes = likes,
        favorites = favorites,
        copies = copies,
        views = views,
        isPopular = isPopular,
        isFeatured = isFeatured,
        tags = tags,
        isLiked = false
    )
}

// 🔹 Simple Category model
data class Category(
    val id: String,
    val name: String,
    val description: String,
    val image: String,
    val post_count: Int = 0
)
