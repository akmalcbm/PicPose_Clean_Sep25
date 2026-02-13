package com.picpose.bestphotographyapp.presentation.search

import com.picpose.bestphotographyapp.data.models.AIPrompt
import com.picpose.bestphotographyapp.data.models.Category
import com.picpose.bestphotographyapp.data.models.GuidePost
import com.picpose.bestphotographyapp.data.models.Post

object SearchMatchers {
    fun normalizeQuery(query: String): String = query.trim()

    fun matchesAIPrompt(prompt: AIPrompt, query: String): Boolean {
        if (query.isBlank()) return true
        return prompt.title.contains(query, ignoreCase = true) ||
            (prompt.fullPrompt?.contains(query, ignoreCase = true) == true) ||
            (prompt.shortPrompt?.contains(query, ignoreCase = true) == true) ||
            (prompt.category?.contains(query, ignoreCase = true) == true) ||
            prompt.tags.any { it.contains(query, ignoreCase = true) }
    }

    fun matchesGuidePost(post: GuidePost, query: String): Boolean {
        if (query.isBlank()) return true
        return post.title.contains(query, ignoreCase = true) ||
            post.content.contains(query, ignoreCase = true) ||
            post.category.contains(query, ignoreCase = true) ||
            post.tags.any { it.contains(query, ignoreCase = true) }
    }

    fun matchesRecentPost(post: Post, query: String): Boolean {
        if (query.isBlank()) return true
        return post.title.contains(query, ignoreCase = true) ||
            post.description.contains(query, ignoreCase = true) ||
            post.category.contains(query, ignoreCase = true) ||
            post.tags.any { it.contains(query, ignoreCase = true) }
    }

    fun matchesCategory(category: Category, query: String): Boolean {
        if (query.isBlank()) return true
        return category.name.contains(query, ignoreCase = true) ||
            category.description.contains(query, ignoreCase = true)
    }
}
