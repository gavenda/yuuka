package dev.gavenda.yuuka.sync

/**
 * The named parts of the ledger a client can refresh on its own.
 *
 * These are the words a push speaks in, and the words the outbox uses to say
 * what to reload once a change has landed. A slice is not a row: [TRANSACTIONS]
 * means "the pages and months you are holding are out of date", not any
 * particular transaction.
 *
 * The server keeps the same list in `server/notify.ts` and the web app in
 * `src/lib/slices.ts`, along with the same mapping from a path to what a write
 * there disturbs. All three have to agree, so keep them together.
 */
object Slices {
    const val ACCOUNTS = "accounts"
    const val ACCOUNT_TYPES = "accountTypes"
    const val CATEGORIES = "categories"
    const val TAGS = "tags"
    const val TRANSACTIONS = "transactions"
    const val BUDGETS = "budgets"
    const val INCOME_PLAN = "incomePlan"
    const val SUBSCRIPTIONS = "subscriptions"
    const val SETTINGS = "settings"
    const val ROUND_UP_RULE = "roundUpRule"
    const val PAYEES = "payees"
    const val SUMMARY = "summary"

    private val all = setOf(
        ACCOUNTS, ACCOUNT_TYPES, CATEGORIES, TAGS, TRANSACTIONS,
        BUDGETS, INCOME_PLAN, SUBSCRIPTIONS, SETTINGS, ROUND_UP_RULE, PAYEES, SUMMARY,
    )

    /**
     * What a write to this path disturbs.
     *
     * The blast radius is wider than the row that changed, deliberately: a
     * transaction moves an account's balance and the month's summary, and
     * renaming a category changes what every transaction card reads. Saying too
     * much costs a refresh the app would have done on its next open anyway;
     * saying too little leaves a figure wrong on screen with nothing to correct
     * it.
     */
    fun forPath(path: String): List<String> = when {
        path.startsWith("/api/account-types") -> listOf(ACCOUNT_TYPES, ACCOUNTS)
        // Includes `/accounts/{id}/adjust`, which posts a transaction like any other.
        path.startsWith("/api/accounts") -> listOf(ACCOUNTS, TRANSACTIONS, SUMMARY, PAYEES)
        path.startsWith("/api/categories") -> listOf(CATEGORIES, TRANSACTIONS, BUDGETS, SUMMARY)
        path.startsWith("/api/tags") -> listOf(TAGS, TRANSACTIONS)
        path.startsWith("/api/transactions") -> listOf(TRANSACTIONS, ACCOUNTS, SUMMARY, PAYEES)
        path.startsWith("/api/budgets") -> listOf(BUDGETS, SUMMARY)
        path.startsWith("/api/income-plan") -> listOf(INCOME_PLAN, SUMMARY)
        path.startsWith("/api/subscriptions") -> listOf(SUBSCRIPTIONS)
        path.startsWith("/api/settings") -> listOf(SETTINGS, SUMMARY)
        path.startsWith("/api/round-up") -> listOf(ROUND_UP_RULE)
        path.startsWith("/api/payees") -> listOf(PAYEES)
        else -> emptyList()
    }

    /** Reads a slice list off a push payload, ignoring anything this build does not recognise. */
    fun parse(value: String?): List<String> =
        value.orEmpty().split(',').map { it.trim() }.filter { it in all }
}
