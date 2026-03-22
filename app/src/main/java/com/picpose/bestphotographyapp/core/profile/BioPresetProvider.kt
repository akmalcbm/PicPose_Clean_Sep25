package com.picpose.bestphotographyapp.core.profile

import android.content.Context
import com.picpose.bestphotographyapp.R

/**
 * Shared source for profile bio presets.
 *
 * Keep this list sourced from resources so both signup and profile editing use
 * the same preset pool.
 */
object BioPresetProvider {

    fun suggestions(context: Context): List<String> {
        return context.resources
            .getStringArray(R.array.ai_bio_suggestions)
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    fun randomSuggestion(context: Context): String? {
        return suggestions(context).randomOrNull()
    }

    fun resolveOrRandom(context: Context, bio: String?): String? {
        val normalized = bio?.trim().orEmpty()
        if (normalized.isNotEmpty()) return normalized
        return randomSuggestion(context)
    }
}
