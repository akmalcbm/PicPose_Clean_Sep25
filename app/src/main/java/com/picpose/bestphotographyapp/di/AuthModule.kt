/**
 * ---
 * File: AuthModule.kt
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
import com.picpose.bestphotographyapp.data.remote.auth.FacebookAuthClient
import com.picpose.bestphotographyapp.data.remote.auth.GoogleAuthUiClient
import com.picpose.bestphotographyapp.data.remote.auth.TwitterAuthClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {

    @Provides
    @Singleton
    fun provideGoogleClient(@ApplicationContext context: Context): GoogleAuthUiClient =
        GoogleAuthUiClient(context)

    @Provides
    @Singleton
    fun provideFacebookClient(): FacebookAuthClient = FacebookAuthClient()

    @Provides
    @Singleton
    fun provideTwitterClient(): TwitterAuthClient = TwitterAuthClient()
}
