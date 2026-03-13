/**
 * ---
 * File: V2NetworkModule.kt
 * Layer: Dependency Injection
 * Project: PicPose
 *
 * Purpose:
 * Provides Hilt bindings so feature code can receive repositories, database instances, and services by injection.
 *
 * Interactions:
 * Supports constructor injection so app components receive stable shared dependencies.
 *
 * Data Flow:
 * Feature-specific flow; see adjacent ViewModels and repositories for the full path.
 *
 * Maintainer Notes:
 * - Keep responsibilities narrow and update this header when the file grows into a larger abstraction.
 * ---
 */

package com.picpose.bestphotographyapp.di

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.picpose.bestphotographyapp.BuildConfig
import com.picpose.bestphotographyapp.data.local.datastore.ReferralCodeCache
import com.picpose.bestphotographyapp.data.local.datastore.RewardsHubCache
import com.picpose.bestphotographyapp.core.network.ApiKeyInterceptor
import com.picpose.bestphotographyapp.core.network.AuthInterceptor
import com.picpose.bestphotographyapp.data.remote.api.V2ApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object V2NetworkModule {
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Provides
    @Singleton
    @V2Gson
    fun provideV2Gson(): Gson = GsonBuilder().create()

    @Provides
    @Singleton
    @V2Client
    fun provideV2OkHttpClient(
        apiKeyInterceptor: ApiKeyInterceptor,
        authInterceptor: AuthInterceptor,
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .addInterceptor(apiKeyInterceptor)
            .addInterceptor(authInterceptor)

        if (BuildConfig.DEBUG) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                }
            )
        }

        return builder.build()
    }

    @Provides
    @Singleton
    @V2Retrofit
    fun provideV2Retrofit(
        @V2Client okHttpClient: OkHttpClient,
        @V2Gson gson: Gson,
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun provideV2ApiService(@V2Retrofit retrofit: Retrofit): V2ApiService {
        return retrofit.create(V2ApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideRewardsHubCache(
        @ApplicationContext context: Context,
        @V2Gson gson: Gson,
    ): RewardsHubCache = RewardsHubCache(context, gson)

    @Provides
    @Singleton
    fun provideReferralCodeCache(
        @ApplicationContext context: Context,
    ): ReferralCodeCache = ReferralCodeCache(context)
}
