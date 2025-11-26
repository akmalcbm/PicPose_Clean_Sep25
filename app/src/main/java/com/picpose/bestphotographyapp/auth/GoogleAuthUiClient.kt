package com.picpose.bestphotographyapp.auth

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

class GoogleAuthUiClient(
    private val context: Context
) {

    private val credentialManager = CredentialManager.create(context)

    /**
     * Step 1: Build Google Sign-In Request using GetGoogleIdOption
     */
    private fun buildGoogleIdOption(): GetGoogleIdOption {
        return GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(true)
            .setServerClientId(GOOGLE_WEB_CLIENT_ID)
            .build()
    }

    /**
     * Step 2: Launch Google Sign-In (no deprecated API)
     */
    suspend fun signIn(): GetCredentialResponse? {
        return try {
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(buildGoogleIdOption())
                .build()

            credentialManager.getCredential(
                context = context,
                request = request
            )

        } catch (e: Exception) {
            Log.e("GoogleAuthUiClient", "Google sign-in failed: ${e.localizedMessage}")
            null
        }
    }

    /**
     * Step 3: Extract user info from Google ID Token
     */
    fun parseGoogleCredential(response: GetCredentialResponse?): GoogleUserData? {
        return try {
            val credential = response?.credential
                ?: return null

            val googleCred = GoogleIdTokenCredential.createFrom(credential.data)

            GoogleUserData(
                displayName = googleCred.displayName,
                email = googleCred.id,
                profilePictureUrl = googleCred.profilePictureUri?.toString(),
                idToken = googleCred.idToken
            )

        } catch (e: Exception) {
            Log.e("GoogleAuthUiClient", "Credential parse error: ${e.localizedMessage}")
            null
        }
    }

    companion object {
        // Replace with your real Web Client ID
        const val GOOGLE_WEB_CLIENT_ID =
            "YOUR_WEB_CLIENT_ID.apps.googleusercontent.com"
    }
}

data class GoogleUserData(
    val displayName: String?,
    val email: String?,
    val profilePictureUrl: String?,
    val idToken: String?
)
