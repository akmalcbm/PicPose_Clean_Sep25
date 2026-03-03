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
