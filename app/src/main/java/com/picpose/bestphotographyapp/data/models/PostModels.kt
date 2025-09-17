// PostModels.kt
package com.picpose.bestphotographyapp.data.models

import com.google.gson.annotations.SerializedName

// Post DTO returned by your PHP endpoints
data class PostDto(
    @SerializedName("id") val id: String = "",
    @SerializedName("title") val title: String = "",
    // API sometimes uses 'excerpt' or 'shortPrompt' etc. Accept both.
    @SerializedName("excerpt") val excerpt: String? = null,
    @SerializedName("shortPrompt") val shortPrompt: String? = null,
    @SerializedName("fullPrompt") val fullPrompt: String? = null,
    @SerializedName("content") val content: String? = null,
    @SerializedName("featured_image") val featured_image: String? = null,
    @SerializedName("imageUrl") val imageUrl: String? = null,
    @SerializedName("category") val category: String? = null,
    @SerializedName("tags") val tags: List<String> = emptyList(),
    @SerializedName("likes") val likes: Int = 0,
    @SerializedName("views") val views: Int = 0,
    @SerializedName("createdBy") val createdBy: String? = null,
    @SerializedName("created_at") val created_at: String? = null,
    @SerializedName("createdAt") val createdAt: String? = null,
    @SerializedName("is_published") val is_published: Boolean = true
)

data class PostResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: List<PostDto> = emptyList()
)

// Domain model for Post (UI)
data class Post(
    val id: String,
    val title: String,
    val description: String = "",
    val image: String = "",
    val category: String = "",
    val author: String = "",
    val created_at: String = "",
    val likes: Int = 0,
    val views: Int = 0,
    val is_featured: Boolean = false,
    val tags: List<String> = emptyList(),
    val isLiked: Boolean = false,
    val authorId: String = ""
)

// Mapper DTO -> domain
fun PostDto.toPost(): Post {
    val desc = excerpt
        ?: shortPrompt
        ?: fullPrompt
        ?: content
        ?: ""
    val img = featured_image ?: imageUrl ?: ""
    val created = createdAt ?: created_at ?: ""
    return Post(
        id = id,
        title = title,
        description = desc,
        image = img,
        category = category ?: "",
        author = createdBy ?: "",
        created_at = created,
        likes = likes,
        views = views,
        is_featured = !(!is_published), // keep logic simple - server decides featured/published
        tags = tags ?: emptyList(),
        isLiked = false,
        authorId = createdBy ?: ""
    )
}

// Simple Category model (used in your repo mocks)
data class Category(
    val id: String,
    val name: String,
    val description: String,
    val image: String,
    val post_count: Int = 0
)
