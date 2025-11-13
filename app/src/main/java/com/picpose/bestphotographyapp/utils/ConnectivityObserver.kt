package com.picpose.bestphotographyapp.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class ConnectivityObserver(private val context: Context) {

    enum class Status {
        Available, Unavailable, Losing, Lost
    }

    fun observe(): Flow<Status> = callbackFlow {

        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val callback = object : ConnectivityManager.NetworkCallback() {

            override fun onAvailable(network: Network) {
                trySend(Status.Available)
            }

            override fun onLost(network: Network) {
                trySend(Status.Lost)
            }

            override fun onUnavailable() {
                trySend(Status.Unavailable)
            }

            override fun onLosing(network: Network, maxMsToLive: Int) {
                trySend(Status.Losing)
            }
        }

        connectivityManager.registerDefaultNetworkCallback(callback)

        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }
}
