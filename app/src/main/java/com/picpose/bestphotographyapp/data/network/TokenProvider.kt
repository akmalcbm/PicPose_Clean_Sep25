package com.picpose.bestphotographyapp.data.network

import android.util.Log
import com.picpose.bestphotographyapp.data.datastore.UserSessionManager
import com.picpose.bestphotographyapp.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Singleton
class TokenProvider @Inject constructor(
    private val userSessionManager: UserSessionManager,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {
    @Volatile
    private var latestToken: String? = null

    @Volatile
    private var observingJob: Job? = null

    fun start() {
        if (observingJob != null) return

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

    fun currentToken(): String? = latestToken

    companion object {
        private const val TAG = "TokenProvider"
    }
}
