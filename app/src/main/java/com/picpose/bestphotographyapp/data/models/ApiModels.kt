package com.picpose.bestphotographyapp.data.models

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
    val is_featured: Boolean = false
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
    val shortPrompt: String, // 1-2 lines preview
    val fullPrompt: String,  // Complete detailed prompt
    val imageUrl: String,
    val category: String,
    val tags: List<String> = emptyList(),
    val likes: Int = 0,
    val isPopular: Boolean = false
)

data class AIPromptResponse(
    val success: Boolean,
    val message: String,
    val data: List<AIPrompt>
)
