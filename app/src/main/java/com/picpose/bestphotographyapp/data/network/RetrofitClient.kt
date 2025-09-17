// RetrofitClient.kt (improved)
package com.picpose.bestphotographyapp.data.network

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    // Use your actual base (ensure trailing slash). If your PHP sits at / picpose_admin/api/ use that.
    private const val BASE_URL = "https://picpose.iamakmal.in/api/"

    // Optional: central API key used in many calls (or provide per-call)
    var defaultApiKey: String? = null

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val apiKeyInterceptor = Interceptor { chain ->
        val req = chain.request()
        val originalUrl = req.url

        // Example: attach api_key as query param if defaultApiKey set
        val newUrl = if (!defaultApiKey.isNullOrBlank()) {
            originalUrl.newBuilder()
                .addQueryParameter("api_key", defaultApiKey)
                .build()
        } else originalUrl

        val newReq = req.newBuilder()
            .url(newUrl)
            // If you prefer header style:
            // .addHeader("API-Key", defaultApiKey ?: "")
            .build()
        chain.proceed(newReq)
    }

    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        // Add logging only in debug; you can gate by BuildConfig.DEBUG
        .addInterceptor(logging)
        // Add apiKeyInterceptor if you use defaultApiKey
        .addInterceptor(apiKeyInterceptor)
        .build()

    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttp)
            .addConverterFactory(
                GsonConverterFactory.create()
            )
            .build()
            .create(ApiService::class.java)
    }
}
