/**
 * ---
 * File: PKCEUtil.kt
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

package com.picpose.bestphotographyapp.core.utils

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

object PKCEUtil {
    fun generateCodeVerifier(): String {
        val sr = SecureRandom()
        val code = ByteArray(64)
        sr.nextBytes(code)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(code)
    }

    fun codeChallengeFromVerifier(verifier: String): String {
        val bytes = verifier.toByteArray(Charsets.US_ASCII)
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }
}