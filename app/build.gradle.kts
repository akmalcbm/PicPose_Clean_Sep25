/**
 * ---
 * File: build.gradle.kts
 * Layer: Build Configuration
 * Project: PicPose
 *
 * Purpose:
 * Declares Gradle build configuration, plugins, and module dependencies for the project.
 *
 * Interactions:
 * Works with nearby classes in the same layer to keep responsibilities separated and easier to maintain.
 *
 * Data Flow:
 * Feature-specific flow; see adjacent ViewModels and repositories for the full path.
 *
 * Maintainer Notes:
 * - Keep responsibilities narrow and update this header when the file grows into a larger abstraction.
 * ---
 */

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.tasks.bundling.Zip

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

hilt {
    // Keep Hilt aggregating task enabled so generated deps are tracked correctly by KSP.
    enableAggregatingTask = true
}

configurations.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "com.google.android.gms" &&
            requested.name.startsWith("play-services-measurement")
        ) {
            useVersion("23.0.0")
            because("Align Measurement artifacts to a single version to avoid duplicate classes")
        }
    }
}

android {
    namespace = "com.picpose.bestphotographyapp"
    compileSdk = 36
    val apiKey = (project.findProperty("PICPOSE_API_KEY") as String?)?.trim().orEmpty()
    val apiBaseUrl = (project.findProperty("PICPOSE_API_BASE_URL") as String?)?.trim()
        ?: "https://picpose.iamakmal.in/"

    defaultConfig {
        applicationId = "com.picpose.bestphotographyapp"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "3.0.0"

        // API key must come from local/CI gradle properties.
        buildConfigField(
            "String",
            "API_KEY",
            "\"$apiKey\""
        )
        buildConfigField(
            "String",
            "API_BASE_URL",
            "\"$apiBaseUrl\""
        )
        buildConfigField(
            "String",
            "REFERRAL_PLAY_URL",
            "\"https://play.google.com/store/apps/details?id=com.picpose.bestphotographyapp\""
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            // Enable resource shrinking along with code shrinking
            isShrinkResources = true
            ndk {
                // Generate native debug symbols for Play Console Native debug symbols upload.
                // Use "SYMBOL_TABLE" instead of "FULL" to reduce symbol artifact size.
                debugSymbolLevel = "FULL"
            }

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Ensure BuildConfig is generated for release builds too
            buildConfigField(
                "String",
                "API_KEY",
                "\"$apiKey\""
            )
            buildConfigField(
                "String",
                "API_BASE_URL",
                "\"$apiBaseUrl\""
            )
            buildConfigField(
                "String",
                "REFERRAL_PLAY_URL",
                "\"https://play.google.com/store/apps/details?id=com.picpose.bestphotographyapp\""
            )
            manifestPlaceholders["crashlyticsCollectionEnabled"] = "true"
        }
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
            // BuildConfig field is inherited from defaultConfig, but you can override if needed
            manifestPlaceholders["crashlyticsCollectionEnabled"] = "false"
        }

    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        jniLibs {
            // These dependency-provided .so files are not strip-compatible in release.
            // Keep symbols to avoid strip warnings/failures in :app:stripReleaseDebugSymbols.
            keepDebugSymbols += setOf(
                "**/libandroidx.graphics.path.so",
                "**/libdatastore_shared_counter.so"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    firebaseCrashlytics {
        mappingFileUploadEnabled = true
    }
}

composeCompiler {
    enableStrongSkippingMode = true
}

// Add this for memory optimization
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:unchecked", "-Xlint:deprecation"))
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            listOf(
                "-opt-in=kotlin.RequiresOptIn",
                "-Xjvm-default=all",
                "-Xstring-concat=inline"
            )
        )
    }
}

val packageReleaseNativeDebugSymbols by tasks.registering(Zip::class) {
    group = "distribution"
    description = "Packages release native debug symbols for Play Console upload."
    dependsOn("mergeReleaseNativeLibs")

    from(layout.buildDirectory.dir("intermediates/merged_native_libs/release/mergeReleaseNativeLibs/out/lib")) {
        into("lib")
    }

    destinationDirectory.set(layout.buildDirectory.dir("outputs/native-debug-symbols/release"))
    archiveFileName.set("native-debug-symbols.zip")
}

tasks.matching { it.name == "bundleRelease" || it.name == "packageReleaseBundle" }.configureEach {
    finalizedBy(packageReleaseNativeDebugSymbols)
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.play.services.measurement.api)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // Firebase & Authentication
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation(libs.play.services.auth)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.google.identity)
    // Firebase Messaging (FCM)
    implementation(libs.firebase.messaging)

    // Activity & Lifecycle
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Navigation
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    // Splash Screen
    implementation(libs.androidx.core.splashscreen)

    implementation(libs.coil.compose)
    implementation(libs.lottie.compose)

    // Retrofit + Gson
    implementation(libs.retrofit2)
    implementation(libs.converter.gson)

    //OKHttp
    implementation(libs.okhttp.logging.interceptor)

    // ⭐ OkHttp core (needed for Twitter token exchange)
    implementation(libs.okhttp3)

    // ⭐ Moshi (Twitter JSON parsing)
    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)

    // ⭐ Browser custom tabs (launch Twitter OAuth)
    implementation(libs.androidx.browser)

    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation("com.github.yalantis:ucrop:2.2.8")

    implementation(libs.play.services.ads)         // Google AdMob
    implementation(libs.facebook.audience.network) // Meta Audience Network

    implementation(libs.facebook.login)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Hilt
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    // Window Size
    implementation(libs.androidx.compose.material3.window.size)

    // Tests
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
