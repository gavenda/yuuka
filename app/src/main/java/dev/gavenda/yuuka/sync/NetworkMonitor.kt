package dev.gavenda.yuuka.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Whether there is a network worth trying.
 *
 * `NET_CAPABILITY_VALIDATED` is the reason this is a callback rather than a
 * flag: a device attached to a captive portal or a router with no route out
 * reports a connection, and sending a batch into that wastes the attempt and
 * marks every queued row as failed. Validated means Android actually reached
 * the internet through it.
 *
 * It is still only a hint. Nothing refuses to try because this says false — a
 * flush is attempted regardless and an unreachable API leaves the queue as it
 * was. What this is for is knowing *when to bother trying again*, which is the
 * moment the queue drains after a flight.
 */
class NetworkMonitor(context: Context) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)

    val isOnline: Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(validated(network))
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
            }

            override fun onLost(network: Network) {
                trySend(false)
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        manager.registerNetworkCallback(request, callback)
        trySend(validated(manager.activeNetwork))

        awaitClose { manager.unregisterNetworkCallback(callback) }
    }
        .conflate()
        .distinctUntilChanged()

    private fun validated(network: Network?): Boolean =
        network != null &&
            manager.getNetworkCapabilities(network)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
}
