package dev.gavenda.yuuka.data

import java.util.UUID

/**
 * Naming a row before the server has seen it.
 *
 * Offline-first means a row exists, and is referenced by later rows, before it
 * has ever been sent: a transaction entered on a train names an account that
 * may itself still be queued. The client therefore names what it creates, and
 * the server stores the name it was given — so a queued transaction's
 * `accountId` is still right when it finally lands, with no second pass to
 * rewrite references.
 *
 * The shape is the server's own (`server/ids.ts`) and the web app's
 * (`src/lib/ids.ts`): a prefix, an underscore and a hex string. Nothing
 * downstream can tell which side generated an id, which is the point — there
 * is no "provisional id" to leak into the schema or the cache.
 */
object Ids {
    const val ACCOUNT = "acc"
    const val ACCOUNT_TYPE = "atp"
    const val CATEGORY = "cat"
    const val TAG = "tag"
    const val TRANSACTION = "txn"
    const val TRANSFER = "tfr"
    const val SUBSCRIPTION = "sub"
    const val BUDGET = "bdg"

    /** A new id of this kind, e.g. `txn_9f1c…`. */
    fun new(prefix: String): String = "${prefix}_${UUID.randomUUID().toString().replace("-", "")}"
}
