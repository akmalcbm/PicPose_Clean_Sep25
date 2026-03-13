/**
 * ---
 * File: ApiKeyInterceptor.kt
 * Layer: Data
 * Project: PicPose
 *
 * Purpose:
 * Configures networking behavior such as authentication, API keys, or Retrofit client creation.
 *
 * Interactions:
 * Consumed by repositories to talk to backend APIs and map raw payloads into app models.
 *
 * Data Flow:
 * Repository -> Retrofit service -> Backend response -> Model mapping -> ViewModel/UI
 *
 * Maintainer Notes:
 * - Prefer backend-neutral mapping in repositories instead of leaking transport details into the UI.
 * - TODO: Add stricter error classification and retry policy where network flows are user-critical.
 * ---
 */

package com.picpose.bestphotographyapp.core.network

import android.util.Log
import com.picpose.bestphotographyapp.BuildConfig
import javax.inject.Inject
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response

class ApiKeyInterceptor @Inject constructor() : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val originalUrl = request.url

        val apiKey = BuildConfig.API_KEY.trim()
        if (apiKey.isBlank()) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "BuildConfig.API_KEY is blank. V2 requests will continue without api_key.")
            }
            return chain.proceed(request)
        }

        if (originalUrl.queryParameter("api_key").isNullOrBlank()) {
            val updatedUrl: HttpUrl = originalUrl.newBuilder()
                .addQueryParameter("api_key", apiKey)
                .build()

            return chain.proceed(
                request.newBuilder()
                    .url(updatedUrl)
                    .build()
            )
        }

        return chain.proceed(request)
    }

    companion object {
        private const val TAG = "ApiKeyInterceptor"
    }
}
