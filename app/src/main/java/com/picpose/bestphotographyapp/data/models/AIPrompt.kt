package com.picpose.bestphotographyapp.data.models

data class AIPrompt(
    val id: String,
    val title: String,
    val shortPrompt: String? = null,
    val fullPrompt: String? = null,
    val imageUrl: String? = null,
    val category: String? = null,
    val tags: List<String>? = emptyList(),
    val likes: Int = 0,
    val isPopular: Boolean = false,
    val isFavorite: Boolean = false,   // <-- required
    val status: String? = "published",
    val priority: Int = 0,
    val createdAt: String? = null,
    val updatedAt: String? = null
)
