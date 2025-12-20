package com.picpose.bestphotographyapp.di

import android.content.Context
import com.picpose.bestphotographyapp.data.network.ApiService
import com.picpose.bestphotographyapp.data.network.RetrofitClient
import com.picpose.bestphotographyapp.data.repository.ExploreRepository
import com.picpose.bestphotographyapp.data.repository.HomeRepository
import com.picpose.bestphotographyapp.data.repository.StatsRepository
import com.picpose.bestphotographyapp.data.database.StatsDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    // ✅ Retrofit API
    @Provides
    @Singleton
    fun provideApiService(): ApiService =
        RetrofitClient.apiService

    // ✅ Stats Repository
    @Provides
    @Singleton
    fun provideStatsRepository(
        api: ApiService,
        dao: StatsDao
    ): StatsRepository {
        return StatsRepository(api, dao)
    }

    // ✅ Home Repository
    @Provides
    @Singleton
    fun provideHomeRepository(
        @ApplicationContext context: Context
    ): HomeRepository {
        return HomeRepository(
            context = context
        )
    }


    // ✅ Explore Repository
    @Provides
    @Singleton
    fun provideExploreRepository(
        @ApplicationContext context: Context
    ): ExploreRepository {
        return ExploreRepository(
            context = context)
    }
}
