/**
 * ---
 * File: AuthRepositoryModule.kt
 * Layer: Dependency Injection
 * Project: PicPose
 *
 * Purpose:
 * Binds the domain auth repository contract to its data-layer implementation.
 * ---
 */

package com.picpose.bestphotographyapp.di

import com.picpose.bestphotographyapp.data.repository.AuthRepositoryImpl
import com.picpose.bestphotographyapp.domain.repository.AuthRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthRepositoryModule {
    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        impl: AuthRepositoryImpl
    ): AuthRepository
}

