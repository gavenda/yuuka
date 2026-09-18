package dev.gavenda.yuuka.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import dev.gavenda.yuuka.data.local.dao.AccountDao
import dev.gavenda.yuuka.data.local.dao.AccountTypeDao
import dev.gavenda.yuuka.data.local.dao.BudgetDao
import dev.gavenda.yuuka.data.local.dao.CategoryDao
import dev.gavenda.yuuka.data.local.dao.IncomePlanDao
import dev.gavenda.yuuka.data.local.dao.PayeeDao
import dev.gavenda.yuuka.data.local.dao.RoundUpRuleDao
import dev.gavenda.yuuka.data.local.dao.SettingsDao
import dev.gavenda.yuuka.data.local.dao.SummaryDao
import dev.gavenda.yuuka.data.local.dao.TransactionDao
import dev.gavenda.yuuka.data.local.entity.AccountEntity
import dev.gavenda.yuuka.data.local.entity.AccountTypeEntity
import dev.gavenda.yuuka.data.local.entity.BudgetEntity
import dev.gavenda.yuuka.data.local.entity.CategoryEntity
import dev.gavenda.yuuka.data.local.entity.IncomePlanEntity
import dev.gavenda.yuuka.data.local.entity.PayeeEntity
import dev.gavenda.yuuka.data.local.entity.RoundUpRuleEntity
import dev.gavenda.yuuka.data.local.entity.SettingsEntity
import dev.gavenda.yuuka.data.local.entity.SummaryEntity
import dev.gavenda.yuuka.data.local.entity.TransactionEntity

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
        SettingsEntity::class,
        TransactionEntity::class,
        BudgetEntity::class,
        IncomePlanEntity::class,
        SummaryEntity::class,
        PayeeEntity::class,
        RoundUpRuleEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class YuukaDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun accountTypeDao(): AccountTypeDao
    abstract fun categoryDao(): CategoryDao
    abstract fun settingsDao(): SettingsDao
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun incomePlanDao(): IncomePlanDao
    abstract fun summaryDao(): SummaryDao
    abstract fun payeeDao(): PayeeDao
    abstract fun roundUpRuleDao(): RoundUpRuleDao
}
