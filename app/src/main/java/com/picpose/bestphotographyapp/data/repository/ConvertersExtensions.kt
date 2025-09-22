package com.picpose.bestphotographyapp.data.repository

import com.picpose.bestphotographyapp.data.database.FavoritePrompt
import com.picpose.bestphotographyapp.data.models.AIPrompt

// Convert AIPrompt -> FavoritePrompt (for insertion into Room)
fun AIPrompt.toFavoritePrompt(): FavoritePrompt {
    return FavoritePrompt(
        // id field in FavoritePrompt is local PK (auto-generated) so we omit it here by using default
        promptId = this.id,
        title = this.title,
        shortPrompt = this.shortPrompt,
        fullPrompt = this.fullPrompt,
        imageUrl = this.imageUrl,
        category = this.category,
        likes = this.likes,
        isPopular = this.isPopular,
        tags = this.tags,
        status = this.status,
        priority = this.priority,
        dateAdded = System.currentTimeMillis(),
        favoritedAt = System.currentTimeMillis()
    )
}

// Convert FavoritePrompt -> AIPrompt (when loading favorites from Room)
fun FavoritePrompt.toAIPrompt(): AIPrompt {
    return AIPrompt(
        id = this.promptId,
        title = this.title ?: "",
        shortPrompt = this.shortPrompt,
        fullPrompt = this.fullPrompt,
        imageUrl = this.imageUrl,
        category = this.category,
        tags = this.tags ?: emptyList(),
        likes = this.likes ?: 0,
        isPopular = this.isPopular ?: false,
        isFavorite = true,
        status = this.status ?: "published",
        priority = this.priority ?: 0,
        createdAt = this.dateAdded?.let { java.time.Instant.ofEpochMilli(it).toString() },
        updatedAt = this.favoritedAt?.let { java.time.Instant.ofEpochMilli(it).toString() }
    )
}
