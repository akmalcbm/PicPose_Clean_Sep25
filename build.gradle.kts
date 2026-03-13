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



// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
}
