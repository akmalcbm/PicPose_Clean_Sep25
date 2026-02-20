package com.picpose.bestphotographyapp.di

import android.content.Context
import com.picpose.bestphotographyapp.core.crash.CrashReporter
import com.picpose.bestphotographyapp.data.datastore.DeviceIdStore
import com.picpose.bestphotographyapp.data.network.ApiService
import com.picpose.bestphotographyapp.data.network.AdsApiService
import com.picpose.bestphotographyapp.data.network.RetrofitClient
import com.picpose.bestphotographyapp.data.repository.AdsRepository
import com.picpose.bestphotographyapp.data.repository.ExploreRepository
import com.picpose.bestphotographyapp.data.repository.HomeRepository
import com.picpose.bestphotographyapp.data.repository.StatsRepository
import com.picpose.bestphotographyapp.data.database.StatsDao
import com.picpose.bestphotographyapp.presentation.ads.AdsConfigCache
import com.picpose.bestphotographyapp.presentation.ads.AdsFrequencyManager
import com.picpose.bestphotographyapp.presentation.ads.AdsManager
import com.picpose.bestphotographyapp.presentation.ads.ConsentGate
import com.picpose.bestphotographyapp.presentation.ads.DefaultConsentGate
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

    @Provides
    @Singleton
    fun provideAdsApiService(): AdsApiService =
        RetrofitClient.createService(AdsApiService::class.java)

    @Provides
    @Singleton
    fun provideAdsConfigCache(
        @ApplicationContext context: Context
    ): AdsConfigCache = AdsConfigCache(context)

    @Provides
    @Singleton
    fun provideAdsRepository(
        adsApiService: AdsApiService,
        adsConfigCache: AdsConfigCache,
        deviceIdStore: DeviceIdStore
    ): AdsRepository = AdsRepository(
        api = adsApiService,
        cache = adsConfigCache,
        deviceIdStore = deviceIdStore
    )

    @Provides
    @Singleton
    fun provideAdsFrequencyManager(): AdsFrequencyManager = AdsFrequencyManager

    @Provides
    @Singleton
    fun provideConsentGate(): ConsentGate = DefaultConsentGate()

    @Provides
    @Singleton
    fun provideAdsManager(): AdsManager = AdsManager

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
        @ApplicationContext context: Context,
        crashReporter: CrashReporter
    ): HomeRepository {
        return HomeRepository(
            context = context,
            crashReporter = crashReporter
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
