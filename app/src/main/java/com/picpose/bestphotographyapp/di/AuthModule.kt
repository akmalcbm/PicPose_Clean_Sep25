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
