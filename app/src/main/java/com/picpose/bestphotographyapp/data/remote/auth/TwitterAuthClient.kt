package com.picpose.bestphotographyapp.data.remote.auth

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

data class TwitterTokenResponse(
    val access_token: String?,
    val token_type: String?,
    val expires_in: Int?,
    val refresh_token: String?
)

/**
 * Minimal Twitter (X) OAuth2 + PKCE flow helper.
 *
 * NOTE: For security, prefer performing token exchange on your backend. This client performs a direct
 * token exchange (useable for testing). Replace TODO_* constants.
 */
class TwitterAuthClient {

    private val clientId = "TODO_TWITTER_CLIENT_ID" // set your client id
    private val redirectUri = "com.picpose://oauth/twitter_callback"
    private val scope = "tweet.read users.read offline.access"
    private val authorizeUrl = "https://twitter.com/i/oauth2/authorize"
    private val tokenUrl = "https://api.twitter.com/2/oauth2/token"
    private val http = OkHttpClient()

    fun buildAuthorizationUrl(codeChallenge: String, state: String): String {
        val uri = Uri.parse(authorizeUrl).buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("scope", scope)
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge", codeChallenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .build()
        return uri.toString()
    }

    fun launchAuth(context: Context, url: String) {
        val tabsIntent = CustomTabsIntent.Builder().build()
        tabsIntent.launchUrl(context, Uri.parse(url))
    }

    suspend fun exchangeCodeForToken(code: String, codeVerifier: String): Result<TwitterTokenResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val form = FormBody.Builder()
                    .add("client_id", clientId)
                    .add("grant_type", "authorization_code")
                    .add("code", code)
                    .add("redirect_uri", redirectUri)
                    .add("code_verifier", codeVerifier)
                    .build()

                val request = Request.Builder()
                    .url(tokenUrl)
                    .post(form)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .build()

                val resp = http.newCall(request).execute()
                val body = resp.body?.string()
                if (!resp.isSuccessful) return@withContext Result.failure(Exception("Token exchange failed: ${resp.code} ${body}"))

                val moshi = Moshi.Builder().build()
                val adapter = moshi.adapter(TwitterTokenResponse::class.java)
                val parsed = adapter.fromJson(body ?: "")
                if (parsed != null) Result.success(parsed)
                else Result.failure(Exception("Empty token response"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
