/**
 * ---
 * File: DataStoreModule.kt
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
import com.picpose.bestphotographyapp.data.local.datastore.DeviceIdStore
import com.picpose.bestphotographyapp.data.local.datastore.SettingsManager
import com.picpose.bestphotographyapp.data.local.datastore.UserSessionManager
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
