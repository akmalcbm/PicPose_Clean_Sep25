package com.picpose.bestphotographyapp.data.models

import kotlinx.serialization.Serializable

@Serializable
data class AIPromptResponse(
    val success: Boolean,
    val data: List<AIPromptDto>,
    val message: String? = null
)

@Serializable
data class AIPromptDto(
    val id: String,
    val title: String,
    val shortPrompt: String,
    val fullPrompt: String,
    val imageUrl: String,
    val category: String,
    val tags: List<String> = emptyList(),
    val likes: Int = 0,
    val isPopular: Boolean = false,
    val status: String = "published",
    val priority: Int = 0,
    val createdAt: String? = null,
    val updatedAt: String? = null
) {
    fun toAIPrompt() = AIPrompt(
        id = id,
        title = title,
        shortPrompt = shortPrompt,
        fullPrompt = fullPrompt,
        imageUrl = imageUrl,
        category = category,
        tags = tags,
        likes = likes,
        isPopular = isPopular
    )
}

data class PostResponse(
    val success: Boolean,
    val message: String,
    val data: List<Post>
)

data class Post(
    val id: String,
    val title: String,
    val description: String,
    val image: String, // This will be the image URL from your server
    val category: String,
    val author: String,
    val created_at: String,
    val likes: Int = 0,
    val views: Int = 0,
    val is_featured: Boolean = false,
    val tags: List<String> = emptyList(), // Add tags field
    val isLiked: Boolean = false, // Add isLiked field for UI state
    val authorId: String = "" // Add authorId field
)

data class CategoryResponse(
    val success: Boolean,
    val message: String,
    val data: List<Category>
)

data class Category(
    val id: String,
    val name: String,
    val description: String,
    val image: String,
    val post_count: Int = 0
)

data class UserStats(
    val totalPosts: Int,
    val totalLikes: Int,
    val totalViews: Int,
    val level: String
)

data class AIPrompt(
    val id: String,
    val title: String,
    val shortPrompt: String,
    val fullPrompt: String,
    val imageUrl: String,
    val category: String,
    val tags: List<String> = emptyList(),
    val likes: Int = 0,
    val isPopular: Boolean = false,
    val isFavorite: Boolean = false
)
