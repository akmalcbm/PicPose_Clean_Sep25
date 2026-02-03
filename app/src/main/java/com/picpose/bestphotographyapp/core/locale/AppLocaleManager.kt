package com.picpose.bestphotographyapp.core.locale

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object AppLocaleManager {
    fun applyLanguage(languageTag: String) {
        val normalized = when (languageTag) {
            "hi" -> "hi"
            else -> "en"
        }
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(normalized)
        )
    }
}
