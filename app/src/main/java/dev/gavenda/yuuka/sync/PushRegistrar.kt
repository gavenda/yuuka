package dev.gavenda.yuuka.sync

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import dev.gavenda.yuuka.data.remote.YuukaApi
import dev.gavenda.yuuka.data.remote.apiCall
import dev.gavenda.yuuka.data.remote.dto.DeviceRegistrationRequest
import kotlinx.coroutines.tasks.await

private const val TAG = "PushRegistrar"

/**
 * Tells the server where to reach this install.
 *
 * A change made in the web app leaves this device's cache quietly wrong until
 * something makes it ask again. Polling would cost a request a minute to catch
 * a change a day, so the server pushes instead: a data-only message naming
 * which slices of the ledger moved, handled by [YuukaMessagingService].
 *
 * Everything here is best-effort and deliberately quiet about failing. Push is
 * an optimisation over the syncing the app already does on open, on reconnect
 * and on pull-to-refresh; a device that never registers is only slower to
 * notice, never wrong. A build with no `google-services.json` has no Firebase
 * to ask, and that is a supported way to run the app.
 */
class PushRegistrar(
    private val api: YuukaApi,
    private val deviceId: DeviceId,
) {
    /** The token last registered, so signing out can give exactly that one up. */
    @Volatile
    private var registered: String? = null

    /** Called once a session exists — a token is useless before the server knows whose it is. */
    suspend fun register() {
        val token = try {
            FirebaseMessaging.getInstance().token.await()
        } catch (error: Exception) {
            Log.i(TAG, "No messaging token; this device will sync without push.", error)
            return
        }

        register(token)
    }

    /** Also called by [YuukaMessagingService] when FCM rotates the token behind us. */
    suspend fun register(token: String) {
        try {
            apiCall { api.registerDevice(DeviceRegistrationRequest(token = token, deviceId = deviceId.value)) }
            registered = token
        } catch (error: Exception) {
            Log.i(TAG, "Could not register for push; will try again on next start.", error)
        }
    }

    /**
     * Gives up this install's registration.
     *
     * Signing out has to unregister, or the next push would wake a device whose
     * cache has been cleared and whose session is gone — and, on a shared phone,
     * would be about a ledger the person now holding it cannot see.
     */
    suspend fun unregister() {
        val token = registered ?: return
        registered = null

        try {
            apiCall { api.unregisterDevice(token) }
        } catch (error: Exception) {
            // The server prunes a token it cannot deliver to, so a failure here
            // costs at most one undeliverable push.
            Log.i(TAG, "Could not unregister for push.", error)
        }
    }
}
