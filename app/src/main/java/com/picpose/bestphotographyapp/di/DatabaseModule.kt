package com.picpose.bestphotographyapp.di

import android.content.Context
import com.picpose.bestphotographyapp.data.database.AppDatabase
import com.picpose.bestphotographyapp.data.database.dao.EngagementDao
import com.picpose.bestphotographyapp.data.database.FavoritePromptDao
import com.picpose.bestphotographyapp.data.database.LikedPromptDao
import com.picpose.bestphotographyapp.data.database.StatsDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    // 🔹 Database
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    // 🔹 DAOs
    @Provides
    fun provideEngagementDao(
        db: AppDatabase
    ): EngagementDao = db.engagementDao()

    @Provides
    fun provideFavoritePromptDao(
        db: AppDatabase
    ): FavoritePromptDao = db.favoriteDao()

    @Provides
    fun provideLikedPromptDao(
        db: AppDatabase
    ): LikedPromptDao = db.likedPromptDao()

    @Provides
    fun provideStatsDao(
        db: AppDatabase
    ): StatsDao = db.statsDao()
}
