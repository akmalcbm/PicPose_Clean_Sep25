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
