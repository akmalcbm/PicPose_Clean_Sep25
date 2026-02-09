package com.picpose.bestphotographyapp.di

import android.content.Context
import com.picpose.bestphotographyapp.data.datastore.DeviceIdStore
import com.picpose.bestphotographyapp.data.datastore.SettingsManager
import com.picpose.bestphotographyapp.data.datastore.UserSessionManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for DataStore dependencies
 */
@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    @Provides
    @Singleton
    fun provideUserSessionManager(
        @ApplicationContext context: Context
    ): UserSessionManager {
        return UserSessionManager(context)
    }

    @Provides
    @Singleton
    fun provideSettingsManager(
        @ApplicationContext context: Context
    ): SettingsManager {
        return SettingsManager(context)
    }

    @Provides
    @Singleton
    fun provideDeviceIdStore(
        @ApplicationContext context: Context
    ): DeviceIdStore {
        return DeviceIdStore(context)
    }
}
