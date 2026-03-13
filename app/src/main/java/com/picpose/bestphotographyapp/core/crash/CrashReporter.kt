/**
 * ---
 * File: CrashReporter.kt
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

package com.picpose.bestphotographyapp.core.crash

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.picpose.bestphotographyapp.BuildConfig
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CrashReporter @Inject constructor() {
    private val crashlytics = FirebaseCrashlytics.getInstance()

    init {
        crashlytics.setCustomKey("app_version", BuildConfig.VERSION_NAME)
        crashlytics.setCustomKey("build_type", BuildConfig.BUILD_TYPE)
    }

    fun configureCollection(enabled: Boolean) {
        crashlytics.setCrashlyticsCollectionEnabled(enabled)
    }

    fun setUserIdentifier(userId: String?) {
        if (userId.isNullOrBlank()) {
            crashlytics.setUserId("")
            return
        }
        crashlytics.setUserId(hashValue(userId))
    }

    fun setAccountType(accountType: String?) {
        crashlytics.setCustomKey("account_type", accountType?.trim()?.ifBlank { "unknown" } ?: "unknown")
    }

    fun setLastScreen(screenName: String) {
        crashlytics.setCustomKey("last_screen", screenName.trim().ifBlank { "unknown" }.take(40))
    }

    fun recordUnexpectedNetworkFailure(operation: String, throwable: Throwable, httpCode: Int? = null) {
        crashlytics.setCustomKey("error_type", "network")
        crashlytics.setCustomKey("operation", operation.take(40))
        if (httpCode != null) crashlytics.setCustomKey("http_code", httpCode)
        crashlytics.recordException(throwable)
    }

    fun recordParsingFailure(scope: String, throwable: Throwable) {
        crashlytics.setCustomKey("error_type", "parsing")
        crashlytics.setCustomKey("parse_scope", scope.take(40))
        crashlytics.recordException(throwable)
    }

    fun recordImageUploadFailure(scope: String, throwable: Throwable) {
        crashlytics.setCustomKey("error_type", "image_upload")
        crashlytics.setCustomKey("upload_scope", scope.take(40))
        crashlytics.recordException(throwable)
    }

    private fun hashValue(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return bytes.joinToString("") { b -> "%02x".format(b) }
    }
}
