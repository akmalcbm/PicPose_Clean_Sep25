package com.picpose.bestphotographyapp.core.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnalyticsLogger @Inject constructor(
    @ApplicationContext context: Context
) {
    private val analytics = FirebaseAnalytics.getInstance(context)

    fun logAppOpen() {
        logEvent("app_open")
    }

    fun logScreenView(screenName: String) {
        val normalized = sanitize(screenName, fallback = "unknown")
        val params = Bundle().apply {
            putString(FirebaseAnalytics.Param.SCREEN_NAME, normalized)
            putString(FirebaseAnalytics.Param.SCREEN_CLASS, "main_activity")
        }
        analytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, params)
    }

    fun logPromptView(promptId: String, category: String?) {
        logEvent(
            name = "prompt_view",
            params = Bundle().apply {
                putString("id", sanitize(promptId, "unknown"))
                putString("category", sanitize(category, "unknown"))
            }
        )
    }

    fun logGuideView(guideId: String, category: String?) {
        logEvent(
            name = "guide_view",
            params = Bundle().apply {
                putString("id", sanitize(guideId, "unknown"))
                putString("category", sanitize(category, "unknown"))
            }
        )
    }

    fun logPromptLike(promptId: String) {
        logEvent("prompt_like", Bundle().apply { putString("id", sanitize(promptId, "unknown")) })
    }

    fun logGuideLike(guideId: String) {
        logEvent("guide_like", Bundle().apply { putString("id", sanitize(guideId, "unknown")) })
    }

    fun logSharePrompt(promptId: String) {
        logEvent("share_prompt", Bundle().apply { putString("id", sanitize(promptId, "unknown")) })
    }

    fun logShareGuide(guideId: String) {
        logEvent("share_guide", Bundle().apply { putString("id", sanitize(guideId, "unknown")) })
    }

    fun logSearchPerformed(queryLength: Int) {
        logEvent(
            name = "search_performed",
            params = Bundle().apply {
                putLong("query_length", queryLength.coerceAtLeast(0).toLong())
            }
        )
    }

    fun logLoginSuccess(method: String) {
        logEvent("login_success", Bundle().apply { putString("method", sanitize(method, "unknown")) })
    }

    fun logSignupSuccess(method: String) {
        logEvent("signup_success", Bundle().apply { putString("method", sanitize(method, "unknown")) })
    }

    fun logNotificationOpen(deepLinkType: String) {
        logEvent(
            "notification_open",
            Bundle().apply { putString("deep_link_type", sanitize(deepLinkType, "unknown")) }
        )
    }

    private fun logEvent(name: String, params: Bundle = Bundle()) {
        analytics.logEvent(sanitize(name, fallback = "unknown_event"), params)
    }

    private fun sanitize(raw: String?, fallback: String): String {
        val candidate = raw
            ?.trim()
            ?.lowercase()
            ?.replace(Regex("[^a-z0-9_]+"), "_")
            ?.replace(Regex("_+"), "_")
            ?.trim('_')
            .orEmpty()

        return if (candidate.isBlank()) fallback else candidate.take(40)
    }
}
