package com.picpose.bestphotographyapp.data.network

import android.util.Log
import com.picpose.bestphotographyapp.BuildConfig
import com.picpose.bestphotographyapp.data.models.AccountType
import com.picpose.bestphotographyapp.data.models.UserRole
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.CacheControl
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.lang.reflect.Type
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private const val TAG = "API"
    private val BASE_URL = BuildConfig.API_BASE_URL
    private const val CACHE_SIZE = 10L * 1024 * 1024 // 10MB

    // Global API key used everywhere unless overridden
    var defaultApiKey: String? =
        BuildConfig.API_KEY.takeIf { it.isNotBlank() }

    private var cacheDir: java.io.File? = null

    private val accountTypeDeserializer =
        JsonDeserializer<AccountType> { json: JsonElement?, _: Type, _: JsonDeserializationContext ->
            AccountType.from(json?.asString)
        }

    private val userRoleDeserializer =
        JsonDeserializer<UserRole> { json: JsonElement?, _: Type, _: JsonDeserializationContext ->
            UserRole.from(json?.asString)
        }

    private val gson: Gson by lazy {
        GsonBuilder()
            .registerTypeAdapter(AccountType::class.java, accountTypeDeserializer)
            .registerTypeAdapter(UserRole::class.java, userRoleDeserializer)
            .create()
    }

    fun initCache(context: android.content.Context) {
        cacheDir = java.io.File(context.cacheDir, "http_cache")
    }

    // -----------------------------
    // 1. Automatic API Key Interceptor
    // -----------------------------
    private val apiKeyInterceptor = Interceptor { chain ->
        val req = chain.request().newBuilder()

        defaultApiKey?.let { key ->
            req.addHeader("X-API-Key", key)
        }

        chain.proceed(req.build())
    }


    // -----------------------------
    // 2. Cache Interceptor for Online Mode
    // -----------------------------
    private val cacheInterceptor = Interceptor { chain ->
        val request = chain.request()

        val newRequest = request.newBuilder()
            .header("Cache-Control", "public, max-age=300") // 5 minute cache
            .build()

        chain.proceed(newRequest)
    }


    // -----------------------------
    // 3. Offline Cache Interceptor
    // -----------------------------
    private val offlineCacheInterceptor = Interceptor { chain ->
        var request = chain.request()

        val cacheControl = CacheControl.Builder()
            .maxStale(7, TimeUnit.DAYS)
            .build()

        request = request.newBuilder()
            .cacheControl(cacheControl)
            .build()

        chain.proceed(request)
    }


    // -----------------------------
    // 4. API Monitoring Interceptor (safe logging)
    // -----------------------------
    private val monitoringInterceptor = Interceptor { chain ->
        val request = chain.request()
        val start = System.currentTimeMillis()

        Log.d(TAG, "➡️ ${request.method} ${request.url}")

        val response: Response = try {
            chain.proceed(request)
        } catch (e: Exception) {
            Log.e(TAG, "❌ ERROR on ${request.url}: ${e.message}")
            throw e
        }

        val duration = System.currentTimeMillis() - start
        Log.d(TAG, "⬅️ ${response.code} (${duration}ms) ${response.request.url}")

        if (BuildConfig.DEBUG) {
            try {
                val copy = response.peekBody(8 * 1024).string()
                Log.d(TAG, "⬅️ BODY: $copy")
            } catch (_: Exception) { }
        }

        response
    }


    // -----------------------------
    // 5. Http Logging (debug only)
    // -----------------------------
    private val httpLogging = HttpLoggingInterceptor { msg ->
        Log.d(TAG, msg)
    }.apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BASIC
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }


    // -----------------------------
    // 6. OkHttp Setup
    // -----------------------------
    private val okHttpClient: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .callTimeout(60, TimeUnit.SECONDS)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)

            // Order of interceptors is important
            .addInterceptor(apiKeyInterceptor)
            .addInterceptor(cacheInterceptor)
            .addNetworkInterceptor(offlineCacheInterceptor)
            .addInterceptor(monitoringInterceptor)
            .addInterceptor(httpLogging)

        cacheDir?.let {
            builder.cache(okhttp3.Cache(it, CACHE_SIZE))
        }

        builder.build()
    }


    // -----------------------------
    // 7. Retrofit Instance
    // -----------------------------
    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }


    // ❌ WRONG:
    // val apiService: UserApiService

    // ✅ RIGHT:
    val apiService: ApiService by lazy {
        retrofit.create(ApiService::class.java)
    }

    // Additional services
    fun <T> createService(clazz: Class<T>): T = retrofit.create(clazz)
}
