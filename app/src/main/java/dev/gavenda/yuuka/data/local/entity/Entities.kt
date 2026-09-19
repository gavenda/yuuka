package dev.gavenda.yuuka.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room mirrors of the API's resources. Enum-shaped fields are stored as their
 * raw string value (matching the wire format exactly) rather than through a
 * Room [androidx.room.TypeConverter], so the entity <-> domain mapping is the
 * only place that needs to know about the enum type.
 */

@Entity(tableName = "account_types")
data class AccountTypeEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sortOrder: Int,
    val archived: Boolean,
    val accountCount: Int,
    val createdAt: String,
    val updatedAt: String,
)

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String,
    val name: String,
    val typeId: String,
    val typeName: String?,
    val currency: String,
    val logoUrl: String?,
    val logoInvertDark: Boolean,
    val roundUpSource: Boolean,
    val startingBalance: Long,
    val balance: Long,
    val archived: Boolean,
    val createdAt: String,
    val updatedAt: String,
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val kind: String,
    val color: String,
    val sortOrder: Int,
    val archived: Boolean,
    val parentId: String?,
    val appliesTo: String,
    val createdAt: String,
    val updatedAt: String,
)

/** A single row, pinned at id 0 — there is exactly one settings record per signed-in user. */
@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 0,
    val displayCurrency: String,
    val budgetMode: String,
    val defaultAccountId: String?,
    val createdAt: String,
    val updatedAt: String,
)

/** Single row, pinned at id 0 — mirrors [SettingsEntity]; not guaranteed to exist until the first sync. */
@Entity(tableName = "round_up_rule")
data class RoundUpRuleEntity(
    @PrimaryKey val id: Int = 0,
    val enabled: Boolean,
    val roundTo: Long,
    val destinationAccountId: String?,
    val categoryId: String?,
    val createdAt: String?,
    val updatedAt: String?,
)

@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: String,
    val createdAt: String,
    val updatedAt: String,
)

/**
 * Which tags a transaction wears. Deliberately without foreign keys: a transaction is written with
 * `REPLACE`, which deletes the old row first, and a cascade would take its links with it.
 */
@Entity(tableName = "transaction_tags", primaryKeys = ["transactionId", "tagId"], indices = [Index("tagId")])
data class TransactionTagEntity(
    val transactionId: String,
    val tagId: String,
)

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val accountName: String?,
    val categoryId: String?,
    val categoryName: String?,
    val categoryColor: String?,
    val amount: Long,
    val occurredOn: String,
    val payee: String,
    val notes: String,
    val transferId: String?,
    /** Posted by a subscription's cron at 00:00 UTC, so its time of day is not the user's to change. */
    val automated: Boolean,
    val runningBalance: Long,
    val createdAt: String,
    val updatedAt: String,
)

/**
 * [queriedMonth] is the month the row was last fetched under from
 * `GET /budgets?month=`, kept alongside the API's own (possibly null, for a
 * fixed budget) [month] so the local cache can be scoped per query the same
 * way the network call is.
 */
@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey val id: String,
    val categoryId: String,
    val month: String?,
    val amount: Long,
    val percent: Double?,
    val queriedMonth: String,
    val createdAt: String,
    val updatedAt: String,
)

/**
 * [queriedMonth] is the month the row was last fetched under from
 * `GET /income-plan?month=`, kept alongside the API's own (possibly null, for
 * a fixed plan) [month] so the local cache can be scoped per query the same
 * way the network call is — mirrors [BudgetEntity]'s [BudgetEntity.queriedMonth].
 */
@Entity(tableName = "income_plans")
data class IncomePlanEntity(
    @PrimaryKey val queriedMonth: String,
    val month: String?,
    val amount: Long,
    val mode: String,
    val grossAmount: Long?,
    val createdAt: String?,
    val updatedAt: String?,
)

/** The monthly summary is a derived aggregate; it is cached whole as JSON rather than modelled relationally. */
@Entity(tableName = "summaries")
data class SummaryEntity(
    @PrimaryKey val month: String,
    val json: String,
    val cachedAt: Long,
)

@Entity(tableName = "payees")
data class PayeeEntity(
    @PrimaryKey val id: String,
    val payee: String,
    val kind: String,
    val accountId: String?,
    val accountName: String?,
    val toAccountId: String?,
    val toAccountName: String?,
    val categoryId: String?,
    val categoryName: String?,
    val categoryColor: String?,
    val notes: String,
    val usedCount: Int,
    val lastUsedAt: String,
)

@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val accountName: String?,
    val categoryId: String?,
    val categoryName: String?,
    val categoryColor: String?,
    val amount: Long,
    val payee: String,
    val notes: String,
    val startOn: String,
    val dayOfMonth: Int,
    val nextRunOn: String,
    val lastRunOn: String?,
    val enabled: Boolean,
    val createdAt: String,
    val updatedAt: String,
)
