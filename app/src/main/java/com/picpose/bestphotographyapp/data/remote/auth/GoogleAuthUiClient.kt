/**
 * ---
 * File: GoogleAuthUiClient.kt
 * Layer: Data
 * Project: PicPose
 *
 * Purpose:
 * Supports the PicPose app with feature-specific models, helpers, or UI building blocks.
 *
 * Interactions:
 * Consumed by repositories to talk to backend APIs and map raw payloads into app models.
 *
 * Data Flow:
 * Repository -> Retrofit service -> Backend response -> Model mapping -> ViewModel/UI
 *
 * Maintainer Notes:
 * - Keep responsibilities narrow and update this header when the file grows into a larger abstraction.
 * ---
 */

package com.picpose.bestphotographyapp.data.remote.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/**
 * Modern Google Sign-In using AndroidX Credentials + Google ID token parsing.
 * Ensure GOOGLE_WEB_CLIENT_ID is set to your Web OAuth client id (the one for server verification).
 */
class GoogleAuthUiClient(context: Context) {

    private val credentialManager: CredentialManager = CredentialManager.create(context)

    private fun buildGoogleIdOption(): GetGoogleIdOption {
        return GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .setServerClientId(GOOGLE_WEB_CLIENT_ID)
            .build()
    }

    suspend fun signIn(activity: Activity): GetCredentialResponse? {
        return try {
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(buildGoogleIdOption())
                .build()

            credentialManager.getCredential(context = activity, request = request)
        } catch (e: NoCredentialException) {
            Log.w("GoogleAuthUiClient", "signIn no credentials available: ${e.localizedMessage}")
            throw e
        } catch (e: GetCredentialCancellationException) {
            Log.i("GoogleAuthUiClient", "signIn cancelled by user")
            throw e
        } catch (e: Exception) {
            val apiException = e as? ApiException ?: e.cause as? ApiException
            val statusText = apiException?.let {
                " apiStatusCode=${it.statusCode} apiStatusName=${CommonStatusCodes.getStatusCodeString(it.statusCode)}"
            }.orEmpty()
            Log.e(
                "GoogleAuthUiClient",
                "signIn error class=${e.javaClass.simpleName} message=${e.localizedMessage}$statusText",
                e
            )
            throw e
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
            Log.e(
                "GoogleAuthUiClient",
                "parseGoogleCredential error class=${e.javaClass.simpleName} message=${e.localizedMessage}",
                e
            )
            null
        }
    }

    companion object {
        // Done replace with your Google Web Client ID (OAuth 2.0 Client ID for Web)
        const val GOOGLE_WEB_CLIENT_ID = "424655024692-i2dupr2t96snjnlo82f5o1dcvu63stbm.apps.googleusercontent.com"
    }
}
