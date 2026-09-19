package dev.gavenda.yuuka.di

import dev.gavenda.yuuka.repository.*
import org.koin.dsl.module

val repositoryModule = module {
    single { LedgerRepository(get(), get(), get(), get(), get(), get(), get()) }
    single { TransactionRepository(get(), get()) }
    single { BudgetRepository(get(), get(), get(), get()) }
    single { PayeeRepository(get(), get()) }
    single { SubscriptionRepository(get(), get()) }
    single { SyncRepository(get(), get(), get(), get(), get(), get(), get(), get()) }
}
