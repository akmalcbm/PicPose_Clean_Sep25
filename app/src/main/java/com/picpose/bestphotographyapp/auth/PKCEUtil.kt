package com.picpose.bestphotographyapp.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.*

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
