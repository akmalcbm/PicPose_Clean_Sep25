/**
 * ---
 * File: AppLocaleManager.kt
 * Layer: Core
 * Project: PicPose
 *
 * Purpose:
 * Provides app-wide helpers, constants, analytics, locale, formatting, or cross-cutting abstractions.
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

package com.picpose.bestphotographyapp.core.locale

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

object AppLocaleManager {
    fun applyLanguage(languageTag: String) {
        val normalized = resolveLanguageTags(languageTag)
        if (normalized.isEmpty()) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
            return
        }
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(normalized)
        )
    }

    fun resolveLanguageTags(languageTag: String): String {
        if (languageTag == "system") return ""
        val localeTag = Locale.forLanguageTag(languageTag).toLanguageTag()
        return if (localeTag.isBlank() || localeTag == "und") "en" else localeTag
    }
}
