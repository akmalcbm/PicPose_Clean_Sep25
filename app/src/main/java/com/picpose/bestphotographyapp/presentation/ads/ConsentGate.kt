/**
 * ---
 * File: ConsentGate.kt
 * Layer: Presentation (UI)
 * Project: PicPose
 *
 * Purpose:
 * Supports the PicPose app with feature-specific models, helpers, or UI building blocks.
 *
 * Interactions:
 * Works with nearby classes in the same layer to keep responsibilities separated and easier to maintain.
 *
 * Data Flow:
 * Feature-specific flow; see adjacent ViewModels and repositories for the full path.
 *
 * Maintainer Notes:
 * - Keep SDK-specific code isolated here so feature screens remain testable.
 * - TODO: Add analytics and remote-config driven rollout controls where appropriate.
 * ---
 */

package com.picpose.bestphotographyapp.presentation.ads

import com.picpose.bestphotographyapp.BuildConfig
import java.util.concurrent.atomic.AtomicReference

interface ConsentGate {
    suspend fun isReady(): Boolean
}

/**
 * Default stub behavior:
 * - Debug builds: true (developer-friendly)
 * - Release builds: false until CMP integration sets an override
 */
class DefaultConsentGate(
    initialOverride: Boolean? = null
) : ConsentGate {

    private val override = AtomicReference<Boolean?>(initialOverride)

    fun setOverride(ready: Boolean?) {
        override.set(ready)
    }

    override suspend fun isReady(): Boolean {
        val forced = override.get()
        if (forced != null) return forced
        return BuildConfig.DEBUG
    }
}
