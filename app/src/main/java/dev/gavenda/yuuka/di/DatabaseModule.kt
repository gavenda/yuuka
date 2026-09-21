package dev.gavenda.yuuka.di

import androidx.room.Room
import dev.gavenda.yuuka.data.local.OutboxDatabase
import dev.gavenda.yuuka.data.local.YuukaDatabase
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val databaseModule = module {
    single {
        // Room is a cache of the API, never its source of truth — every table
        // is repopulated from the network on load, so a schema change can
        // just drop and recreate rather than carry a hand-written migration.
        Room.databaseBuilder(androidContext(), YuukaDatabase::class.java, "yuuka.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    single { get<YuukaDatabase>().accountDao() }
    single { get<YuukaDatabase>().accountTypeDao() }
    single { get<YuukaDatabase>().categoryDao() }
    single { get<YuukaDatabase>().tagDao() }
    single { get<YuukaDatabase>().settingsDao() }
    single { get<YuukaDatabase>().transactionDao() }
    single { get<YuukaDatabase>().budgetDao() }
    single { get<YuukaDatabase>().incomePlanDao() }
    single { get<YuukaDatabase>().summaryDao() }
    single { get<YuukaDatabase>().payeeDao() }
    single { get<YuukaDatabase>().roundUpRuleDao() }
    single { get<YuukaDatabase>().subscriptionDao() }

    // The unsent queue, in a database of its own and with no destructive
    // fallback: a row in it is work the server has never seen, so dropping the
    // table on a schema change would throw it away. See `OutboxDatabase`.
    single {
        Room.databaseBuilder(androidContext(), OutboxDatabase::class.java, "yuuka-outbox.db").build()
    }

    single { get<OutboxDatabase>().outboxDao() }
}
