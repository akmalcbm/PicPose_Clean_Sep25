package com.picpose.bestphotographyapp.data.remote.auth

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/**
 * Modern Google Sign-In using AndroidX Credentials + Google ID token parsing.
 * Ensure GOOGLE_WEB_CLIENT_ID is set to your Web OAuth client id (the one for server verification).
 */
class GoogleAuthUiClient(private val context: Context) {

    private val credentialManager: CredentialManager = CredentialManager.create(context)

    private fun buildGoogleIdOption(): GetGoogleIdOption {
        return GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .setServerClientId(GOOGLE_WEB_CLIENT_ID)
            .build()
    }

    suspend fun signIn(): GetCredentialResponse? {
        return try {
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(buildGoogleIdOption())
                .build()

            credentialManager.getCredential(context = context, request = request)
        } catch (e: Exception) {
            Log.e("GoogleAuthUiClient", "signIn error: ${e.localizedMessage}")
            null
        }
    }

    fun parseGoogleCredential(response: GetCredentialResponse?): GoogleUserData? {
        return try {
            val credential = response?.credential ?: return null
            val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
            GoogleUserData(
                displayName = googleCred.displayName,
                email = googleCred.id,
                profilePictureUrl = googleCred.profilePictureUri?.toString(),
                idToken = googleCred.idToken
            )
        } catch (e: Exception) {
            Log.e("GoogleAuthUiClient", "parseGoogleCredential error: ${e.localizedMessage}")
            null
        }
    }

    companion object {
        // Done replace with your Google Web Client ID (OAuth 2.0 Client ID for Web)
        const val GOOGLE_WEB_CLIENT_ID = "424655024692-i2dupr2t96snjnlo82f5o1dcvu63stbm.apps.googleusercontent.com"
    }
}
