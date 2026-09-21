package dev.gavenda.yuuka.repository

import dev.gavenda.yuuka.data.Ids
import dev.gavenda.yuuka.data.local.Provisional
import dev.gavenda.yuuka.data.local.dao.*
import dev.gavenda.yuuka.data.local.toDomain
import dev.gavenda.yuuka.data.local.toEntity
import dev.gavenda.yuuka.sync.Outbox
import dev.gavenda.yuuka.data.model.*
import dev.gavenda.yuuka.data.remote.YuukaApi
import dev.gavenda.yuuka.data.remote.apiCall
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Accounts, account types, categories and settings change rarely but are
 * needed by nearly every screen, so they are loaded once, cached in Room, and
 * shared from there — the Compose equivalent of the web app's `ledger` Pinia
 * store, `src/stores/ledger.ts`.
 *
 * Every screen observes [accounts]/[accountTypes]/[categories]/[settings]
 * directly from Room; [refreshAll] and the narrower `refresh*` calls are what
 * pull a fresh copy from the API into that cache. A mutation (create/update/
 * delete) always re-syncs the affected list afterwards, the same way the Pinia
 * store re-fetches rather than patching its own state optimistically.
 *
 * Writes are local-first. A mutation writes the row it drew straight into Room
 * and hands the call to the [Outbox], which sends it when there is a
 * connection; it does not wait for the network, so a screen updates at the
 * speed of the database whether there is one or not. The server's answer
 * arrives later, as a refresh of the slices the change touched, and replaces
 * whatever was guessed here — see `sync/Outbox.kt`.
 */
