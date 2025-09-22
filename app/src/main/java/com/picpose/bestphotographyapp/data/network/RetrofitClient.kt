// RetrofitClient.kt (improved)
package com.picpose.bestphotographyapp.data.network

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    // Base URL - make sure trailing slash present.
    private const val BASE_URL = "https://picpose.iamakmal.in/api/"

    // Central API key (optional). If set, it will be appended as query param
    // unless the request already contains an "api_key" query param.
    var defaultApiKey: String? = null

    // Logging interceptor - enable only during debug builds
    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    // Interceptor that appends api_key as query param WHEN the request does not already include api_key
    private val apiKeyInterceptor = Interceptor { chain ->
        val req = chain.request()
        val originalUrl = req.url

        // If request already includes api_key, don't append default
        val alreadyHasApiKey = originalUrl.queryParameterNames.any { it.equals("api_key", ignoreCase = true) }

        val newUrl = if (!alreadyHasApiKey && !defaultApiKey.isNullOrBlank()) {
            originalUrl.newBuilder()
                .addQueryParameter("api_key", defaultApiKey)
                .build()
        } else originalUrl

        val newReq = req.newBuilder()
            .url(newUrl)
            .build()
        chain.proceed(newReq)
    }

    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        // Use logging only in debug. Uncomment the next line or gate it with BuildConfig.DEBUG
        // .addInterceptor(logging)
        .addInterceptor(apiKeyInterceptor)
        .build()

    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttp)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
