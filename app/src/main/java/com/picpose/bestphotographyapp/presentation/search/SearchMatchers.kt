/**
 * ---
 * File: SearchMatchers.kt
 * Layer: Shared
 * Project: PicPose
 *
 * Purpose:
 * Supports the PicPose app with feature-specific models, helpers, or UI building blocks.
 *
 * Interactions:
 * Works with nearby classes in the same layer to keep responsibilities separated and easier to maintain.
 *
 * Data Flow:
 * Feature-specific flow; see adjacent ViewModels and repositories for the full path.
 *
 * Maintainer Notes:
 * - Keep responsibilities narrow and update this header when the file grows into a larger abstraction.
 * ---
 */

package com.picpose.bestphotographyapp.presentation.search

import com.picpose.bestphotographyapp.data.remote.dto.AIPrompt
import com.picpose.bestphotographyapp.data.remote.dto.Category
import com.picpose.bestphotographyapp.data.remote.dto.GuidePost
import com.picpose.bestphotographyapp.data.remote.dto.Post
import com.picpose.bestphotographyapp.data.remote.dto.v2.V2PromptDto

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

    fun matchesV2Prompt(prompt: V2PromptDto, query: String): Boolean {
        if (query.isBlank()) return true
        return prompt.title.contains(query, ignoreCase = true) ||
            (prompt.shortPrompt?.contains(query, ignoreCase = true) == true) ||
            (prompt.fullPrompt?.contains(query, ignoreCase = true) == true) ||
            (prompt.teaserText?.contains(query, ignoreCase = true) == true) ||
            (prompt.category?.contains(query, ignoreCase = true) == true) ||
            prompt.tags.any { it.contains(query, ignoreCase = true) } ||
            prompt.tier.contains(query, ignoreCase = true)
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
