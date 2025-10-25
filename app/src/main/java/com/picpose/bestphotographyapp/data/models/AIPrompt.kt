package com.picpose.bestphotographyapp.data.models

data class AIPrompt(
    val id: String,
    val title: String,
    val shortPrompt: String? = null,
    val fullPrompt: String? = null,
    val imageUrl: String? = null,
    val category: String? = null,
    val tags: List<String>? = emptyList(),
    var likes: Int = 0,       // ✅ Changed to var for dynamic updates
    var views: Int = 0,       // ✅ Changed to var for dynamic updates
    val isPopular: Boolean = false,
    var isFavorite: Boolean = false,   // ✅ keep mutable for toggling in UI
    val status: String? = "published",
    val priority: Int = 0,
    val createdAt: String? = null,
    val updatedAt: String? = null
)
