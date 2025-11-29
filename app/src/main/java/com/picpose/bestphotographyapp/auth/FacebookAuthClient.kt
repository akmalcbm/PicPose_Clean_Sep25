package com.picpose.bestphotographyapp.auth

import android.app.Activity
import android.os.Bundle
import android.util.Log
import com.facebook.AccessToken
import com.facebook.CallbackManager
import com.facebook.GraphRequest
import com.facebook.login.LoginManager
import org.json.JSONObject

/**
 * Small wrapper around Facebook LoginManager + Graph API.
 * - startLogin(activity, onTokenReceived, onError) will show FB UI and return AccessToken via callback.
 * - fetchProfile(accessToken, callback) fetches id,name,email,picture via Graph API.
 *
 * Make sure to add Facebook SDK initialization in Application if required.
 */
class FacebookAuthClient {

    private val callbackManager: CallbackManager = CallbackManager.Factory.create()

    fun getCallbackManager(): CallbackManager = callbackManager

    fun startLogin(
        activity: Activity,
        onTokenReceived: (AccessToken?) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val loginManager = LoginManager.getInstance()
            loginManager.logIn(activity, listOf("email", "public_profile"))

            loginManager.registerCallback(callbackManager, object : com.facebook.FacebookCallback<com.facebook.login.LoginResult> {
                override fun onCancel() {
                    onError("Facebook login cancelled")
                }

                override fun onError(error: com.facebook.FacebookException) {
                    onError(error.localizedMessage ?: "Facebook login error")
                }

                override fun onSuccess(result: com.facebook.login.LoginResult) {
                    onTokenReceived(result.accessToken)
                }
            })
        } catch (e: Exception) {
            onError(e.localizedMessage ?: "Facebook login error")
        }
    }

    fun fetchProfile(accessToken: AccessToken, onResult: (FacebookProfile?) -> Unit) {
        try {
            val request = GraphRequest.newMeRequest(accessToken) { jsonObject: JSONObject?, _ ->
                if (jsonObject == null) {
                    onResult(null); return@newMeRequest
                }
                val name = jsonObject.optString("name")
                val email = jsonObject.optString("email")
                val id = jsonObject.optString("id")
                val picture = jsonObject.optJSONObject("picture")
                    ?.optJSONObject("data")
                    ?.optString("url")
                onResult(FacebookProfile(id = id, name = name, email = email, picture = picture))
            }
            val params = Bundle().apply {
                putString("fields", "id,name,email,picture.width(200)")
            }
            request.parameters = params
            request.executeAsync()
        } catch (e: Exception) {
            Log.e("FacebookAuthClient", "fetchProfile error: ${e.localizedMessage}")
            onResult(null)
        }
    }
}

data class FacebookProfile(
    val id: String?,
    val name: String?,
    val email: String?,
    val picture: String?
)
