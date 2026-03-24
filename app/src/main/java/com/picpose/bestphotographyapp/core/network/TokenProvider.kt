/**
 * ---
 * File: TokenProvider.kt
 * Layer: Data
 * Project: PicPose
 *
 * Purpose:
 * Configures networking behavior such as authentication, API keys, or Retrofit client creation.
 *
 * Interactions:
 * Consumed by repositories to talk to backend APIs and map raw payloads into app models.
 *
 * Data Flow:
 * Repository -> Retrofit service -> Backend response -> Model mapping -> ViewModel/UI
 *
 * Maintainer Notes:
 * - Prefer backend-neutral mapping in repositories instead of leaking transport details into the UI.
 * - TODO: Add stricter error classification and retry policy where network flows are user-critical.
 * ---
 */

package com.picpose.bestphotographyapp.core.network

import android.util.Log
import com.picpose.bestphotographyapp.data.local.datastore.UserSessionManager
import com.picpose.bestphotographyapp.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@Singleton
class TokenProvider @Inject constructor(
    private val userSessionManager: UserSessionManager,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {
    @Volatile
    private var latestToken: String? = null

    @Volatile
    private var observingJob: Job? = null

    @Volatile
    private var hasHydratedFromStore: Boolean = false

    @Volatile
    private var lastSynchronousReadAtMs: Long = 0L

    fun start() {
        if (observingJob != null) return

        hydrateTokenFromStoreIfNeeded(force = false)

        observingJob = applicationScope.launch {
            userSessionManager.userToken
                .distinctUntilChanged()
                .collect { token ->
                    latestToken = token?.takeIf { it.isNotBlank() }
                    if (latestToken == null) {
                        Log.d(TAG, "Authorization token cleared")
                    }
                }
        }
    }

    fun currentToken(): String? {
        val cached = latestToken?.takeIf { it.isNotBlank() }
        if (cached != null) return cached

        // Critical for first protected calls right after login:
        // if the async collector has not observed DataStore yet, do a direct read.
        hydrateTokenFromStoreIfNeeded(force = true)
        val refreshed = latestToken?.takeIf { it.isNotBlank() }
        if (refreshed != null) return refreshed

        // Last fallback for edge timing where throttle blocked the force path.
        val blockingRead = runCatching {
            runBlocking { userSessionManager.getUserTokenOnce() }
        }.getOrNull()?.takeIf { it.isNotBlank() }
        if (!blockingRead.isNullOrBlank()) {
            latestToken = blockingRead
            return blockingRead
        }
        return null
    }

    private fun hydrateTokenFromStoreIfNeeded(force: Boolean) {
        if (!force && hasHydratedFromStore) return
        synchronized(this) {
            val now = System.currentTimeMillis()
            if (force && (now - lastSynchronousReadAtMs) < FORCED_READ_THROTTLE_MS) return
            if (!force && hasHydratedFromStore) return

            latestToken = runCatching {
                runBlocking { userSessionManager.getUserTokenOnce() }
            }.getOrNull()?.takeIf { it.isNotBlank() }
            hasHydratedFromStore = true
            lastSynchronousReadAtMs = now
        }
    }

    companion object {
        private const val TAG = "TokenProvider"
        private const val FORCED_READ_THROTTLE_MS = 250L
    }
}
