package dev.gavenda.yuuka.data.local

import dev.gavenda.yuuka.data.local.entity.AccountEntity
import dev.gavenda.yuuka.data.local.entity.AccountTypeEntity
import dev.gavenda.yuuka.data.local.entity.CategoryEntity
import dev.gavenda.yuuka.data.local.entity.SubscriptionEntity
import dev.gavenda.yuuka.data.local.entity.TagEntity
import dev.gavenda.yuuka.data.local.entity.TransactionEntity
import java.time.Instant

/**
 * The row written before the server has seen it.
 *
 * Every screen reads from Room, so a change has to land there immediately or it
 * does not appear until the network answers — which, offline, is never. These
 * builders are that row: what the user typed, plus the fields the API would
 * have filled in (the account's name, the category's colour, a balance that has
 * moved) worked out from the cache already on the device.
 *
 * Every figure here is a good guess, never the truth. Once the outbox drains,
 * the slices the change touched are refetched and whatever these worked out is
 * replaced by what the server has. What they buy is the moment in between, and
 * two rules keep that moment honest:
 *
 * - **Guess the same way the server does.** A round-up computed differently
 *   here than in `server/routes/transactions.ts` would show one figure and save
 *   another. [roundUpFor] is deliberately the same arithmetic, and so is the
 *   web app's `roundUpFor` in `src/lib/provisional.ts`.
 * - **Never guess what only the server knows.** A running balance depends on
 *   every row in the account in date order, and a summary on the whole month.
 *   Those are left at their best local value and corrected on refresh rather
 *   than invented.
 */
object Provisional {
    private fun now(): String = Instant.now().toString()

    /** Synthetic, and never taught to the payee history — as a transfer's composed name is not. */
    const val ROUND_UP_PAYEE = "Save the Change"

    fun transaction(
        id: String,
        accountId: String,
        account: AccountEntity?,
        category: CategoryEntity?,
        amount: Long,
        occurredOn: String,
        payee: String,
        notes: String,
        transferId: String? = null,
    ): TransactionEntity = TransactionEntity(
        id = id,
        accountId = accountId,
        accountName = account?.name,
        categoryId = category?.id,
        categoryName = category?.name,
        categoryColor = category?.color,
        amount = amount,
        occurredOn = occurredOn,
        payee = payee,
        notes = notes,
        transferId = transferId,
        // A schedule posts automated rows, and a schedule is the server.
        automated = false,
        // Right for the newest row on the account, approximate for one
        // back-dated into the middle of its history — corrected on the refresh.
        runningBalance = (account?.balance ?: 0L) + amount,
        createdAt = now(),
        updatedAt = now(),
    )

    /**
     * What "Save the Change" would move, given an expense.
     *
     * The gap between the amount spent and the next multiple up. An amount
     * already on a multiple rounds up by nothing, which is why zero means "no
     * round-up" rather than "a round-up of zero".
     */
    fun roundUpFor(amount: Long, roundTo: Long): Long {
        if (amount >= 0 || roundTo <= 0) return 0

        val remainder = (-amount) % roundTo
        return if (remainder == 0L) 0 else roundTo - remainder
    }

    /** A transfer's name when none was typed. Composed the same way the API composes it. */
    fun transferName(from: AccountEntity?, to: AccountEntity?): String = "${from?.name ?: "—"} → ${to?.name ?: "—"}"

    fun account(
        id: String,
        name: String,
        typeId: String,
        type: AccountTypeEntity?,
        currency: String,
        startingBalance: Long,
        logoUrl: String?,
        logoInvertDark: Boolean,
        roundUpSource: Boolean,
    ): AccountEntity = AccountEntity(
        id = id,
        name = name,
        typeId = typeId,
        typeName = type?.name,
        currency = currency,
        logoUrl = logoUrl,
        logoInvertDark = logoInvertDark,
        roundUpSource = roundUpSource,
        startingBalance = startingBalance,
        // Nothing has posted to it yet, so its balance is what it opened with.
        balance = startingBalance,
        archived = false,
        createdAt = now(),
        updatedAt = now(),
    )

    fun accountType(id: String, name: String, sortOrder: Int): AccountTypeEntity = AccountTypeEntity(
        id = id,
        name = name,
        sortOrder = sortOrder,
        archived = false,
        accountCount = 0,
        createdAt = now(),
        updatedAt = now(),
    )

    /**
     * A child inherits its parent's kind, which is a quiet correction on the
     * server rather than an error — so it is applied here too, otherwise a
     * subcategory would show one kind and save another.
     */
    fun category(id: String, name: String, kind: String, color: String, parent: CategoryEntity?): CategoryEntity = CategoryEntity(
        id = id,
        name = name,
        kind = parent?.kind ?: kind,
        color = color,
        sortOrder = 0,
        archived = false,
        parentId = parent?.id,
        createdAt = now(),
        updatedAt = now(),
    )

    fun tag(id: String, name: String, color: String): TagEntity = TagEntity(
        id = id,
        name = name,
        color = color,
        transactionCount = 0,
        createdAt = now(),
        updatedAt = now(),
    )

    /**
     * The start date's day of the month is the anchor for every later run, and
     * the first run is the start date itself — the API refuses a date already
     * past, so there is never a backlog to work out here.
     */
    fun subscription(
        id: String,
        accountId: String,
        account: AccountEntity?,
        category: CategoryEntity?,
        amount: Long,
        payee: String,
        notes: String,
        startOn: String,
    ): SubscriptionEntity = SubscriptionEntity(
        id = id,
        accountId = accountId,
        accountName = account?.name,
        categoryId = category?.id,
        categoryName = category?.name,
        categoryColor = category?.color,
        amount = amount,
        payee = payee,
        notes = notes,
        startOn = startOn,
        dayOfMonth = startOn.substring(8, 10).toInt(),
        nextRunOn = startOn,
        lastRunOn = null,
        enabled = true,
        createdAt = now(),
        updatedAt = now(),
    )

    /** Moves an existing row's `updatedAt` as a write would, so a later edit is not judged stale against it. */
    fun touchedAt(): String = now()
}
