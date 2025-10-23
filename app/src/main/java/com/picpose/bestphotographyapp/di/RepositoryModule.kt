package com.picpose.bestphotographyapp.di

import android.content.Context
import androidx.room.Room
import com.picpose.bestphotographyapp.data.database.AppDatabase
import com.picpose.bestphotographyapp.data.database.StatsDao
import com.picpose.bestphotographyapp.data.network.ApiService
import com.picpose.bestphotographyapp.data.network.RetrofitClient
import com.picpose.bestphotographyapp.data.repository.HomeRepository
import com.picpose.bestphotographyapp.data.repository.StatsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    // ✅ Provide Retrofit ApiService
    @Provides
    @Singleton
    fun provideApiService(): ApiService = RetrofitClient.apiService

    // ✅ Provide Room Database
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "picpose_database")
            .fallbackToDestructiveMigration()
            .build()

    // ✅ Provide DAO
    @Provides
    fun provideStatsDao(db: AppDatabase): StatsDao = db.statsDao()

    // ✅ Provide Repositories
    @Provides
    @Singleton
    fun provideStatsRepository(api: ApiService, dao: StatsDao): StatsRepository =
        StatsRepository(api, dao)

    @Provides
    @Singleton
    fun provideHomeRepository(@ApplicationContext context: Context): HomeRepository =
        HomeRepository(context)
}
