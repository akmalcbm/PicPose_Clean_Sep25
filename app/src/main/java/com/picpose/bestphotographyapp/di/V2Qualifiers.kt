/**
 * ---
 * File: V2Qualifiers.kt
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

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class V2Client

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class V2Retrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class V2Gson
