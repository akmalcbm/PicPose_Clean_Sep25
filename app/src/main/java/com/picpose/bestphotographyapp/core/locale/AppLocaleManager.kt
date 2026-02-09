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
