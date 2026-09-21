package dev.gavenda.yuuka.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

private const val WORK_NAME = "yuuka.outbox-flush"

/**
 * Sends the queue when there is a network, whether or not the app is open.
 *
 * [Outbox.flush] already runs on every write and whenever [NetworkMonitor] sees
 * a connection come back, and between them they cover the case of someone
 * holding the app. They do not cover the common one: changes entered on a
 * plane, the app closed, and a connection arriving an hour later on the ground.
 * WorkManager does, because its `CONNECTED` constraint survives the process
 * being killed and the device rebooting.
 *
 * The constraint is `CONNECTED` rather than `UNMETERED`: a batch of queued
 * writes is a few kilobytes, and waiting for Wi-Fi to save it would be the
 * wrong trade for a ledger the user expects to be up to date.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters,
    private val outbox: Outbox,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // `flush` reports false when something is still waiting — the API was
        // out of reach, or an operation failed in a way worth retrying. Retry
        // backs off exponentially rather than hammering a server that is down.
        return if (outbox.flush()) Result.success() else Result.retry()
    }

    companion object {
        /**
         * Asks for a flush as soon as there is a network.
         *
         * `KEEP` rather than `REPLACE`: a hundred changes made offline should
         * schedule one flush, not restart the same one a hundred times and push
         * it back each time.
         */
        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
