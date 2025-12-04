package com.picpose.bestphotographyapp.auth

import android.app.Activity
import android.os.Bundle
import android.util.Log
import com.facebook.*
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import org.json.JSONObject

/**
 * Clean, modern Facebook login wrapper.
 * Fully compatible with AuthViewModel:
 *
 * facebookClient.startLogin(
 *     activity,
 *     onSuccess = { accessToken -> ... },
 *     onError   = { message -> ... }
 * )
 */
class FacebookAuthClient {

    private val callbackManager: CallbackManager = CallbackManager.Factory.create()
    fun getCallbackManager(): CallbackManager = callbackManager

    /**
     * Start Facebook login
     */
    fun startLogin(
        activity: Activity,
        onSuccess: (AccessToken?) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            // Permissions needed
            val permissions = listOf("email", "public_profile")

            val loginManager = LoginManager.getInstance()

            // Trigger login UI
            loginManager.logInWithReadPermissions(activity, permissions)

            // Register callback
            loginManager.registerCallback(callbackManager,
                object : FacebookCallback<LoginResult> {

                    override fun onSuccess(result: LoginResult) {
                        Log.d("FacebookAuthClient", "Login success, token received")
                        onSuccess(result.accessToken)
                    }

                    override fun onCancel() {
                        onError("Facebook login cancelled")
                    }

                    override fun onError(error: FacebookException) {
                        val msg = error.localizedMessage ?: "Facebook login error"
                        Log.e("FacebookAuthClient", msg)
                        onError(msg)
                    }
                }
            )

        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Facebook login error"
            Log.e("FacebookAuthClient", msg)
            onError(msg)
        }
    }

    /**
     * Fetch profile: id, name, email, picture
     */
    fun fetchProfile(
        accessToken: AccessToken,
        onResult: (FacebookProfile?) -> Unit
    ) {
        try {
            val request = GraphRequest.newMeRequest(accessToken) { json: JSONObject?, _ ->
                if (json == null) {
                    onResult(null)
                    return@newMeRequest
                }

                val id = json.optString("id")
                val name = json.optString("name")
                val email = json.optString("email")
                val picture = json
                    .optJSONObject("picture")
                    ?.optJSONObject("data")
                    ?.optString("url")

                onResult(
                    FacebookProfile(
                        id = id,
                        name = name,
                        email = email,
                        picture = picture
                    )
                )
            }

            val params = Bundle().apply {
                putString(
                    "fields",
                    "id,name,email,picture.width(200)"
                )
            }
            request.parameters = params
            request.executeAsync()

        } catch (e: Exception) {
            Log.e("FacebookAuthClient", "fetchProfile error: ${e.localizedMessage}")
            onResult(null)
        }
    }
}

/**
 * Returned Facebook profile fields
 */
data class FacebookProfile(
    val id: String?,
    val name: String?,
    val email: String?,
    val picture: String?
)