class LedgerRepository(
    private val api: YuukaApi,
    private val outbox: Outbox,
    private val accountDao: AccountDao,
    private val accountTypeDao: AccountTypeDao,
    private val categoryDao: CategoryDao,
    private val settingsDao: SettingsDao,
    private val roundUpRuleDao: RoundUpRuleDao,
    private val tagDao: TagDao,
    private val transactionDao: TransactionDao,
) {
    val accounts: Flow<List<Account>> = accountDao.observeAll().map { list -> list.map { it.toDomain() } }
    val accountTypes: Flow<List<AccountType>> = accountTypeDao.observeAll().map { list -> list.map { it.toDomain() } }
    val categories: Flow<List<Category>> = categoryDao.observeAll().map { list -> list.map { it.toDomain() } }
    val tags: Flow<List<Tag>> = tagDao.observeAll().map { list -> list.map { it.toDomain() } }
    val settings: Flow<Settings?> = settingsDao.observe().map { it?.toDomain() }
    val roundUpRule: Flow<RoundUpRule?> = roundUpRuleDao.observe().map { it?.toDomain() }

    suspend fun refreshAll() = coroutineScope {
        launch { refreshAccounts() }
        launch { refreshCategories() }
        launch { refreshTags() }
        launch { refreshSettings() }
        launch { refreshRoundUpRule() }
    }

    suspend fun refreshAccounts() = coroutineScope {
        // Types come along: renaming one changes what every account displays.
        val accountsDeferred = async { apiCall { api.listAccounts(includeArchived = true) } }
        val typesDeferred = async { apiCall { api.listAccountTypes(includeArchived = true) } }
        accountDao.replaceAll(accountsDeferred.await().accounts.map { it.toEntity() })
        accountTypeDao.replaceAll(typesDeferred.await().accountTypes.map { it.toEntity() })
    }

    suspend fun refreshCategories() {
        val response = apiCall { api.listCategories(includeArchived = true) }
        categoryDao.replaceAll(response.categories.map { it.toEntity() })
    }

    suspend fun refreshTags() {
        val response = apiCall { api.listTags() }
        tagDao.replaceAll(response.tags.map { it.toEntity() })
    }

    suspend fun refreshSettings() {
        val response = apiCall { api.getSettings() }
        settingsDao.upsert(response.settings.toEntity())
    }

    suspend fun updateSettings(displayCurrency: String? = null, budgetMode: String? = null, defaultAccountId: String? = null, clearDefaultAccount: Boolean = false) {
        val body = buildJsonObject {
            displayCurrency?.let { put("displayCurrency", it) }
            budgetMode?.let { put("budgetMode", it) }
            if (clearDefaultAccount) put("defaultAccountId", null as String?) else defaultAccountId?.let { put("defaultAccountId", it) }
        }
        // The row on screen moves now; the API is told when there is a
        // connection. Naming the entity is what lets the server drop this edit
        // if a newer one reached it first.
        settingsDao.current()?.let { current ->
            settingsDao.upsert(
                current.copy(
                    displayCurrency = displayCurrency ?: current.displayCurrency,
                    budgetMode = budgetMode ?: current.budgetMode,
                    defaultAccountId = if (clearDefaultAccount) null else defaultAccountId ?: current.defaultAccountId,
                    updatedAt = Provisional.touchedAt(),
                ),
            )
        }
        outbox.enqueue("PATCH", "/api/settings", body, entity = "settings")
    }

    suspend fun refreshRoundUpRule() {
        val response = apiCall { api.getRoundUpRule() }
        roundUpRuleDao.upsert(response.roundUpRule.toEntity())
    }

    suspend fun updateRoundUpRule(
        enabled: Boolean? = null,
        roundTo: Long? = null,
        destinationAccountId: String? = null,
        clearDestination: Boolean = false,
        categoryId: String? = null,
        clearCategory: Boolean = false,
    ) {
        val body = buildJsonObject {
            enabled?.let { put("enabled", it) }
            roundTo?.let { put("roundTo", it) }
            if (clearDestination) put("destinationAccountId", null as String?) else destinationAccountId?.let { put("destinationAccountId", it) }
            if (clearCategory) put("categoryId", null as String?) else categoryId?.let { put("categoryId", it) }
        }
        roundUpRuleDao.current()?.let { current ->
            roundUpRuleDao.upsert(
                current.copy(
                    enabled = enabled ?: current.enabled,
                    roundTo = roundTo ?: current.roundTo,
                    destinationAccountId = if (clearDestination) null else destinationAccountId ?: current.destinationAccountId,
                    categoryId = if (clearCategory) null else categoryId ?: current.categoryId,
                    updatedAt = Provisional.touchedAt(),
                ),
            )
        }
        outbox.enqueue("PATCH", "/api/round-up", body, entity = "roundUpRule")
    }

    suspend fun createAccountType(name: String, sortOrder: Int) {
        // The client names the row, so a queued account created against it in
        // the same breath still references the right id when both land.
        val id = Ids.new(Ids.ACCOUNT_TYPE)
        accountTypeDao.upsert(Provisional.accountType(id, name, sortOrder))
        outbox.enqueue("POST", "/api/account-types", buildJsonObject { put("id", id); put("name", name); put("sortOrder", sortOrder) })
    }

    suspend fun renameAccountType(id: String, name: String) {
        accountTypeDao.byId(id)?.let { accountTypeDao.upsert(it.copy(name = name, updatedAt = Provisional.touchedAt())) }
        outbox.enqueue("PATCH", "/api/account-types/$id", buildJsonObject { put("name", name) }, entity = "accountType", rowId = id)
    }

    suspend fun setAccountTypeArchived(id: String, archived: Boolean) {
        accountTypeDao.byId(id)?.let { accountTypeDao.upsert(it.copy(archived = archived, updatedAt = Provisional.touchedAt())) }
        outbox.enqueue("PATCH", "/api/account-types/$id", buildJsonObject { put("archived", archived) }, entity = "accountType", rowId = id)
    }

    /**
     * A type still in use cannot be deleted, and only the API knows for certain
     * how many accounts hold it — so this one is sent rather than queued, and
     * the caller still gets the 409 that points at archiving instead.
     */
    suspend fun deleteAccountType(id: String) {
        apiCall { api.deleteAccountType(id) }
        accountTypeDao.deleteById(id)
        refreshAccounts()
    }

    suspend fun createAccount(
        name: String,
        typeId: String,
        currency: String,
        startingBalance: Long,
        logoUrl: String,
        logoInvertDark: Boolean,
        roundUpSource: Boolean = false,
    ) {
        val body = buildJsonObject {
            put("name", name)
            put("typeId", typeId)
            put("currency", currency)
            put("startingBalance", startingBalance)
            put("logoUrl", logoUrl)
            put("logoInvertDark", logoInvertDark)
            put("roundUpSource", roundUpSource)
        }
        val id = Ids.new(Ids.ACCOUNT)
        accountDao.upsert(
            Provisional.account(
                id = id,
                name = name,
                typeId = typeId,
                type = accountTypeDao.byId(typeId),
                currency = currency,
                startingBalance = startingBalance,
                logoUrl = logoUrl.takeIf { it.isNotBlank() },
                logoInvertDark = logoInvertDark,
                roundUpSource = roundUpSource,
            ),
        )
        outbox.enqueue("POST", "/api/accounts", JsonObject(body + ("id" to JsonPrimitive(id))))
    }

    suspend fun updateAccount(
        id: String,
        name: String,
        typeId: String,
        currency: String,
        startingBalance: Long,
        logoUrl: String,
        logoInvertDark: Boolean,
        roundUpSource: Boolean? = null,
    ) {
        val body = buildJsonObject {
            put("name", name)
            put("typeId", typeId)
            put("currency", currency)
            put("startingBalance", startingBalance)
            put("logoUrl", logoUrl)
            put("logoInvertDark", logoInvertDark)
            roundUpSource?.let { put("roundUpSource", it) }
        }
        accountDao.byId(id)?.let { current ->
            accountDao.upsert(
                current.copy(
                    name = name,
                    typeId = typeId,
                    // Changing the type changes the label beside the account, so it
                    // is resolved now rather than left showing the old one.
                    typeName = accountTypeDao.byId(typeId)?.name,
                    currency = currency,
                    // The balance is the starting balance plus everything posted to
                    // it, so moving the one moves the other by the same amount.
                    balance = current.balance - current.startingBalance + startingBalance,
                    startingBalance = startingBalance,
                    logoUrl = logoUrl.takeIf { it.isNotBlank() },
                    logoInvertDark = logoInvertDark,
                    roundUpSource = roundUpSource ?: current.roundUpSource,
                    updatedAt = Provisional.touchedAt(),
                ),
            )
        }
        outbox.enqueue("PATCH", "/api/accounts/$id", body, entity = "account", rowId = id)
    }

    suspend fun setAccountArchived(id: String, archived: Boolean) {
        accountDao.byId(id)?.let { accountDao.upsert(it.copy(archived = archived, updatedAt = Provisional.touchedAt())) }
        outbox.enqueue("PATCH", "/api/accounts/$id", buildJsonObject { put("archived", archived) }, entity = "account", rowId = id)
    }

    /** Throws [dev.gavenda.yuuka.data.remote.ApiError] with status 409 when the account still has transactions and [includeTransactions] is false. */
    suspend fun deleteAccount(id: String, includeTransactions: Boolean = false) {
        // The 409 is a question about data this device already holds, so it is
        // asked online; only the confirmed delete that follows is queued.
        if (!includeTransactions) {
            apiCall { api.deleteAccount(id, false) }
            accountDao.deleteById(id)
            refreshAccounts()
            return
        }

        accountDao.deleteById(id)
        transactionDao.deleteByAccountId(id)
        outbox.enqueue("DELETE", "/api/accounts/$id?includeTransactions=true", entity = "account", rowId = id)
    }

    suspend fun createCategory(name: String, kind: CategoryKind, color: String, parentId: String?) {
        val body = buildJsonObject {
            put("name", name)
            put("kind", kind.name)
            put("color", color)
            put("parentId", parentId)
        }
        val id = Ids.new(Ids.CATEGORY)
        categoryDao.upsert(Provisional.category(id, name, kind.name, color, parentId?.let { categoryDao.byId(it) }))
        outbox.enqueue("POST", "/api/categories", JsonObject(body + ("id" to JsonPrimitive(id))))
    }

    /** A child inherits its parent's kind and scope, so only these three fields are ever sent when editing. */
    suspend fun updateCategory(id: String, name: String, kind: CategoryKind, color: String) {
        val body = buildJsonObject { put("name", name); put("kind", kind.name); put("color", color) }
        categoryDao.byId(id)?.let { current ->
            categoryDao.upsert(current.copy(name = name, kind = kind.name, color = color, updatedAt = Provisional.touchedAt()))
        }
        outbox.enqueue("PATCH", "/api/categories/$id", body, entity = "category", rowId = id)
    }

    suspend fun setCategoryArchived(id: String, archived: Boolean) {
        categoryDao.byId(id)?.let { categoryDao.upsert(it.copy(archived = archived, updatedAt = Provisional.touchedAt())) }
        outbox.enqueue("PATCH", "/api/categories/$id", buildJsonObject { put("archived", archived) }, entity = "category", rowId = id)
    }

    /** Deleting a category never destroys history — the transactions that wore it fall back to uncategorised. */
    suspend fun deleteCategory(id: String) {
        categoryDao.deleteAndUncategorise(id)
        outbox.enqueue("DELETE", "/api/categories/$id", entity = "category", rowId = id)
    }

    suspend fun createTag(name: String, color: String) {
        val id = Ids.new(Ids.TAG)
        tagDao.upsert(Provisional.tag(id, name, color))
        outbox.enqueue("POST", "/api/tags", buildJsonObject { put("id", id); put("name", name); put("color", color) })
    }

    suspend fun updateTag(id: String, name: String, color: String) {
        tagDao.byId(id)?.let { tagDao.upsert(it.copy(name = name, color = color, updatedAt = Provisional.touchedAt())) }
        outbox.enqueue("PATCH", "/api/tags/$id", buildJsonObject { put("name", name); put("color", color) }, entity = "tag", rowId = id)
    }

    /** Only the labels come off: the API never touches the transactions, and the cached chips follow the tag list. */
    suspend fun deleteTag(id: String) {
        tagDao.deleteAndUnlink(id)
        outbox.enqueue("DELETE", "/api/tags/$id", entity = "tag", rowId = id)
    }
}
