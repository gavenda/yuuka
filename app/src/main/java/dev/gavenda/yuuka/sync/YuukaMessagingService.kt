package dev.gavenda.yuuka.sync

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dev.gavenda.yuuka.repository.SyncRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Where a change made on another device arrives.
 *
 * The messages are data-only and nothing is ever shown. The user does not need
 * telling — they made the change themselves, in the web app or on another
 * phone — and a notification saying so would be noise. What arrives is a list
 * of slice names, and the answer is to refetch exactly those: a transaction
 * added in the browser costs this device its transactions, accounts and the
 * month's summary, not a full sync.
 *
 * The message is a hint and nothing may depend on it arriving. FCM makes no
 * delivery promise, the device may be dozing, and the app still syncs on open,
 * on reconnect and on pull-to-refresh regardless.
 *
 * A message also arrives when the token is rotated, which is the other half of
 * [PushRegistrar]: a rotated token that is not re-registered silently stops
 * this device being reachable.
 */
class YuukaMessagingService : FirebaseMessagingService() {
    private val sync: SyncRepository by inject()
    private val outbox: Outbox by inject()
    private val registrar: PushRegistrar by inject()

    // The service is torn down as soon as the callback returns, so the work
    // cannot hang off its lifecycle; this scope outlives it for as long as the
    // refresh takes.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(message: RemoteMessage) {
        val slices = Slices.parse(message.data["slices"])
        if (slices.isEmpty()) return

        scope.launch {
            // Anything of ours that has not been sent goes first: refreshing the
            // slices would otherwise overwrite the rows the user is waiting to
            // have saved with the server's older copy of them.
            outbox.flush()
            sync.refreshSlices(slices)
        }
    }

    override fun onNewToken(token: String) {
        scope.launch { registrar.register(token) }
    }
}
