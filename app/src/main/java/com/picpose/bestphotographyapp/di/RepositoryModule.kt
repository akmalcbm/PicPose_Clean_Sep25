package com.picpose.bestphotographyapp.di

import android.content.Context
import com.picpose.bestphotographyapp.data.repository.HomeRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideHomeRepository(
        @ApplicationContext context: Context
    ): HomeRepository {
        return HomeRepository(context)
    }
}