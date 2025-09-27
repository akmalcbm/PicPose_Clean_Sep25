package com.picpose.bestphotographyapp.data.models

import com.google.gson.annotations.SerializedName

// Guide Post DTO returned by API
data class GuidePostDto(
    @SerializedName("id") val id: String = "",
    @SerializedName("title") val title: String = "",
    @SerializedName("content") val content: String? = null,
    @SerializedName("excerpt") val excerpt: String? = null,
    @SerializedName("featured_image") val featured_image: String? = null,
    @SerializedName("imageUrl") val imageUrl: String? = null,
    @SerializedName("category") val category: String? = null,
    @SerializedName("tags") val tags: List<String> = emptyList(),
    @SerializedName("difficulty_level") val difficulty_level: String? = null,
    @SerializedName("estimated_read_time") val estimated_read_time: Int = 0,
    @SerializedName("likes") val likes: Int = 0,
    @SerializedName("views") val views: Int = 0,
    @SerializedName("is_featured") val is_featured: Boolean = false,
    @SerializedName("author") val author: String? = null,
    @SerializedName("author_id") val author_id: String? = null,
    @SerializedName("created_at") val created_at: String? = null,
    @SerializedName("createdAt") val createdAt: String? = null,
    @SerializedName("updated_at") val updated_at: String? = null,
    @SerializedName("is_published") val is_published: Boolean = true,
    @SerializedName("status") val status: String? = "published"
)

// Domain model for GuidePost (UI)
data class GuidePost(
    val id: String,
    val title: String,
    val content: String = "",  
    val excerpt: String = "",
    val image: String = "",
    val category: String = "",
    val tags: List<String> = emptyList(),
    val difficultyLevel: String = "",
    val estimatedReadTime: Int = 0,
    val likes: Int = 0,
    val views: Int = 0,
    val isFeatured: Boolean = false,
    val author: String = "",
    val authorId: String = "",
    val createdAt: String = "",
    val updatedAt: String = "",
    val isLiked: Boolean = false
)

// Mapper DTO -> domain
fun GuidePostDto.toGuidePost(): GuidePost {
    val contentText = content ?: excerpt ?: ""
    val excerptText = excerpt ?: content?.take(150) ?: ""
    val imageUrl = featured_image ?: imageUrl ?: ""
    val createdTime = createdAt ?: created_at ?: ""
    
    return GuidePost(
        id = id,
        title = title,
        content = contentText,
        excerpt = excerptText,
        image = imageUrl,
        category = category ?: "",
        tags = tags,
        difficultyLevel = difficulty_level ?: "",
        estimatedReadTime = estimated_read_time,
        likes = likes,
        views = views,
        isFeatured = is_featured,
        author = author ?: "",
        authorId = author_id ?: "",
        createdAt = createdTime,
        updatedAt = updated_at ?: "",
        isLiked = false // Default value, can be updated based on user preferences
    )
}