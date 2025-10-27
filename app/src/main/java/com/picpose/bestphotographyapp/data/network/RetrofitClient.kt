package com.picpose.bestphotographyapp.data.network

import android.util.Log
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private const val TAG = "API"
    private const val BASE_URL = "https://picpose.iamakmal.in/"
    private const val CACHE_SIZE = 10L * 1024 * 1024 // 10 MB cache

    // Exposed global override for API key header (optional)
    var defaultApiKey: String? = "7a6f3c27a1b6d5e8e4c8a2b3f9e6d1f47c5b8a9d3e7f2c6a4b9e3d1c5f8a7b2c"
    
    // Cache directory (will be set from Application context)
    private var cacheDir: java.io.File? = null
    
    /**
     * Initialize cache directory from application context
     */
    fun initCache(context: android.content.Context) {
        cacheDir = java.io.File(context.cacheDir, "http_cache")
    }

    // Cache interceptor for offline support
    private val cacheInterceptor = Interceptor { chain ->
        var request = chain.request()
        
        // Add cache control for GET requests
        if (request.method == "GET") {
            val cacheControl = okhttp3.CacheControl.Builder()
                .maxAge(5, TimeUnit.MINUTES) // Cache for 5 minutes
                .build()
            
            request = request.newBuilder()
                .cacheControl(cacheControl)
                .build()
        }
        
        chain.proceed(request)
    }
    
    // Offline cache interceptor
    private val offlineCacheInterceptor = Interceptor { chain ->
        var request = chain.request()
        
        // Force cache when offline for GET requests
        if (request.method == "GET") {
            val cacheControl = okhttp3.CacheControl.Builder()
                .maxStale(7, TimeUnit.DAYS) // Use stale cache up to 7 days when offline
                .build()
            
            request = request.newBuilder()
                .cacheControl(cacheControl)
                .build()
        }
        
        chain.proceed(request)
    }

    // Monitoring interceptor (logs requests/responses)
    private val monitoringInterceptor = Interceptor { chain ->
        val request = chain.request()
        val start = System.currentTimeMillis()
        Log.d(TAG, "➡️ REQUEST  ${request.method} ${request.url}  | headers=${request.headers}")

        // optionally log request body (be careful with large/binary)
        try {
            request.body?.let { body ->
                val buffer = okio.Buffer()
                body.writeTo(buffer)
                val bodyText = buffer.readUtf8()
                Log.d(TAG, "➡️ REQUEST BODY: $bodyText")
            }
        } catch (_: Exception) { /* ignore */ }

        val response: Response = try {
            chain.proceed(request)
        } catch (e: Exception) {
            val dur = System.currentTimeMillis() - start
            Log.e(TAG, "❌ NETWORK ERROR ${request.method} ${request.url}  (${dur}ms): ${e.message}")
            throw e
        }

        val duration = System.currentTimeMillis() - start
        Log.d(TAG, "⬅️ RESPONSE ${response.code} ${response.request.url} (${duration}ms)")

        try {
            val respBody = response.peekBody(Long.MAX_VALUE).string()
            Log.d(TAG, "⬅️ RESPONSE BODY: $respBody")
        } catch (_: Exception) { /* ignore large bodies */ }

        response
    }

    // OkHttp logging
    private val httpLogging = HttpLoggingInterceptor { message -> Log.d(TAG, message) }
        .apply { level = HttpLoggingInterceptor.Level.BODY }

    // Build OkHttpClient with interceptors
    private val okHttpClient: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .callTimeout(60, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val origRequest = chain.request()
                val origUrl = origRequest.url

                // Append api_key as query param if defaultApiKey is set
                val newUrl = defaultApiKey?.let { key ->
                    origUrl.newBuilder()
                        .addQueryParameter("api_key", key)
                        .build()
                } ?: origUrl

                val newRequest = origRequest.newBuilder()
                    .url(newUrl)
                    .build()

                chain.proceed(newRequest)
            }
            .addInterceptor(cacheInterceptor)
            .addNetworkInterceptor(offlineCacheInterceptor)
            .addInterceptor(monitoringInterceptor)
            .addInterceptor(httpLogging)
        
        // Add cache if directory is set
        cacheDir?.let {
            builder.cache(okhttp3.Cache(it, CACHE_SIZE))
        }
        
        builder.build()
    }

    // Retrofit instance
    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    // Expose ApiService singleton (replace ApiService with your interface)
    val apiService: ApiService by lazy {
        retrofit.create(ApiService::class.java)
    }

    // Helper to create additional services if needed
    fun <T> createService(clazz: Class<T>): T = retrofit.create(clazz)
}
