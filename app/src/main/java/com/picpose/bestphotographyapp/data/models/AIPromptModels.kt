// AIPromptModels.kt
package com.picpose.bestphotographyapp.data.models

import com.google.gson.annotations.SerializedName

// Network DTO for AI Prompt (Gson)
data class AIPromptDto(
    @SerializedName("id") val id: String = "",
    @SerializedName("title") val title: String = "",
    @SerializedName("shortPrompt") val shortPrompt: String? = "",
    @SerializedName("fullPrompt") val fullPrompt: String? = "",
    @SerializedName("imageUrl") val imageUrl: String? = "",
    @SerializedName("category") val category: String? = "",
    @SerializedName("tags") val tags: List<String> = emptyList(),
    @SerializedName("likes") val likes: Int = 0,
    @SerializedName("isPopular") val isPopular: Boolean = false,
    @SerializedName("status") val status: String? = "published",
    @SerializedName("priority") val priority: Int = 0,
    @SerializedName("createdAt") val createdAt: String? = null,
    @SerializedName("updatedAt") val updatedAt: String? = null
)

data class MetaDto(
    @SerializedName("total") val total: Int = 0,
    @SerializedName("page") val page: Int = 1,
    @SerializedName("limit") val limit: Int = 20,
    @SerializedName("hasMore") val hasMore: Boolean = false
)

data class AIPromptResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("data") val data: List<AIPromptDto> = emptyList(),
    @SerializedName("meta") val meta: MetaDto? = null,
    @SerializedName("message") val message: String? = null
)

// Domain model used by app
data class AIPrompt(
    val id: String,
    val title: String,
    val shortPrompt: String = "",
    val fullPrompt: String = "",
    val imageUrl: String = "",
    val category: String = "",
    val tags: List<String> = emptyList(),
    val likes: Int = 0,
    val isPopular: Boolean = false,
    val isFavorite: Boolean = false,
    val status: String = "published",
    val priority: Int = 0,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

// mapper extension - adapt field names if your AIPromptDto uses other names
fun AIPromptDto.toAIPrompt(isFavorite: Boolean = false): AIPrompt {
    return AIPrompt(
        id = this.id ?: "",
        title = this.title ?: "",
        shortPrompt = this.shortPrompt ?: "",
        fullPrompt = this.fullPrompt ?: "",
        imageUrl = this.imageUrl ?: "",
        category = this.category ?: "",
        tags = this.tags ?: emptyList(),
        likes = this.likes ?: 0,
        isPopular = this.isPopular ?: false,
        status = this.status ?: "published",
        priority = this.priority ?: 0,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        isFavorite = isFavorite
    )
}
