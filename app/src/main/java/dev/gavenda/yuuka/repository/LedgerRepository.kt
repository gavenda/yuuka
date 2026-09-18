package dev.gavenda.yuuka.repository

import dev.gavenda.yuuka.data.local.dao.AccountDao
import dev.gavenda.yuuka.data.local.dao.AccountTypeDao
import dev.gavenda.yuuka.data.local.dao.CategoryDao
import dev.gavenda.yuuka.data.local.dao.RoundUpRuleDao
import dev.gavenda.yuuka.data.local.dao.SettingsDao
import dev.gavenda.yuuka.data.local.toDomain
import dev.gavenda.yuuka.data.local.toEntity
import dev.gavenda.yuuka.data.model.Account
import dev.gavenda.yuuka.data.model.AccountType
import dev.gavenda.yuuka.data.model.Category
import dev.gavenda.yuuka.data.model.CategoryKind
import dev.gavenda.yuuka.data.model.CategoryScope
import dev.gavenda.yuuka.data.model.RoundUpRule
import dev.gavenda.yuuka.data.model.Settings
import dev.gavenda.yuuka.data.remote.YuukaApi
import dev.gavenda.yuuka.data.remote.apiCall
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
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
 */
class LedgerRepository(
    private val api: YuukaApi,
    private val accountDao: AccountDao,
    private val accountTypeDao: AccountTypeDao,
    private val categoryDao: CategoryDao,
    private val settingsDao: SettingsDao,
    private val roundUpRuleDao: RoundUpRuleDao,
) {
    val accounts: Flow<List<Account>> = accountDao.observeAll().map { list -> list.map { it.toDomain() } }
    val accountTypes: Flow<List<AccountType>> = accountTypeDao.observeAll().map { list -> list.map { it.toDomain() } }
    val categories: Flow<List<Category>> = categoryDao.observeAll().map { list -> list.map { it.toDomain() } }
    val settings: Flow<Settings?> = settingsDao.observe().map { it?.toDomain() }
    val roundUpRule: Flow<RoundUpRule?> = roundUpRuleDao.observe().map { it?.toDomain() }

    suspend fun refreshAll() = coroutineScope {
        launch { refreshAccounts() }
        launch { refreshCategories() }
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
        val response = apiCall { api.updateSettings(body) }
        settingsDao.upsert(response.settings.toEntity())
    }

    suspend fun refreshRoundUpRule() {
        val response = apiCall { api.getRoundUpRule() }
        roundUpRuleDao.upsert(response.roundUpRule.toEntity())
    }

    suspend fun updateRoundUpRule(enabled: Boolean? = null, roundTo: Long? = null, destinationAccountId: String? = null, clearDestination: Boolean = false) {
        val body = buildJsonObject {
            enabled?.let { put("enabled", it) }
            roundTo?.let { put("roundTo", it) }
            if (clearDestination) put("destinationAccountId", null as String?) else destinationAccountId?.let { put("destinationAccountId", it) }
        }
        val response = apiCall { api.updateRoundUpRule(body) }
        roundUpRuleDao.upsert(response.roundUpRule.toEntity())
    }

    suspend fun createAccountType(name: String, sortOrder: Int) {
        apiCall { api.createAccountType(buildJsonObject { put("name", name); put("sortOrder", sortOrder) }) }
        refreshAccounts()
    }

    suspend fun renameAccountType(id: String, name: String) {
        apiCall { api.updateAccountType(id, buildJsonObject { put("name", name) }) }
        refreshAccounts()
    }

    suspend fun setAccountTypeArchived(id: String, archived: Boolean) {
        apiCall { api.updateAccountType(id, buildJsonObject { put("archived", archived) }) }
        refreshAccounts()
    }

    suspend fun deleteAccountType(id: String) {
        apiCall { api.deleteAccountType(id) }
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
        apiCall { api.createAccount(body) }
        refreshAccounts()
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
        apiCall { api.updateAccount(id, body) }
        refreshAccounts()
    }

    suspend fun setAccountArchived(id: String, archived: Boolean) {
        apiCall { api.updateAccount(id, buildJsonObject { put("archived", archived) }) }
        refreshAccounts()
    }

    /** Throws [dev.gavenda.yuuka.data.remote.ApiError] with status 409 when the account still has transactions and [includeTransactions] is false. */
    suspend fun deleteAccount(id: String, includeTransactions: Boolean = false) {
        apiCall { api.deleteAccount(id, includeTransactions) }
        refreshAccounts()
    }

    suspend fun createCategory(name: String, kind: CategoryKind, appliesTo: CategoryScope, color: String, parentId: String?) {
        val body = buildJsonObject {
            put("name", name)
            put("kind", kind.name)
            put("appliesTo", appliesTo.name)
            put("color", color)
            put("parentId", parentId)
        }
        apiCall { api.createCategory(body) }
        refreshCategories()
    }

    /** A child inherits its parent's kind and scope, so only these three fields are ever sent when editing. */
    suspend fun updateCategory(id: String, name: String, kind: CategoryKind, color: String) {
        val body = buildJsonObject { put("name", name); put("kind", kind.name); put("color", color) }
        apiCall { api.updateCategory(id, body) }
        refreshCategories()
    }

    suspend fun setCategoryArchived(id: String, archived: Boolean) {
        apiCall { api.updateCategory(id, buildJsonObject { put("archived", archived) }) }
        refreshCategories()
    }

    suspend fun deleteCategory(id: String) {
        apiCall { api.deleteCategory(id) }
        refreshCategories()
    }
}
