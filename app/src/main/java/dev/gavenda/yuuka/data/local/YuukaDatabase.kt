package dev.gavenda.yuuka.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import dev.gavenda.yuuka.data.local.dao.*
import dev.gavenda.yuuka.data.local.entity.*

/**
 * Room is the app's single source of truth for the UI: every screen observes
 * a `Flow` from here, and repositories sync it from the network on load and
 * after every mutation. This is what keeps the last-seen ledger on screen
 * immediately, offline or not, while a sync is in flight.
 */
@Database(
    entities = [
        AccountTypeEntity::class,
        AccountEntity::class,
        CategoryEntity::class,
        TagEntity::class,
        SettingsEntity::class,
        TransactionEntity::class,
        TransactionTagEntity::class,
        BudgetEntity::class,
        IncomePlanEntity::class,
        SummaryEntity::class,
        PayeeEntity::class,
        RoundUpRuleEntity::class,
        SubscriptionEntity::class,
    ],
    // 8: categories lost `appliesTo`, its meaning folded into `kind`. The
    // builder falls back to a destructive migration, which is right here — this
    // database is a cache of the API, and a full sync refills it.
    version = 8,
    exportSchema = true,
)
abstract class YuukaDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun accountTypeDao(): AccountTypeDao
    abstract fun categoryDao(): CategoryDao
    abstract fun tagDao(): TagDao
    abstract fun settingsDao(): SettingsDao
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun incomePlanDao(): IncomePlanDao
    abstract fun summaryDao(): SummaryDao
    abstract fun payeeDao(): PayeeDao
    abstract fun roundUpRuleDao(): RoundUpRuleDao
    abstract fun subscriptionDao(): SubscriptionDao
}
